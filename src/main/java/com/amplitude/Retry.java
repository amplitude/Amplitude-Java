package com.amplitude;

import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;

class RetryEventsOnceResult {
    protected boolean shouldRetry;
    protected boolean shouldReduceEventCount;
    protected int[] eventIndicesToRemove;
    protected static RetryEventsOnceResult getResult(boolean shouldRetry, boolean shouldReduceEventCount, int[] eventIndicesToRemove) {
        RetryEventsOnceResult result = new RetryEventsOnceResult();
        result.shouldRetry = shouldRetry;
        result.shouldReduceEventCount = shouldReduceEventCount;
        result.eventIndicesToRemove = eventIndicesToRemove;
        return result;
    }
}

class Retry {
    // Use map to record the events are currently in retry queue.
    private static Map<String, Map<String, List<Event>>> idToBuffer = new ConcurrentHashMap<String, Map<String, List<Event>>>();
    private static int eventsInRetry = 0;

    // Helper method to get event list from idToBuffer
    private static List<Event> getRetryBuffer(String userId, String deviceId) {
        return (idToBuffer.get(userId) != null) ? idToBuffer.get(userId).get(deviceId) : null;
    }

    private static List<Event> pruneEvent(List<Event> events) {
        List<Event> prunedEvents = new ArrayList<>();
        // If we already have the key value pair for the current event in idToBuffer,
        // We just add into the events list and deal with it later otherwise we should add it to prunedEvents and return back
        for (Event event : events) {
            String userId = event.userId;
            String deviceId = event.deviceId;
            if ((userId != null && userId.length() > 0) || (deviceId != null && deviceId.length() > 0)) {
                List<Event> currentBuffer = getRetryBuffer(userId, deviceId);
                if (currentBuffer != null) {
                    currentBuffer.add(event);
                    eventsInRetry++;
                } else {
                    prunedEvents.add(event);
                }
            }
        }
        return prunedEvents;
    }

