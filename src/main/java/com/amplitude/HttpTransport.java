package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.*;

class EventsRetryResult {
  protected boolean shouldRetry;
  protected boolean shouldReduceEventCount;
  protected int[] eventIndicesToRemove;
  protected int statusCode;
  protected String errorMessage;

  protected EventsRetryResult(
      boolean shouldRetry,
      boolean shouldReduceEventCount,
      int[] eventIndicesToRemove,
      int statusCode,
      String errorMessage) {
    this.shouldRetry = shouldRetry;
    this.shouldReduceEventCount = shouldReduceEventCount;
    this.eventIndicesToRemove = eventIndicesToRemove;
    this.statusCode = statusCode;
    this.errorMessage = errorMessage;
  }
}

class HttpTransport {
  // Use map to record the events are currently in retry queue.
  private Object throttleLock = new Object();
  private Map<String, Integer> throttledUserId = new HashMap<>();
  private Map<String, Integer> throttledDeviceId = new HashMap<>();
  private boolean recordThrottledId = false;
  private Map<String, Map<String, List<Event>>> idToBuffer = new HashMap<>();
  private int eventsInRetry = 0;
  private Object bufferLock = new Object();
  private Object counterLock = new Object();
  private ExecutorService retryThreadPool = Executors.newFixedThreadPool(10);

  private HttpCall httpCall;
  private AmplitudeLog logger;
  private AmplitudeCallbacks callbacks;

  HttpTransport(HttpCall httpCall, AmplitudeCallbacks callbacks, AmplitudeLog logger) {
    this.httpCall = httpCall;
    this.callbacks = callbacks;
    this.logger = logger;
  }

  public void sendEventsWithRetry(List<Event> events) {
    sendEvents(events)
        .thenAcceptAsync(
            response -> {
              Status status = response.status;
              if (shouldRetryForStatus(status)) {
                retryEvents(events, response);
              } else if (status == Status.SUCCESS) {
                triggerEventCallbacks(events, response.code, "Event sent success.");
              } else if (status == Status.FAILED) {
                triggerEventCallbacks(events, response.code, "Event sent Failed.");
              } else {
                triggerEventCallbacks(events, response.code, "Unknown response status.");
              }
            })
        .exceptionally(
            exception -> {
              logger.error("Invalid API Key", exception.getMessage());
              return null;
            });
  }

  // The main entrance for the retry logic.
  public void retryEvents(List<Event> events, Response response) {
      int bufferSize;
      synchronized (counterLock) {
          bufferSize  = eventsInRetry;
      }
      if (bufferSize < Constants.MAX_CACHED_EVENTS) {
          onEventsError(events, response);
      } else {
          String message = "Retry buffer is full(" + bufferSize + "), " + events.size() + " events dropped.";
          logger.warn("DROP EVENTS", message);
          triggerEventCallbacks(events, response.code, message);
      }
  }

  public void setHttpCall(HttpCall httpCall) {
    this.httpCall = httpCall;
  }

  public void setCallbacks(AmplitudeCallbacks callbacks) {
    this.callbacks = callbacks;
  }

  private CompletableFuture<Response> sendEvents(List<Event> events) {
    return CompletableFuture.supplyAsync(
        () -> {
          Response response = null;
          try {
            response = httpCall.makeRequest(events);
            logger.debug("SEND", events, response);
          } catch (AmplitudeInvalidAPIKeyException e) {
            throw new CompletionException(e);
          }
          return response;
        });
  }

  // Call this function if event not in current Retry list.
  private void onEventsError(List<Event> events, Response response) {
    List<Event> eventsToRetry = getEventListToRetry(events, response);
    if (eventsToRetry.isEmpty()) {
      return;
    }
    for (Event event : eventsToRetry) {
      String userId = (event.userId != null) ? event.userId : "";
      String deviceId = (event.deviceId != null) ? event.deviceId : "";
      if (userId.length() > 0 || deviceId.length() > 0) {
        addEventToBuffer(userId, deviceId, event);
      }
    }
      Set<String> users;
      synchronized (bufferLock) {
          users = new HashSet<>(idToBuffer.keySet());
      }
      for (String userId : users) {
          Set<String> devices;
          synchronized (bufferLock) {
              devices = new HashSet<>(idToBuffer.get(userId).keySet());
          }
          for (String deviceId : devices) {
              try {
                  retryThreadPool.submit(new RetryEventsOnLoop(userId, deviceId));
              } catch (RejectedExecutionException e) {
                  logger.warn("Failed init retry thread", e.getMessage());
              }
          }
      }
  }

