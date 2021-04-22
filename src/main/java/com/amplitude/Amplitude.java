package com.amplitude;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class Amplitude {

    public static final String TAG = Amplitude.class.getName();

    private static Map<String, Amplitude> instances = new HashMap<>();
    private String apiKey;

    private AmplitudeLog logger;

    private Amplitude() {
        logger = new AmplitudeLog();
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

    public void setLogMode(AmplitudeLog.LogMode logMode) {
        this.logger.setLogMode(logMode);
    }

    /*
     * Use HTTPUrlConnection object to make async HTTP request,
     * using data from event like device, class name, event props, etc.
     */
    private void syncHttpCall(Event event) {
        HttpsURLConnection connection;
        InputStream inputStream = null;
        try {
            connection = (HttpsURLConnection) new URL(Constants.API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("api_key", apiKey);
            bodyJson.put("events", event.toJsonObject());

            String bodyString = bodyJson.toString();
            OutputStream os = connection.getOutputStream();
            byte[] input = bodyString.getBytes("UTF-8");
            os.write(input, 0, input.length);

            int responseCode = connection.getResponseCode();
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
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {

                }
            }
        }
    }

}
