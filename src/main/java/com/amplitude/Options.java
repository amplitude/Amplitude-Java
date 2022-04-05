package com.amplitude;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Options {

    /**
     * Minimum length for user ID or device ID value.
     */
    public Integer minIdLength;
    public Map<String, String> headers;

    public Options setMinIdLength(Integer minIdLength) {
        this.minIdLength = minIdLength;

        return this;
    }

    public Options setHeaders(Map<String, String> headers) {
        this.headers = headers;

        return this;
    }

    public Options addHeader(String key, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }

        this.headers.put(key, value);

        return this;
    }

    public JSONObject getEventPayloadJson() {
        JSONObject eventOptions = new JSONObject();
        try {
            if (minIdLength != null) eventOptions.put("min_id_length", minIdLength);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return eventOptions;
    }
}
