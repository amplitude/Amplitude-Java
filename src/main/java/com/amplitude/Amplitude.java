package com.amplitude;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.*;

public class Amplitude {

    public static final String TAG = Amplitude.class.getName();

    private static Map<String, Amplitude> instances = new HashMap<>();
    private String apiKey;

    private AmplitudeLog logger;

    private Queue<Event> eventsToSend;
    private boolean aboutToStartFlushing;

    private Amplitude() {
        logger = new AmplitudeLog();
        eventsToSend = new ConcurrentLinkedQueue<>();
        aboutToStartFlushing = false;
    }

    public static Amplitude getInstance() {
        return getInstance("");
    }

    public static Amplitude getInstance(String instanceName) {
        if (!instances.containsKey(instanceName)) {
            Amplitude ampInstance = new Amplitude();
            instances.put(instanceName, ampInstance);
        }
        return instances.get(instanceName);
    }

    public void init(String key) {
        apiKey = key;
    }

    public void setLogMode(AmplitudeLog.LogMode logMode) {
        this.logger.setLogMode(logMode);
    }

    public void logEvent(Event event) {
        List<Event> listOfOne = new ArrayList<>();
        listOfOne.add(event);
        logEvents(listOfOne);
    }

    public void logEvents(Collection<Event> events) {
        eventsToSend.addAll(events);
        if (eventsToSend.size() >= Constants.EVENT_BUF_COUNT) {
            flushEvents();
        } else {
            tryToFlushEventsIfNotFlushing();
        }
    }

    private void tryToFlushEventsIfNotFlushing() {
        if (!aboutToStartFlushing) {
            aboutToStartFlushing = true;
            Thread flushThread =
                    new Thread(() -> {
                        try {
                            Thread.sleep(Constants.EVENT_BUF_TIME_MILLIS);
                        } catch (InterruptedException e) {

                        }
                        flushEvents();
                        aboutToStartFlushing = false;
                    });
            flushThread.start();
        }
    }

    public synchronized void flushEvents() {
        if (eventsToSend.size() > 0) {
            List<Event> eventsInTransit = new ArrayList<>(eventsToSend);
            eventsToSend.clear();
            try {
                Future<Integer> futureResult = CompletableFuture.supplyAsync(() -> {
                    return syncHttpCallWithEventsBuffer(eventsInTransit);
                });
                int responseCode = futureResult.get(Constants.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (responseCode >= Constants.HTTP_STATUS_MIN_RETRY && responseCode <= Constants.HTTP_STATUS_MAX_RETRY) {
                    eventsToSend.addAll(eventsInTransit);
                    tryToFlushEventsIfNotFlushing();
                }
            } catch (InterruptedException | TimeoutException e) {
                tryToFlushEventsIfNotFlushing();
            } catch (ExecutionException e) {
                tryToFlushEventsIfNotFlushing();
            }
        }
    }

    /*
     * Use HTTPUrlConnection object to make async HTTP request,
     * using data from event like device, class name, event props, etc.
     *
     * @return The response code
     */
    private int syncHttpCallWithEventsBuffer(List<Event> events) {
        HttpsURLConnection connection;
        InputStream inputStream = null;
        int responseCode = 500;
        try {
            connection = (HttpsURLConnection) new URL(Constants.API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("api_key", apiKey);

            JSONArray eventsArr = new JSONArray();
            for (int i = 0; i < events.size(); i++) {
                eventsArr.put(i, events.get(i).toJsonObject());
            }
            bodyJson.put("events", eventsArr);

            String bodyString = bodyJson.toString();
            OutputStream os = connection.getOutputStream();
            byte[] input = bodyString.getBytes("UTF-8");
            os.write(input, 0, input.length);

            responseCode = connection.getResponseCode();
            boolean isErrorCode = responseCode >= Constants.HTTP_STATUS_BAD_REQ;
            if (!isErrorCode) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }

            if (!isErrorCode) {
                logger.log(TAG, "Successful HTTP code " + responseCode + " with message: " + sb.toString());
            } else {
                logger.warn(TAG, "Warning, received error HTTP code " + responseCode + " with message: " + sb.toString());
            }
        } catch (IOException e) { //This also handles UnknownHostException, when the SDK has no internet
            responseCode = Constants.HTTP_STATUS_MIN_RETRY;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {

                }
            }
            return responseCode;
        }
    }

}
