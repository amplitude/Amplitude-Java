package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;

import org.json.JSONObject;

import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class RetryEventsOnceResult {
  protected boolean shouldRetry;
  protected boolean shouldReduceEventCount;
  protected int[] eventIndicesToRemove;

  protected RetryEventsOnceResult(
      boolean shouldRetry, boolean shouldReduceEventCount, int[] eventIndicesToRemove) {
    this.shouldRetry = shouldRetry;
    this.shouldReduceEventCount = shouldReduceEventCount;
    this.eventIndicesToRemove = eventIndicesToRemove;
  }
}

class Retry {
  // Use map to record the events are currently in retry queue.
  private static Map<String, Map<String, List<Event>>> idToBuffer =
      new ConcurrentHashMap<String, Map<String, List<Event>>>();
  private static AtomicInteger eventsInRetry = new AtomicInteger(0);

  // Helper method to get event list from idToBuffer
  private static List<Event> getRetryBuffer(String userId, String deviceId) {
    return (idToBuffer.get(userId) != null) ? idToBuffer.get(userId).get(deviceId) : null;
  }

  private static List<Event> pruneEvent(List<Event> events) {
    List<Event> prunedEvents = new ArrayList<>();
    // If we already have the key value pair for the current event in idToBuffer,
    // We just add into the events list and deal with it later otherwise we should add it to
    // prunedEvents and return back
    for (Event event : events) {
      String userId = event.userId;
      String deviceId = event.deviceId;
      if ((userId != null && userId.length() > 0) || (deviceId != null && deviceId.length() > 0)) {
        List<Event> currentBuffer = getRetryBuffer(userId, deviceId);
        if (currentBuffer != null) {
          currentBuffer.add(event);
          eventsInRetry.incrementAndGet();
        } else {
          prunedEvents.add(event);
        }
      }
    }
    return prunedEvents;
  }

  // Cleans up the id in the buffer map if the job is done
  private static void cleanUpBuffer(String userId) {
    Map<String, List<Event>> deviceToBufferMap = idToBuffer.get(userId);
    if (deviceToBufferMap == null) {
      return;
    }
    if (deviceToBufferMap.size() == 0) {
      idToBuffer.remove(userId);
    }
  }

  private static List<Integer> collectIndicesWithRequestBody(JSONObject requestBody, String key) {
    List<Integer> invalidIndices = new ArrayList<Integer>();
    JSONObject fields = requestBody.getJSONObject(key);
    Iterator<String> fieldKeys = fields.keys();
    while (fieldKeys.hasNext()) {
      String fieldKey = fieldKeys.next();
      int[] eventIndices = Utils.jsonArrayToIntArray(fields.getJSONArray(fieldKey));
      for (int eventIndex : eventIndices) {
        invalidIndices.add(eventIndex);
      }
    }
    Collections.sort(invalidIndices);
    return invalidIndices;
  }

  private static int[] collectInvalidEventIndices(Response response) {
    if (response.status == Status.INVALID && response.invalidRequestBody != null) {
      List<Integer> invalidFieldsIndices =
          collectIndicesWithRequestBody(response.invalidRequestBody, "eventsWithInvalidFields");
      List<Integer> missingFieldsIndices =
          collectIndicesWithRequestBody(response.invalidRequestBody, "eventsWithMissingFields");
      invalidFieldsIndices.addAll(missingFieldsIndices);
      Collections.sort(invalidFieldsIndices);
      int[] allInvalidEventIndices = invalidFieldsIndices.stream().mapToInt(i -> i).toArray();
      return allInvalidEventIndices;
    }
    return new int[] {};
  }

  private static RetryEventsOnceResult retryEventsOnce(
      String userId, String deviceId, List<Event> events, HttpCall httpCall) throws AmplitudeInvalidAPIKeyException {
    Response onceReponse = httpCall.syncHttpCallWithEventsBuffer(events);
    boolean shouldRetry = true;
    boolean shouldReduceEventCount = false;
    int[] eventIndicesToRemove = new int[] {};
    if (onceReponse.status == Status.RATELIMIT) {
      if (onceReponse.rateLimitBody != null) {
        JSONObject exceededDailyQuotaUsers =
            onceReponse.rateLimitBody.getJSONObject("exceededDailyQuotaUsers");
        JSONObject exceededDailyQuotaDevices =
            onceReponse.rateLimitBody.getJSONObject("exceededDailyQuotaDevices");
        if ((userId.length() > 0 && exceededDailyQuotaUsers.has(userId))
            || (deviceId.length() > 0 && exceededDailyQuotaDevices.has(deviceId))) {
          shouldRetry = false;
        }
      }
      // Reduce the payload to reduce risk of throttling
      shouldReduceEventCount = true;
    } else if (onceReponse.status == Status.PAYLOAD_TOO_LARGE) {
      shouldRetry = true;
    } else if (onceReponse.status == Status.INVALID) {
      if (events.size() == 1) {
        shouldRetry = false;
      } else {
        eventIndicesToRemove = collectInvalidEventIndices(onceReponse);
      }
    } else if (onceReponse.status == Status.SUCCESS) {
      shouldRetry = false;
    }
    return new RetryEventsOnceResult(shouldRetry, shouldReduceEventCount, eventIndicesToRemove);
  }

