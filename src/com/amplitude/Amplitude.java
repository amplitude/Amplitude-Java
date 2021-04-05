package com.amplitude;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
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
        if (instances == null) {
            instances = new HashMap<>();
        }
        if (!instances.containsKey(instanceName)) {
            Amplitude ampInstance = new Amplitude();
            instances.put(instanceName, ampInstance);
        }
        return instances.get(instanceName);
    }

    public void init(String key) {
        apiKey = key;
    }

    public void logEvent(String name) {
        logEventWithProps(name, null);
    }

    public void logEventWithProps(String eventName, JSONObject eventProps) {
        long time = System.currentTimeMillis();
        Event event = new Event(eventName, eventProps, userProperties, "", Constants.SDK_VERSION,
                lastEventId, sessionId, userId, time);
        logEvent(eventName, event);
    }

    public void logEvent(String eventName, Event event) {
        //try {
            /*
            Future<Object> futureResult = CompletableFuture.supplyAsync(() -> {
                return syncHttpCall(event);
            });
            Object value = futureResult.get(10000, TimeUnit.MILLISECONDS);
             */
            syncHttpCall(event);
        //}
        /* catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
         */
    }

    public JSONObject getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(JSONObject userProperties) {
        this.userProperties = userProperties;
    }

    /*
     * Use HTTPUrlConnection object to make async HTTP request,
     * using data from event like device, class name, event props, etc.
     */
    private Object syncHttpCall(Event event) {
        try {
            HttpsURLConnection connection =
                    (HttpsURLConnection) new URL(Constants.API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            connection.setDoOutput(true);

            JSONObject bodyJson = new JSONObject();
            //bodyJson.put("v", Constants.SDK_VERSION);
            bodyJson.put("api_key", apiKey);
            bodyJson.put("events", new String[]{}); //event == null ? "[{}]" : event.toString());
            //bodyJson.put("upload_time", event.timestamp);

            OutputStream os = connection.getOutputStream();
            System.out.println(bodyJson.toString());
            byte[] input = bodyJson.toString().getBytes("UTF-8");
            os.write(input, 0, input.length);

            System.out.println(connection.getResponseCode());
            String stringResponse = connection.getResponseMessage();
            System.out.println("Response!: " + stringResponse);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return null;
    }

}