  private EventsRetryResult retryEventsOnce(String userId, String deviceId, List<Event> events)
      throws AmplitudeInvalidAPIKeyException {
    Response response = httpCall.makeRequest(events);
    logger.debug("RETRY", events, response);
    boolean shouldRetry = true;
    boolean shouldReduceEventCount = false;
    int[] eventIndicesToRemove = new int[] {};
    switch (response.status) {
      case SUCCESS:
        shouldRetry = false;
        triggerEventCallbacks(events, response.code, "Events sent success.");
        break;
      case RATELIMIT:
        if (response.isUserOrDeviceExceedQuote(userId, deviceId)) {
          shouldRetry = false;
          triggerEventCallbacks(events, response.code, response.error);
        }
        // Reduce the payload to reduce risk of throttling
        shouldReduceEventCount = true;
        break;
      case PAYLOAD_TOO_LARGE:
        shouldRetry = true;
        shouldReduceEventCount = true;
        break;
      case INVALID:
        if (events.size() == 1) {
          shouldRetry = false;
          triggerEventCallbacks(events, response.code, response.error);
        } else {
          eventIndicesToRemove = response.collectInvalidEventIndices();
        }
        break;
      case UNKNOWN:
        shouldRetry = false;
        triggerEventCallbacks(events, response.code, "Unknown response status.");
        break;
      case FAILED:
        shouldRetry = false;
        triggerEventCallbacks(events, response.code, "Event sent Failed.");
        break;
      default:
        break;
    }
    return new EventsRetryResult(
        shouldRetry, shouldReduceEventCount, eventIndicesToRemove, response.code, response.error);
  }