    // Cleans up the id in the buffer map if the job is done
    private static void cleanUpBuffer(String userId, String deviceId) {
        Map<String, List<Event>> deviceToBufferMap = idToBuffer.get(userId);
        if (deviceToBufferMap == null) {
            return;
        }
        List<Event> eventsToRetry = deviceToBufferMap.get(deviceId);
        if (eventsToRetry != null && eventsToRetry.size() == 0) {
            deviceToBufferMap.remove(deviceId);
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
        if (response.status == Status.INVALID && response.InvalidRequestBody != null) {
            List<Integer> invalidFieldsIndices = collectIndicesWithRequestBody(response.InvalidRequestBody, "eventsWithInvalidFields");
            List<Integer> missingFieldsIndices = collectIndicesWithRequestBody(response.InvalidRequestBody, "eventsWithMissingFields");
            invalidFieldsIndices.addAll(missingFieldsIndices);
            Collections.sort(invalidFieldsIndices);
            int[] allInvalidEventIndices = invalidFieldsIndices.stream().mapToInt(i -> i).toArray();
            return allInvalidEventIndices;
        }
        return new int[]{};
    }

    private static RetryEventsOnceResult retryEventsOnce(String userId, String deviceId, List<Event> events, String apiKey) {
        Response onceReponse = HttpCall.syncHttpCallWithEventsBuffer(events, apiKey);
        boolean shouldRetry = false;
        boolean shouldReduceEventCount = false;
        int[] eventIndicesToRemove = new int[]{};
        if (onceReponse.status == Status.RATELIMIT) {
            if (onceReponse.RateLimitBody != null) {
                JSONObject exceededDailyQuotaUsers = onceReponse.RateLimitBody.getJSONObject("exceededDailyQuotaUsers");
                JSONObject exceededDailyQuotaDevices = onceReponse.RateLimitBody.getJSONObject("exceededDailyQuotaDevices");
                if ((userId.length() > 0 && exceededDailyQuotaUsers.has(userId)) ||
                        (deviceId.length() > 0 && exceededDailyQuotaDevices.has(deviceId))) {
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
        return RetryEventsOnceResult.getResult(shouldRetry, shouldReduceEventCount, eventIndicesToRemove);
    }

    private static void retryEventsOnLoop(String userId, String deviceId, String apiKey) {
        List<Event> eventsBuffer = getRetryBuffer(userId, deviceId);
        int[] eventCountHolder = new int[]{eventsBuffer.size()};
        if (eventCountHolder[0] == 0) {
            cleanUpBuffer(userId, deviceId);
            return;
        }
        int retryTimes = Constants.RETRY_TIMEOUTS.length;
        Thread retryThread =
                new Thread(() -> {
                    for (int numRetries = 0; numRetries < retryTimes; numRetries++) {
                        long sleepDuration = Constants.RETRY_TIMEOUTS[numRetries];
                        try {
                            Thread.sleep(sleepDuration);
                            boolean isLastTry = numRetries == Constants.RETRY_TIMEOUTS.length - 1;
                            List<Event> eventsToRetry = eventsBuffer.subList(0, eventCountHolder[0]);
                            RetryEventsOnceResult retryResult = retryEventsOnce(userId, deviceId, eventsToRetry, apiKey);
                            boolean shouldRetry = retryResult.shouldRetry;
                            boolean shouldReduceEventCount = retryResult.shouldReduceEventCount;
                            int[] eventIndicesToRemove = retryResult.eventIndicesToRemove;
                            if (eventIndicesToRemove.length > 0) {
                                int numEventsRemoved = 0;
                                for (int i = 0; i < eventIndicesToRemove.length; i++) {
                                    int index = eventIndicesToRemove[i];
                                    if (index < eventCountHolder[0]) {
                                        eventsBuffer.remove(i);
                                        numEventsRemoved += 1;
                                    }
                                }
                                eventCountHolder[0] -= numEventsRemoved;
                                eventsInRetry -= eventCountHolder[0];
                                // If we managed to remove all the events, break early
                                if (eventCountHolder[0] < 1) {
                                    break;
                                }
                            }
                            if (!shouldRetry) {
                                break;
                            }
                            if (shouldReduceEventCount && !isLastTry) {
                                eventCountHolder[0] = eventCountHolder[0] / 2;
                            }
                            // Clean up the events
                            eventsBuffer.subList(0, eventCountHolder[0]).clear();
                            eventsInRetry -= eventCountHolder[0];
                        } catch (InterruptedException e) {
                        }
                    }
                });
        retryThread.start();
    }

    // Call this function if event not in current Retry list.
    private static void onEventsError(List<Event> events, Response response, String apiKey) {
        List<Event> eventsToRetry = events;
        // Filter invalid event out based on the response code.
        if (response.status == Status.RATELIMIT && response.RateLimitBody != null) {
            // JSONObject deviceId as key, number as value
            JSONObject exceededDailyQuotaUsers = response.RateLimitBody.getJSONObject("exceededDailyQuotaUsers");
            JSONObject exceededDailyQuotaDevices = response.RateLimitBody.getJSONObject("exceededDailyQuotaDevices");
            eventsToRetry = events.stream()
                    .filter((event -> !(event.userId != null && exceededDailyQuotaUsers.has(event.userId))
                            && !(event.deviceId != null && exceededDailyQuotaDevices.has(event.deviceId))))
                    .collect(Collectors.toList());
        } else if (response.status == Status.INVALID) {
            if ((response.InvalidRequestBody.has("missingField") &&
                    response.InvalidRequestBody.getString("missingField").length() > 0) ||
                    events.size() == 1) {
                // Return early if there's an issue with the entire payload
                // or if there's only one event and its invalid
                return;
            } else if (response.InvalidRequestBody != null) {
                // Filter out invalid events id
                int[] invalidEventIndices = collectInvalidEventIndices(response);
                eventsToRetry = IntStream.range(0, events.size())
                        .filter(i -> Arrays.binarySearch(invalidEventIndices, i) < 0)
                        .mapToObj(events::get)
                        .collect(Collectors.toList());
            }
        } else if (response.status == Status.SUCCESS) {
            return;
        }

        for (Event event : eventsToRetry) {
            String userId = (event.userId != null) ? event.userId : "";
            String deviceId = (event.deviceId != null) ? event.deviceId : "";
            if (userId.length() > 0 || deviceId.length() > 0) {
                Map<String, List<Event>> deviceToBufferMap = idToBuffer.get(userId);
                if (deviceToBufferMap == null) {
                    deviceToBufferMap = new HashMap<String, List<Event>>();
                    idToBuffer.put(userId, deviceToBufferMap);
                }
                List<Event> retryBuffer = deviceToBufferMap.get(deviceId);
                if (retryBuffer == null) {
                    retryBuffer = new ArrayList<Event>();
                    deviceToBufferMap.put(deviceId, retryBuffer);
                    Thread retryThread =
                            new Thread(() -> {
                                retryEventsOnLoop(userId, deviceId, apiKey);
                            });
                    retryThread.start();
                }
                eventsInRetry++;
                retryBuffer.add(event);
            }
        }
    }

    // The main entrance for the retry logic.
    protected static void sendEventWithRetry(List<Event> events, String apiKey, Response response) {
        List<Event> eventsToSend = pruneEvent(events);
        if (eventsInRetry < Constants.MAX_CACHED_EVENTS) {
            onEventsError(eventsToSend, response, apiKey);
        }
    }
}
