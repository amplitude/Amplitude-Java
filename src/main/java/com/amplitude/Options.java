package com.amplitude;

import org.json.JSONException;
import org.json.JSONObject;

public class Options {

    /**
     * Minimum length for user ID or device ID value.
     */
    public Integer minIdLength;

    public JSONObject toJsonObject() {
        JSONObject options = new JSONObject();
        try {
            if (minIdLength != null) options.put("min_id_length", minIdLength);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return options;
    }
}