  private List<Event> getEventListToRetry(List<Event> events, Response response) {
    List<Event> eventsToRetry = events;
    List<Event> eventsToDrop = new ArrayList<>();
    // Filter invalid event out based on the response code.
    if (response.status == Status.SUCCESS) {
      return new ArrayList<>();
    } else if (response.status == Status.INVALID) {
      if ((response.invalidRequestBody != null
              && response.invalidRequestBody.has("missingField")
              && response.invalidRequestBody.getString("missingField").length() > 0)
          || events.size() == 1) {
        // Return early if there's an issue with the entire payload
        // or if there's only one event and its invalid
        triggerEventCallbacks(events, response.code, response.error);
        return new ArrayList<>();
      } else if (response.invalidRequestBody != null) {
        // Filter out invalid events id  vv v
        int[] invalidEventIndices = response.collectInvalidEventIndices();
        eventsToRetry = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
          if (Arrays.binarySearch(invalidEventIndices, i) < 0) {
            eventsToRetry.add(events.get(i));
          } else {
            eventsToDrop.add(events.get(i));
          }
        }
        triggerEventCallbacks(eventsToDrop, response.code, response.error);
      }
    } else if (response.status == Status.RATELIMIT && response.rateLimitBody != null) {
      eventsToRetry = new ArrayList<>();

      for (Event event : events) {
        if (!(response.isUserOrDeviceExceedQuote(event.userId, event.deviceId))) {
          eventsToRetry.add(event);
          if (recordThrottledId) {
            try {
              JSONObject throttledUser =
                      response.rateLimitBody.getJSONObject("throttledUsers");
              JSONObject throttledDevice =
                      response.rateLimitBody.getJSONObject("throttledDevices");
              synchronized (throttleLock) {
                if (throttledUser.has(event.userId)) {
                  throttledUserId.put(event.userId, throttledUser.getInt(event.userId));
                }
                if (throttledDevice.has(event.deviceId)) {
                  throttledDeviceId.put(event.deviceId, throttledDevice.getInt(event.deviceId));
                }
              }
            } catch (JSONException e) {
              logger.debug("THROTTLED", "Error get throttled userId or deviceId");
            }
          }
        } else {
          eventsToDrop.add(event);
        }
      }
      triggerEventCallbacks(eventsToDrop, response.code, "User or Device Exceed Daily Quota.");
    }
    return eventsToRetry;
  }

  protected boolean shouldRetryForStatus(Status status) {
    return (status == Status.INVALID
        || status == Status.PAYLOAD_TOO_LARGE
        || status == Status.RATELIMIT
        || status == Status.TIMEOUT);
  }

  private void triggerEventCallbacks(List<Event> events, int status, String message) {
    if (events == null || events.isEmpty()) {
      return;
    }
    for (Event event : events) {
      if (callbacks != null) {
        // client level callback
        callbacks.onLogEventServerResponse(event, status, message);
      }
      if (event.callback != null) {
        // event level callback
        event.callback.onLogEventServerResponse(event, status, message);
      }
    }
  }

    private void addEventToBuffer(String userId, String deviceId, Event event) {
        synchronized (bufferLock) {
            if (!idToBuffer.containsKey(userId)) {
                idToBuffer.put(userId, new HashMap<>());
            }
            if (!idToBuffer.get(userId).containsKey(deviceId)) {
                idToBuffer.get(userId).put(deviceId, new ArrayList<>());
            }
            idToBuffer.get(userId).get(deviceId).add(event);
        }
        synchronized (counterLock) {
            eventsInRetry++;
        }
    }

    private List<Event> getEventsFromBuffer(String userId, String deviceId) {
        synchronized (bufferLock) {
            if (idToBuffer.containsKey(userId) && idToBuffer.get(userId).containsKey(deviceId)) {
                List<Event> events = idToBuffer.get(userId).remove(deviceId);
                if (idToBuffer.get(userId).isEmpty()) {
                    idToBuffer.remove(userId);
                }
                return events;
            }
        }
        return null;
    }

  public boolean shouldWait(Event event) {
    if (recordThrottledId
            && (throttledUserId.containsKey(event.userId)
            || throttledDeviceId.containsKey(event.deviceId))) {
      return true;
    }
    return eventsInRetry >= Constants.MAX_CACHED_EVENTS;
  }

  public void setRecordThrottledId(boolean record) {
    recordThrottledId = record;
  }

    class RetryEventsOnLoop implements Runnable {
        private String userId;
        private String deviceId;

        RetryEventsOnLoop(String userId, String deviceId) {
            this.deviceId = deviceId;
            this.userId = userId;
        }

        @Override
        public void run() {
            List<Event> eventsBuffer = getEventsFromBuffer(userId, deviceId);
            if (eventsBuffer == null || eventsBuffer.size() == 0) {
                return;
            }
            synchronized (counterLock) {
                eventsInRetry -= eventsBuffer.size();
            }
            int retryTimes = Constants.RETRY_TIMEOUTS.length;
            for (int numRetries = 0; numRetries < retryTimes; numRetries++) {
                int eventCount = eventsBuffer.size();
                if (eventCount <= 0) {
                    break;
                }
                long sleepDuration = Constants.RETRY_TIMEOUTS[numRetries];
                try {
                    Thread.sleep(sleepDuration);
                    boolean isLastTry = numRetries == retryTimes - 1;
                    EventsRetryResult retryResult = retryEventsOnce(userId, deviceId, eventsBuffer);
                    boolean shouldRetry = retryResult.shouldRetry;
                    if (!shouldRetry) {
                        // call back done in retryEventsOnce
                        break;
                    } else if (isLastTry) {
                        triggerEventCallbacks(eventsBuffer, retryResult.statusCode, "Event retries exhausted.");
                        break;
                    }
                    boolean shouldReduceEventCount = retryResult.shouldReduceEventCount;
                    int[] eventIndicesToRemove = retryResult.eventIndicesToRemove;
                    if (eventIndicesToRemove.length > 0) {
                        List<Event> eventsToDrop = new ArrayList<>();
                        int numEventsRemoved = 0;
                        for (int i = eventIndicesToRemove.length - 1; i >= 0; i--) {
                            int index = eventIndicesToRemove[i];
                            if (index < eventCount) {
                                eventsToDrop.add(eventsBuffer.remove(index));
                                numEventsRemoved += 1;
                            }
                        }
                        triggerEventCallbacks(eventsToDrop, retryResult.statusCode, "Invalid events.");
                    } else if (shouldReduceEventCount) {
                        List<Event> eventsToDrop = eventsBuffer.subList(eventCount / 2, eventCount);
                        triggerEventCallbacks(eventsToDrop, retryResult.statusCode, "Event dropped for retry");
                        eventsBuffer = eventsBuffer.subList(0, eventCount / 2);
                    }

                } catch (InterruptedException | AmplitudeInvalidAPIKeyException e) {
                    // The retry logic should only be executed after the API key checking passed.
                    // This catch AmplitudeInvalidAPIKeyException is just for handling
                    // retryEventsOnce in thread.
                    logger.debug("RETRY", "Retry thread got interrupted");
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
