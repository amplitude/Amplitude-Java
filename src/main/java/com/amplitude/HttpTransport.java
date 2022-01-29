package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
  private Map<String, Map<String, Queue<Event>>> idToBuffer = new ConcurrentHashMap<>();
  private AtomicInteger eventsInRetry = new AtomicInteger(0);
  private ConcurrentHashMap<String, Integer> throttledUserId = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, Integer> throttledDeviceId = new ConcurrentHashMap<>();
  private boolean recordThrottledId = false;

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
              }  else {
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
    if (eventsInRetry.intValue() < Constants.MAX_CACHED_EVENTS) {
      onEventsError(events, response);
    }
    else {
        logger.warn("DROP EVENTS", "Retry buffer is full");
        triggerEventCallbacks(events, response.code, "Retry buffer full, events dropped.");
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
    Map<String, Set<String>> userToDevices = new HashMap<>();
    for (Event event : eventsToRetry) {
      String userId = (event.userId != null) ? event.userId : "";
      String deviceId = (event.deviceId != null) ? event.deviceId : "";
      if (userId.length() > 0 || deviceId.length() > 0) {
        addEventToBuffer(userId, deviceId, event);
        userToDevices.computeIfAbsent(userId, key -> new HashSet<>()).add(deviceId);
      }
    }
    userToDevices.forEach(
        (userId, deviceSet) -> {
          deviceSet.forEach(
              (deviceId) -> {
                retryEventsOnLoop(userId, deviceId);
              });
        });
  }

  private void retryEventsOnLoop(String userId, String deviceId) {
    Thread retryThread =
        new Thread(
            () -> {
              List<Event> eventsBuffer = getEventsFromBuffer(userId, deviceId);
              if (eventsBuffer == null || eventsBuffer.size() == 0) {
                return;
              }
              eventsInRetry.addAndGet(-eventsBuffer.size());
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
              if (throttledUserId.containsKey(userId)) {
                throttledUserId.remove(userId);
              }
              if (throttledDeviceId.containsKey(deviceId)) {
                throttledDeviceId.remove(deviceId);
              }
            });
    retryThread.start();
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
        //shouldReduceEventCount = true;
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
    List<Event> eventsToRetry = new ArrayList<>();
    List<Event> eventsToDrop = new ArrayList<>();
    // Filter invalid event out based on the response code.
    if (response.status == Status.SUCCESS) {
        triggerEventCallbacks(events, response.code, "Events sent success.");
      return eventsToRetry;
    } else if (response.status == Status.FAILED) {
        triggerEventCallbacks(events, response.code, "Event sent Failed.");
        return eventsToRetry;
    } else if (response.status == Status.UNKNOWN){
        triggerEventCallbacks(events, response.code, "Unknown response status.");
        return eventsToRetry;
    } else if (response.status == Status.INVALID) {
      int[] invalidEventIndices = response.collectInvalidEventIndices();
      for (int i = 0; i < events.size(); i++) {
        if (Arrays.binarySearch(invalidEventIndices, i) < 0) {
          eventsToRetry.add(events.get(i));
        } else {
          eventsToDrop.add(events.get(i));
        }
      }
      triggerEventCallbacks(eventsToDrop, response.code, response.error);
      return eventsToRetry;
    } else if (response.status == Status.RATELIMIT) {
      for (Event event : events) {
        if (response.isUserOrDeviceExceedQuote(event.userId, event.deviceId)) {
          eventsToDrop.add(event);
          if (recordThrottledId) {
            try {
              JSONObject throttledUser = response.rateLimitBody.getJSONObject("exceededDailyQuotaUsers");
              JSONObject throttledDevice = response.rateLimitBody.getJSONObject("exceededDailyQuotaDevices");
              if (throttledUser.has(event.userId)) {
                throttledUserId.put(event.userId, throttledUser.getInt(event.userId));
              }
              if (throttledDevice.has(event.deviceId)) {
                throttledDeviceId.put(event.deviceId, throttledDevice.getInt(event.deviceId));
              }
            } catch (JSONException e) {
              logger.debug("THROTTLED", "Error get throttled userId or deviceId");
            }
          }
        } else {
          eventsToRetry.add(event);
        }
      }
      triggerEventCallbacks(eventsToDrop, response.code, "User or Device Exceed Daily Quota.");
      return eventsToRetry;
    }
    return events;
  }

  // Cleans up the id in the buffer map if the job is done
  private void cleanUpBuffer(String userId) {
    idToBuffer.computeIfPresent(
        userId, (key, value) -> value == null || value.isEmpty() ? null : value);
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

  private synchronized void addEventToBuffer(String userId, String deviceId, Event event) {
    idToBuffer.compute(
        userId,
        (key, value) -> {
          if (value == null) {
            value = new ConcurrentHashMap<>();
          }
          value.compute(
              deviceId,
              (deviceKey, deviceValue) -> {
                if (deviceValue == null) {
                  deviceValue = new ConcurrentLinkedQueue<>();
                }
                deviceValue.add(event);
                eventsInRetry.incrementAndGet();
                return deviceValue;
              });
          return value;
        });
  }

  private synchronized List<Event> getEventsFromBuffer(String userId, String deviceId) {
    if (idToBuffer.containsKey(userId) && idToBuffer.get(userId).containsKey(deviceId)){
        return new ArrayList<>(idToBuffer.remove(userId).remove(deviceId));
    }
    return null;
  }

  public boolean shouldWait(Event event) {
      if (throttledUserId.containsKey(event.userId) || throttledDeviceId.containsKey(event.deviceId)) {
        return true;
      }
      return eventsInRetry.intValue() >= Constants.MAX_CACHED_EVENTS;
  }

  public void setRecordThrottledId(boolean record) {
    recordThrottledId = record;
  }

  public boolean isRecordThrottledId() {
    return recordThrottledId;
  }
}
