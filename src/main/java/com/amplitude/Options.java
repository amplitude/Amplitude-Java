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

    public JSONObject toJsonObject() {
        JSONObject eventOptions = new JSONObject();
        Integer length = minIdLength;
        if (length != null) {
            try {
                eventOptions.put("min_id_length", length.intValue());
            } catch (JSONException ignored) {
                // Omit min_id_length if it cannot be serialized.
            }
        }
        return eventOptions;
    }
}
