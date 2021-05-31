package com.amplitude;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class retryEventsOnceResult {
    boolean shouldRetry;
    boolean shouldReduceEventCount;
    int[] eventIndicesToRemove;
    public static retryEventsOnceResult result;

    public static retryEventsOnceResult getResult(boolean shouldRetry, boolean shouldReduceEventCount, int[] eventIndicesToRemove) {
        result.shouldRetry = shouldRetry;
        result.shouldReduceEventCount = shouldReduceEventCount;
        result.eventIndicesToRemove = eventIndicesToRemove;
        return result;
    }
}

class Retry {
    //Has mapping to record the events are currently in retry queue.
    private static Map<String, Map<String, List<Event>>> idToBuffer = new HashMap<String, Map<String, List<Event>>>();
    private static int eventsInRetry = 0;

    //helper method to get event list from idToBuffer
    private static List<Event> getRetryBuffer(String userId, String deviceId) {
        return (idToBuffer.get(userId) != null) ? idToBuffer.get(userId).get(deviceId) : new ArrayList<>();
    }

    private static List<Event> pruneEvent(List<Event> events) {
        List<Event> prunedEvents = new ArrayList<>();
        // if we already have the key value pair for the current event in idToBuffer,
        // we just add into the events array and deal with it later otherwise, we should add it to prunedEvents and return back
        for (Event event : events) {
            String userId = event.userId;
            String deviceId = event.deviceId;
            //userId and deviceId are simi-required
            if ((userId.length() > 0) || (deviceId.length() > 0)) {
                List<Event> currentBuffer = getRetryBuffer(userId, deviceId);
                if (currentBuffer != null) {
                    //already in current retry;
                    currentBuffer.add(event);
                    eventsInRetry++;
                } else {
                    prunedEvents.add(event);
                }
            }
        }
        return prunedEvents;
    }

    // cleans up the id to buffer map if the job is done
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