  private static void retryEventsOnLoop(String userId, String deviceId, HttpCall httpCall) {
    Thread retryThread =
        new Thread(
            () -> {
              List<Event> eventsBuffer =
                  (idToBuffer.get(userId) != null) ? idToBuffer.get(userId).remove(deviceId) : null;
              if (eventsBuffer == null || eventsBuffer.size() == 0) {
                cleanUpBuffer(userId);
                return;
              }
              int retryTimes = Constants.RETRY_TIMEOUTS.length;
              int eventCount = eventsBuffer.size();
              for (int numRetries = 0; numRetries < retryTimes; numRetries++) {
                long sleepDuration = Constants.RETRY_TIMEOUTS[numRetries];
                try {
                  Thread.sleep(sleepDuration);
                  boolean isLastTry = numRetries == Constants.RETRY_TIMEOUTS.length - 1;
                  List<Event> eventsToRetry = eventsBuffer.subList(0, eventCount);
                  RetryEventsOnceResult retryResult =
                      retryEventsOnce(userId, deviceId, eventsToRetry, httpCall);
                  boolean shouldRetry = retryResult.shouldRetry;
                  boolean shouldReduceEventCount = retryResult.shouldReduceEventCount;
                  int[] eventIndicesToRemove = retryResult.eventIndicesToRemove;

                  if (eventIndicesToRemove.length > 0) {
                    int numEventsRemoved = 0;
                    for (int i = 0; i < eventIndicesToRemove.length; i++) {
                      int index = eventIndicesToRemove[i];
                      if (index < eventCount) {
                        eventsBuffer.remove(i);
                        numEventsRemoved += 1;
                      }
                    }
                    eventCount -= numEventsRemoved;
                    eventsInRetry.addAndGet(-numEventsRemoved);
                  }
                  if (!shouldRetry || eventCount < 1) {
                    break;
                  }
                  if (shouldReduceEventCount && !isLastTry) {
                    eventCount /= 2;
                  }
                } catch (InterruptedException | AmplitudeInvalidAPIKeyException e) {
                }
              }
              eventsInRetry.addAndGet(-eventCount);
            });
    retryThread.start();
  }

  // Call this function if event not in current Retry list.
  private static void onEventsError(List<Event> events, Response response, HttpCall httpCall) {
    List<Event> eventsToRetry = events;
    // Filter invalid event out based on the response code.
    if (response.status == Status.RATELIMIT && response.rateLimitBody != null) {
      // JSONObject deviceId as key, number as value
      JSONObject exceededDailyQuotaUsers =
          response.rateLimitBody.getJSONObject("exceededDailyQuotaUsers");
      JSONObject exceededDailyQuotaDevices =
          response.rateLimitBody.getJSONObject("exceededDailyQuotaDevices");
      eventsToRetry =
          events.stream()
              .filter(
                  (event ->
                      !(event.userId != null && exceededDailyQuotaUsers.has(event.userId))
                          && !(event.deviceId != null
                              && exceededDailyQuotaDevices.has(event.deviceId))))
              .collect(Collectors.toList());
    } else if (response.status == Status.INVALID) {
      if ((response.invalidRequestBody != null
              && response.invalidRequestBody.has("missingField")
              && response.invalidRequestBody.getString("missingField").length() > 0)
          || events.size() == 1) {
        // Return early if there's an issue with the entire payload
        // or if there's only one event and its invalid
        return;
      } else if (response.invalidRequestBody != null) {
        // Filter out invalid events id
        int[] invalidEventIndices = collectInvalidEventIndices(response);
        eventsToRetry =
            IntStream.range(0, events.size())
                .filter(i -> Arrays.binarySearch(invalidEventIndices, i) < 0)
                .mapToObj(events::get)
                .collect(Collectors.toList());
      }
    } else if (response.status == Status.SUCCESS) {
      return;
    }
    Map<String, Set<String>> userToDevices = new HashMap<>();
    for (Event event : eventsToRetry) {
      String userId = (event.userId != null) ? event.userId : "";
      String deviceId = (event.deviceId != null) ? event.deviceId : "";
      if (userId.length() > 0 || deviceId.length() > 0) {
        Map<String, List<Event>> deviceToBufferMap = idToBuffer.get(userId);
        if (deviceToBufferMap == null) {
          deviceToBufferMap = new ConcurrentHashMap<String, List<Event>>();
          idToBuffer.put(userId, deviceToBufferMap);
        }
        List<Event> retryBuffer = deviceToBufferMap.get(deviceId);
        if (retryBuffer == null) {
          retryBuffer = new ArrayList<Event>();
          deviceToBufferMap.put(deviceId, retryBuffer);
        }
        eventsInRetry.incrementAndGet();
        retryBuffer.add(event);
        userToDevices.computeIfAbsent(userId, key -> new HashSet<String>()).add(deviceId);
      }
    }
    userToDevices.forEach(
        (userId, deviceSet) -> {
          deviceSet.forEach(
              (deviceId) -> {
                retryEventsOnLoop(userId, deviceId, httpCall);
              });
        });
  }

  protected static boolean shouldRetryForStatus(Status status) {
    return (status == Status.INVALID
        || status == Status.PAYLOAD_TOO_LARGE
        || status == Status.RATELIMIT);
  }

  // The main entrance for the retry logic.
  protected static void sendEventsWithRetry(
      List<Event> events, Response response, HttpCall httpCall) {
    List<Event> eventsToSend = pruneEvent(events);
    if (eventsInRetry.intValue() < Constants.MAX_CACHED_EVENTS) {
      onEventsError(eventsToSend, response, httpCall);
    }
  }
}
