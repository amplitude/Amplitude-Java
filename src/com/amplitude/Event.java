package com.amplitude;

import java.util.Iterator;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Event {

    public static final String TAG = "com.amplitude.Event"; //AmplitudeClient.class.getName();

    private JSONObject event;
    public long timestamp;

    /*
     * Internal constructor used to create the event object
     * Ideally,
     */
    public Event(String eventName, JSONObject eventProps, JSONObject userProps,
                 String appVersion, String sdkVersion, int eventId, int sessionId,
                 String userId, long timestamp) {
        this.event = new JSONObject();
        try {
            this.event.put("eventName", eventProps.getString("eventName"));
            this.event.put("eventProps", (eventProps == null) ? new JSONObject() : truncate(eventProps));

            this.timestamp = timestamp;
            this.event.put("timestamp", timestamp);

            this.event.put("userProps",(userProps == null) ? new JSONObject() : truncate(userProps));
            this.event.put("user_id", replaceWithJSONNull(userId));
            this.event.put("uuid", UUID.randomUUID().toString());
            this.event.put("session_id", sessionId); // session_id = -1 if outOfSession = true;

            this.event.put("app_version", appVersion);
            this.event.put("sdk_version", sdkVersion);

            this.event.put("event_id", replaceWithJSONNull(eventId));

        } catch (JSONException e) {
            System.out.println(String.format("JSON Serialization of event failed, skipping" + e.toString()));
        }
    }

    /**
    internal method
    */
    protected Object replaceWithJSONNull(Object obj) {
        return obj == null ? JSONObject.NULL : obj;
    }

    protected JSONObject truncate(JSONObject object) {
        if (object == null) {
            return new JSONObject();
        }

        if (object.length() > Constants.MAX_STRING_LENGTH) {
            System.out.println("Warning: too many properties (more than 1000), ignoring");
            return new JSONObject();
        }

        Iterator<?> keys = object.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();

            try {
                Object value = object.get(key);
                if (value.getClass().equals(String.class)) {
                    object.put(key, truncate((String) value));
                } else if (value.getClass().equals(JSONObject.class)) {
                    object.put(key, truncate((JSONObject) value));
                } else if (value.getClass().equals(JSONArray.class)) {
                    object.put(key, truncate((JSONArray) value));
                }
            } catch (JSONException e) {
                System.out.println(e.toString());
            }
        }

        return object;
    }

    protected JSONArray truncate(JSONArray array) throws JSONException {
        if (array == null) {
            return new JSONArray();
        }

        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value.getClass().equals(String.class)) {
                array.put(i, truncate((String) value));
            } else if (value.getClass().equals(JSONObject.class)) {
                array.put(i, truncate((JSONObject) value));
            } else if (value.getClass().equals(JSONArray.class)) {
                array.put(i, truncate((JSONArray) value));
            }
        }
        return array;
    }

    protected static String truncate(String value) {
        return value.length() <= Constants.MAX_PROPERTY_KEYS ? value :
                value.substring(0, Constants.MAX_PROPERTY_KEYS);
    }

    public String toString() {
        return this.event.toString();
    }

}
