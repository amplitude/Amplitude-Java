package com.amplitude;

import org.json.JSONArray;
import org.json.JSONObject;

public class Response {
    public int code;
    public Status status;
    public String error;
    public JSONObject SuccessBody;
    public JSONObject InvalidRequestBody;
    public JSONObject RateLimitBody;
    public static Response res;


    public static Response createNewResponseFromJSON(JSONObject json) {
        res = new Response();
        int code  = json.getInt("code");
        Status status = getCodeStatus(code);
        res.code = code;
        res.status = status;
        /*
        System.out.println(code);
        System.out.println(status);
        System.out.println(json.toString());
        */
        if (status == Status.SUCCESS) {
            res.SuccessBody = new JSONObject();
            res.SuccessBody.put("eventsIngested", json.getLong("events_ingested"));
            res.SuccessBody.put("payloadSizeBytes", json.getInt("payload_size_bytes"));
            res.SuccessBody.put("serverUploadTime", json.getInt("server_upload_time"));
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
            JSONObject exceededDailyQuotaDevices = getJSONObjectValueWithKey(json, "exceeded_daily_quota_devices");
            res.RateLimitBody.put("exceededDailyQuotaDevices", exceededDailyQuotaDevices);
            JSONObject exceededDailyQuotaUsers = getJSONObjectValueWithKey(json, "exceeded_daily_quota_users");
            res.RateLimitBody.put("exceededDailyQuotaUsers", exceededDailyQuotaUsers);
            res.RateLimitBody.put("throttledEvents", convertJSONArrayToIntArray(json, "throttled_events"));
        } else if (status == Status.SYSTEM_ERROR) {
            res.error = json.getString("error");
            res.code = 0;
        }
        return res;
    }

    private static int[] convertJSONArrayToIntArray(JSONObject json, String key) {
        JSONArray jsonArray= json.getJSONArray(key);
        if (jsonArray == null) return new int[]{};
        else {
            int[] intArray = new int[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                intArray[i] = jsonArray.getInt(i);
            }
            return intArray;
        }

    }

    private static String getStringValueWithKey(JSONObject json, String key) {
        return json.getString(key) == null ? "" : json.getString(key);
    }

    private static JSONObject getJSONObjectValueWithKey(JSONObject json, String key) {
        return (json.has(key) && !json.isNull(key)) ? json.getJSONObject(key) : new JSONObject(key);
    }

    public static Status getCodeStatus(int code) {
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
