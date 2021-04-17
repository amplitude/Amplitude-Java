package com.amplitude;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class Amplitude {

    private static Map<String, Amplitude> instances = new HashMap<>();
    private String apiKey;

    private Amplitude() {

    }

    public static synchronized Amplitude getInstance(String instanceName) {
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
        logEvent(new Event(name));
    }

    public void logEvent(Event event) {
        try {
            Future<Void> futureResult = CompletableFuture.supplyAsync(() -> {
                syncHttpCall(event);
                return null;
            });
            futureResult.get(Constants.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    /*
     * Use HTTPUrlConnection object to make async HTTP request,
     * using data from event like device, class name, event props, etc.
     */
    private Object syncHttpCall(Event event) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(Constants.API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            //connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setDoOutput(true);

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("api_key", apiKey);
            bodyJson.put("events", new JSONObject[]{event.getJsonObject()}); //event == null ? "[{}]" : event.toString());

            String bodyString = bodyJson.toString();
            OutputStream os = connection.getOutputStream();
            byte[] input = bodyString.getBytes("UTF-8");
            os.write(input, 0, input.length);
            os.close();

            int responseCode = connection.getResponseCode();
            InputStream inputStream;
            if (Constants.GOOD_RES_CODE_START <= responseCode &&
                    responseCode <= Constants.GOOD_RES_CODE_END) {
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

            if (responseCode >= Constants.BAD_RES_CODE_START) {
                System.err.println("Warning, received error " + responseCode + " with message: " + sb.toString());
            }
            else {
                System.out.println("Response: " + sb.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.getInputStream().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

}
