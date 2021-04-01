package com.amplitude;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;

public class Amplitude {

    private static Map<String, Amplitude> instances;
    private JSONObject userProperties;
    private String apiKey;

    private static int lastEventId = 1;
    private int sessionId;
    private String userId;

    private Amplitude() {
        sessionId = 1; //TODO
        userId = "1";
    }

    public static Amplitude getInstance(String instanceName) {
        if (!instances.containsKey(instanceName)) {
            Amplitude ampInstance = new Amplitude();
            instances.put(instanceName, ampInstance);
        }
        else {
            return instances.get(instanceName);
        }
    }

    public void init(String key) {
        apiKey = key;
    }

    public void logEvent(String name) {
        logEvent(name, null);
    }

    public void logEventWithProps(String eventName, JSONObject eventProps) {
        //Use HTTPUrlConnection object to make async HTTP request,
        //using data from event like device, class name, event props, etc.
        if (eventProps == null) {

        }
        long time = System.currentTimeMillis();
        Event event = new Event(eventName, eventProps, userProperties, "", Constants.SDK_VERSION,
                lastEventId, sessionId, userId, time);
        logEvent(eventName, event);
    }

    public void logEvent(String eventName, Event event) {
        try {
            Future<Object> futureResult = CompletableFuture.supplyAsync(() -> {
                syncHttpCall();
            });
            Object value = futureResult.get(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    public JSONObject getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(JSONObject userProperties) {
        this.userProperties = userProperties;
    }

    public static Future<Object> startAsyncHttpCall() {
        return CompletableFuture.supplyAsync(() -> syncHttpCall());
    }

    static Object syncHttpCall() {
        try {
            HttpURLConnection urlConnection =
                    (HttpURLConnection) new URL("https://jsonplaceholder.typicode.com/posts").openConnection();
            urlConnection.setRequestMethod("POST");
            OutputStreamWriter out = new OutputStreamWriter(urlConnection.getOutputStream());
                out.write("params as json");

            try (InputStreamReader in = new InputStreamReader(urlConnection.getInputStream())) {
                return new Object();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
