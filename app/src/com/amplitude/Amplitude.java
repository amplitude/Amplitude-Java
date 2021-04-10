package com.amplitude;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
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
        sessionId = 1000000; //TODO
        userId = "100000000";
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
        try {
            Future<Object> futureResult = CompletableFuture.supplyAsync(() -> {
                return syncHttpCall(event);
            });
            System.out.println("After async call 1:");
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

            String eventString = event.toString();
            System.out.println(eventString);

            JSONObject bodyJson = new JSONObject();
            //bodyJson.put("v", Constants.SDK_VERSION);
            bodyJson.put("api_key", apiKey);
            bodyJson.put("events", new JSONObject[]{event.getJsonObject()}); //event == null ? "[{}]" : event.toString());
            //bodyJson.put("upload_time", event.timestamp);

            String bodyString = bodyJson.toString();

            OutputStream os = connection.getOutputStream();
            System.out.println("Body: " + bodyString);
            byte[] input = bodyString.getBytes("UTF-8");
            os.write(input, 0, input.length);

            System.out.println(connection.getResponseCode());

            InputStream inputStream;
            if (100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
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
            System.out.println("Response Apr 5th: " + sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return null;
    }

}
