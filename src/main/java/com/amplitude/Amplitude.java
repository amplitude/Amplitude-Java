package com.amplitude;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class Amplitude {

    private static Map<String, Amplitude> instances;
    private JSONObject userProperties;
    private String apiKey;

    private static int lastEventId = 1;
    private long sessionId;
    private String userId;
    private String deviceId;

    private Amplitude() {
        sessionId = System.currentTimeMillis();
        userId = String.valueOf((int) (Math.random()*1000000 + 100000));
        deviceId = UUID.randomUUID().toString();
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
        Event event = new Event(eventName);
        event.eventProperties = eventProps;
        event.deviceId = deviceId;
        event.userId = userId;
        lastEventId++;
        logEvent(event);
    }

    public void logEvent(Event event) {
        try {
            Future<Void> futureResult = CompletableFuture.supplyAsync(() -> {
                syncHttpCall(event);
                return null;
            });
            futureResult.get(10000, TimeUnit.MILLISECONDS);
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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
            bodyJson.put("api_key", apiKey);
            bodyJson.put("events", event.toJsonObject());

            String bodyString = bodyJson.toString();
            OutputStream os = connection.getOutputStream();
            byte[] input = bodyString.getBytes("UTF-8");
            os.write(input, 0, input.length);

            int responseCode = connection.getResponseCode();
            InputStream inputStream;
            if (100 <= responseCode && responseCode <= 399) {
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

            if (responseCode >= 400) {
                System.err.println("Warning, received error " + responseCode + " with message: " + sb.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return null;
    }

}