    private static int[] JSONArrayToIntArray(JSONArray jsonArray) {
        int[] arr = new int[jsonArray.length()];
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = jsonArray.optInt(i);
        }
        return arr;
    }


    private static int[] collectInvalidEventIndices(Response response) {
        List<Integer> invalidIndices = new ArrayList<Integer>();
        if (response.status == Status.INVALID && response.InvalidRequestBody != null) {
            //JSONObject with eventfield string as key, int[] as value
            JSONObject eventsWithInvalidFields = response.InvalidRequestBody.getJSONObject("eventsWithInvalidFields");
            JSONObject eventsWithMissingFields = response.InvalidRequestBody.getJSONObject("eventsWithMissingFields");
            Iterator<String> invalidFieldsKeys = eventsWithInvalidFields.keys();
            Iterator<String> missingFieldsKeys = eventsWithMissingFields.keys();

            while (invalidFieldsKeys.hasNext()) {
                String key = invalidFieldsKeys.next();
                int[] eventIndices = JSONArrayToIntArray(eventsWithInvalidFields.getJSONArray(key));
                for (int eventIndex : eventIndices) {
                    invalidIndices.add(eventIndex);
                }
            }
            while (missingFieldsKeys.hasNext()) {
                String key = missingFieldsKeys.next();
                int[] eventIndices = JSONArrayToIntArray(eventsWithMissingFields.getJSONArray(key));
                for (int eventIndex : eventIndices) {
                    invalidIndices.add(eventIndex);
                }
            }
            Collections.sort(invalidIndices);
            int[] allInvalidEventIndices = invalidIndices.stream().mapToInt(Integer::intValue).toArray();
            return allInvalidEventIndices;
        }
        return new int[]{};
    }

    private static retryEventsOnceResult retryEventsOnce(String userId, String deviceId, List<Event> events, String apiKey) {
        Response onceReponse = Response.syncHttpCallWithEventsBuffer(events, apiKey);
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
            shouldReduceEventCount = true; // Reduce the payload to reduce risk of throttling
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
        return retryEventsOnceResult.getResult(shouldRetry, shouldReduceEventCount, eventIndicesToRemove);
    }

    private static void retryEventsOnloop(String userId, String deviceId, String apiKey) {
        List<Event> eventsBuffer = getRetryBuffer(userId, deviceId);
        // work around for java 8 no final variable in lambeda function
        int[] eventCountHolder = new int[1];
        eventCountHolder[0] = eventsBuffer.size();
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
                            List<Event> eventsToRetrys = eventsBuffer.subList(0, eventCountHolder[0]);
                            retryEventsOnceResult retryResult = retryEventsOnce(userId, deviceId, eventsToRetrys, apiKey);
                            boolean shouldRetry = retryResult.shouldRetry;
                            boolean shouldReduceEventCount = retryResult.shouldReduceEventCount;
                            int[] eventIndicesToRemove = retryResult.eventIndicesToRemove;
                            if (eventIndicesToRemove.length > 0) {
                                int numEventsRemoved = 0;
                                for (int i = 0; i < eventIndicesToRemove.length; i++) {
                                    int index = eventIndicesToRemove[i];
                                    if (index < eventCountHolder[0]) {
                                        //take this index out of eventIndicesToRemove
                                        eventsBuffer.remove(i);
                                        numEventsRemoved += 1;
                                    }
                                }
                                eventCountHolder[0] -= numEventsRemoved;
                                eventsInRetry -= eventCountHolder[0];
                                if (eventCountHolder[0] < 1) {
                                    break; // If we managed to remove all the events, break off early.
                                }
                            }
                            if (!shouldRetry) {
                                break;
                            }
                            if (shouldReduceEventCount && !isLastTry) {
                                eventCountHolder[0] = eventCountHolder[0] / 2;
                            }
                            //clean up the events
                            eventsBuffer.subList(0, eventCountHolder[0]).clear();
                            eventsInRetry -= eventCountHolder[0];
                        } catch (InterruptedException e) {
                        }
                    }
                });
        retryThread.start();
    }

    //calling this function if eventBuffer not in current Retry list.
    private static void onEventsError(List<Event> events, Response response, String apiKey) {
        List<Event> eventsToRetry = new ArrayList<>();
        //filter invalid event out based on the response code.
        if (response.status == Status.RATELIMIT && response.RateLimitBody != null) {
            //JSONObject deviceId as key, number as valude
            JSONObject exceededDailyQuotaUsers = response.RateLimitBody.getJSONObject("exceededDailyQuotaUsers");
            JSONObject exceededDailyQuotaDevices = response.RateLimitBody.getJSONObject("exceededDailyQuotaDevices");
            eventsToRetry = events.stream()
                    .filter((event -> (event.userId.length() > 0 && exceededDailyQuotaUsers.has(event.userId))
                            && (event.deviceId.length() > 0 && exceededDailyQuotaDevices.has(event.deviceId))))
                    .collect(Collectors.toList());
        } else if (response.status == Status.INVALID) {
            if (response.InvalidRequestBody.getString("missingField").length() > 0 || events.size() == 1) {
                // Return early if there's an issue with the entire payload
                // or if there's only one event and its invalid
                return;
            } else {
                //filter out invalid events id
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
            String userId = event.userId;
            String deviceId = event.deviceId;
            if ((userId.length() > 0) || (deviceId.length() > 0)) {
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
                                //TODO: retryEventsOnLoop(userId, deviceId, apiKey);
                            });
                    retryThread.start();
                }
                eventsInRetry++;
                retryBuffer.add(event);
            }
        }
    }

    //the main entrance for the retry logic.
    protected static Response sendEventWithRetry(List<Event> events, String apiKey) {
        System.out.println(apiKey);
        //init the response
        Response response = new Response();
        response.status = Status.UNKNOWN;
        response.code = 0;
        //pruning the input events, if the currentEventBuffer already in the retry list, just append the event. otherwise, call onEventsError if the result is valid
        List<Event> eventsToSend = pruneEvent(events);
        response = Response.syncHttpCallWithEventsBuffer(eventsToSend, apiKey);
        if (response.status == Status.INVALID ||
                response.status == Status.PAYLOAD_TOO_LARGE ||
                response.status == Status.RATELIMIT) {
            if (eventsInRetry < Constants.MAX_CACHED_EVENTS) {
                onEventsError(eventsToSend, response, apiKey);
            }
        }
        return response;
    }
}
