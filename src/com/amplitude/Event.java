package com.amplitude;

import org.json.simple.JSONObject;

public class Event {
    // should move into Constants.java
    public static final int MAX_PROPERTY_KEYS = 1024;
    public static final int MAX_STRING_LENGTH = 1000;
    public static final String TAG = "com.amplitude.Event"; //AmplitudeClient.class.getName();

    private JSObject event;

    public Event(String eventName, JSONObject eventProps, JSONObject groups, JSONObject groupProps,
                 JSONObject userProps, String app_version,int event_id, int session_id, String insert_id,
                 String user_id, String device_id, boolean outOfSession, long timestamp) {
        this.event = new JSONObject();
        try {
            this.event.put("eventName", eventProps.getString("eventName"));
            this.event.put("eventProps", (eventProps == null) ? new JSONObject() : truncate(eventProps));

            this.event.put("groups", (groups == null) ? new JSONObject() : truncate(groups));
            this.event.put("groupProps", (groups == null) ? new JSONObject() : truncate(groups));

            this.event.put("outOfSession", (outOfSession == true) ?  true : false);
            this.event.put("timestamp", timestamp);

            this.event.put("userProps",(userProps == null) ? new JSONObject() : truncate(userProps));
            this.event.put("user_id", replaceWithJSONNull(user_id));
            this.event.put("device_id", replaceWithJSONNull(device_id));
            this.event.put("uuid", UUID.randomUUID().toString());
            this.event.put("session_id", (this.outOfsession) ? -1 : session_id); // session_id = -1 if outOfSession = true;

            this.event.put("app_version", app_version);

            this.event.put("event_id", replaceWithJSONNull(event_id));
            this.event.put("insert_id", replaceWithJSONNull(insert_id));

        } catch (JSONException e) {
            logger.e(TAG, String.format(
                "JSON Serialization of event failed, skipping: %s", e.toString()
            ));
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

        if (object.length() > MAX_STRING_LENGTH) {
            logger.w(TAG, "Warning: too many properties (more than 1000), ignoring");
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
                logger.e(TAG, e.toString());
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
        return value.length() <= MAX_PROPERTY_KEYS ? value :
                value.substring(0, MAX_PROPERTY_KEYS);
    }

}
