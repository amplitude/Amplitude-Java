package com.amplitude;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.List;

public class Response {
    protected int code;
    protected Status status;
    protected String error;
    protected JSONObject SuccessBody;
    protected JSONObject InvalidRequestBody;
    protected JSONObject RateLimitBody;
    protected static Response res;

    private static Response createNewResponseFromJSON(JSONObject json) {
        res = new Response();
        int code  = json.getInt("code");
        Status status = getCodeStatus(code);
        res.code = code;
        res.status = status;
        if (status == Status.SUCCESS) {
            res.SuccessBody = new JSONObject();
            res.SuccessBody.put("eventsIngested", json.getInt("events_ingested"));
            res.SuccessBody.put("payloadSizeBytes", json.getInt("payload_size_bytes"));
            res.SuccessBody.put("serverUploadTime", json.getLong("server_upload_time"));
        } else if (status == Status.INVALID) {
            res.InvalidRequestBody = new JSONObject();
            res.error = getStringValueWithKey(json, "error");
            res.InvalidRequestBody.put("missingField", getStringValueWithKey(json, "missing_field"));
            JSONObject eventsWithInvalidFields = getJSONObjectValueWithKey(json, "events_with_invalid_fields");
            res.InvalidRequestBody.put("eventsWithInvalidFields", eventsWithInvalidFields);
            JSONObject eventsWithMissingFields = getJSONObjectValueWithKey(json, "events_with_missing_fields");
            res.InvalidRequestBody.put("eventsWithMissingFields", eventsWithMissingFields);
        } else if (status == Status.PAYLOAD_TOO_LARGE) {
            res.error = getStringValueWithKey(json, "error");
        } else if (status == Status.RATELIMIT) {
            res.error = getStringValueWithKey(json, "error");
            res.RateLimitBody = new JSONObject();
            res.RateLimitBody.put("epsThreshold", json.getInt("eps_threshold"));
            JSONObject throttledDevices = getJSONObjectValueWithKey(json, "throttled_devices");
            res.RateLimitBody.put("throttledDevices", throttledDevices);
            JSONObject throttledUsers = getJSONObjectValueWithKey(json, "throttled_users");
            res.RateLimitBody.put("throttledUsers", throttledUsers);
            res.RateLimitBody.put("throttledEvents", convertJSONArrayToIntArray(json, "throttled_events"));
            JSONObject exceededDailyQuotaDevices = getJSONObjectValueWithKey(json, "exceeded_daily_quota_devices");
            res.RateLimitBody.put("exceededDailyQuotaDevices", exceededDailyQuotaDevices);
            JSONObject exceededDailyQuotaUsers = getJSONObjectValueWithKey(json, "exceeded_daily_quota_users");
            res.RateLimitBody.put("exceededDailyQuotaUsers", exceededDailyQuotaUsers);
        } else if (status == Status.SYSTEM_ERROR) {
            res.error = json.getString("error");
            res.code = 0;
        }
        return res;
    }

    /*
     * Use HTTPUrlConnection object to make async HTTP request,
     * using data from event like device, class name, event props, etc.
     *
     * @return The response code
     */
    protected static Response syncHttpCallWithEventsBuffer(List<Event> events, String apiKey) {
        HttpsURLConnection connection;
        InputStream inputStream = null;
        int responseCode = 500;
        Response responseBody = new Response();
        try {
            connection = (HttpsURLConnection) new URL(Constants.API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(Constants.NETWORK_TIMEOUT_MILLIS);
            connection.setReadTimeout(Constants.NETWORK_TIMEOUT_MILLIS);
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
            JSONObject responseJson = new JSONObject(sb.toString());
            responseBody = Response.createNewResponseFromJSON(responseJson);
        } catch (IOException e) {
            // This handles UnknownHostException, when the SDK has no internet.
            // Also SocketTimeoutException, when the HTTP request times out.
            JSONObject timesOutResponse = new JSONObject();
            timesOutResponse.put("status", Status.TIMEOUT);
            timesOutResponse.put("code", 0);
            responseBody = Response.createNewResponseFromJSON(timesOutResponse);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {

                }
            }
            return responseBody;
        }

    }

    private static int[] convertJSONArrayToIntArray(JSONObject json, String key) {
        boolean hasKey = json.has(key) && !json.isNull(key);
        if (!hasKey) return new int[]{};
        else {
            JSONArray jsonArray = json.getJSONArray(key);
            int[] intArray = new int[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                intArray[i] = jsonArray.getInt(i);
            }
            return intArray;
        }
    }

    private static String getStringValueWithKey(JSONObject json, String key) {
        return json.has(key) && json.getString(key) != null ? json.getString(key) : "";
    }

    private static JSONObject getJSONObjectValueWithKey(JSONObject json, String key) {
        return (json.has(key) && !json.isNull(key)) ? json.getJSONObject(key) : new JSONObject();
    }

    protected static Status getCodeStatus(int code) {
        if (code >= 200 && code < 300) {
            return Status.SUCCESS;
        }
        if (code == 429) {
            return Status.RATELIMIT;
        }
        if (code == 413) {
            return Status.PAYLOAD_TOO_LARGE;
        }
        if (code == 408) {
            return Status.TIMEOUT;
        }
        if (code >= 400 && code < 500) {
            return Status.INVALID;
        }
        if (code >= 500) {
            return Status.FAILED;
        }
        return Status.UNKNOWN;
    }
}
