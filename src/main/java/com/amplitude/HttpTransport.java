package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;

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
    List<Event> eventsToSend = pruneEvent(events);
    if (eventsInRetry.intValue() < Constants.MAX_CACHED_EVENTS) {
      onEventsError(eventsToSend, response);
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
              Queue<Event> eventsQueue = getEventsFromBuffer(userId, deviceId);
              if (eventsQueue == null || eventsQueue.size() == 0) {
                cleanUpBuffer(userId);
                return;
              }
              List<Event> eventsBuffer = new ArrayList<>(eventsQueue);
              int retryTimes = Constants.RETRY_TIMEOUTS.length;
              int eventCount = eventsBuffer.size();
              for (int numRetries = 0; numRetries < retryTimes; numRetries++) {
                long sleepDuration = Constants.RETRY_TIMEOUTS[numRetries];
                try {
                  Thread.sleep(sleepDuration);
                  boolean isLastTry = numRetries == Constants.RETRY_TIMEOUTS.length - 1;
                  List<Event> eventsToRetry = eventsBuffer.subList(0, eventCount);
                  EventsRetryResult retryResult = retryEventsOnce(userId, deviceId, eventsToRetry);
                  boolean shouldRetry = retryResult.shouldRetry;
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
                    eventCount -= numEventsRemoved;
                    eventsInRetry.addAndGet(-numEventsRemoved);
                  }
                  if (!shouldRetry || eventCount < 1) {
                    break;
                  }
                  if (shouldReduceEventCount && !isLastTry) {
                    triggerEventCallbacks(
                        eventsBuffer.subList(eventCount / 2, eventCount),
                        retryResult.statusCode,
                        "Event dropped for retry");
                    eventCount /= 2;
                  }
                  if (isLastTry) {
                    triggerEventCallbacks(
                        eventsBuffer.subList(0, eventCount),
                        retryResult.statusCode,
                        "Event retries exhausted.");
                  }
                  if (eventCount == 0) {
                    break;
                  }
                } catch (InterruptedException | AmplitudeInvalidAPIKeyException e) {
                  // The retry logic should only be executed after the API key checking passed.
                  // This catch AmplitudeInvalidAPIKeyException is just for handling
                  // retryEventsOnce in thread.
                  Thread.currentThread().interrupt();
                }
              }
              eventsInRetry.addAndGet(-eventCount);
            });
    retryThread.start();
  }

  private EventsRetryResult retryEventsOnce(String userId, String deviceId, List<Event> events)
      throws AmplitudeInvalidAPIKeyException {
    Response response = httpCall.makeRequest(events);
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
        break;
      case INVALID:
        if (events.size() == 1) {
          shouldRetry = false;
          triggerEventCallbacks(events, response.code, response.error);
        } else {
          eventIndicesToRemove = response.collectInvalidEventIndices();
        }
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
        } else {
          eventsToDrop.add(event);
        }
      }
      triggerEventCallbacks(eventsToDrop, response.code, "User or Device Exceed Daily Quota.");
    }
    return eventsToRetry;
  }

  private List<Event> pruneEvent(List<Event> events) {
    List<Event> prunedEvents = new ArrayList<>();
    // If we already have the key value pair for the current event in idToBuffer,
    // We just add into the events list and deal with it later otherwise we should add it to
    // prunedEvents and return.
    for (Event event : events) {
      String userId = event.userId;
      String deviceId = event.deviceId;
      if ((userId != null && userId.length() > 0) || (deviceId != null && deviceId.length() > 0)) {
        idToBuffer.compute(
            userId,
            (key, value) -> {
              if (value == null) {
                prunedEvents.add(event);
                return null;
              }
              value.compute(
                  deviceId,
                  (deviceKey, deviceValue) -> {
                    if (deviceValue == null) {
                      prunedEvents.add(event);
                      return null;
                    }
                    deviceValue.add(event);
                    eventsInRetry.incrementAndGet();
                    return deviceValue;
                  });
              return value;
            });
      }
    }
    return prunedEvents;
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
      AmplitudeCallbacks eventCallback = event.callback != null ? event.callback : callbacks;
      if (eventCallback != null) {
        eventCallback.onLogEventServerResponse(event, status, message);
      }
    }
  }

  private void addEventToBuffer(String userId, String deviceId, Event event) {
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
                return deviceValue;
              });
          return value;
        });
  }

  private Queue<Event> getEventsFromBuffer(String userId, String deviceId) {
    List<Queue<Event>> eventQueues = new ArrayList<>();
    idToBuffer.compute(
        userId,
        (key, value) -> {
          if (value == null) {
            return null;
          }
          value.compute(
              deviceId,
              (deviceKey, deviceValue) -> {
                if (deviceValue != null) {
                  eventQueues.add(deviceValue);
                }
                return null;
              });
          return value;
        });
    return eventQueues.isEmpty() ? null : eventQueues.get(0);
  }
}
