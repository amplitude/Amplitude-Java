package com.amplitude;

import java.util.Map;

public class Amplitude {

    private static Map<String, Amplitude> instances;
    private JSONObject userProperties;
    private String apiKey;

    public static Amplitude getInstance(String instanceName) {
        if (!instances.containsKey(instanceName)) {
            Amplitude ampInstance = new Amplitude();
            newAmpInstance.put(instanceName, ampInstance);
        }
        else {
            return instances.get(instanceName);
        }
    }

    public void init(String key) {
        apiKey = key;
    }

    public void logEvent(String name) {
        logEvent(name, null);
    }

    public void logEvent(String name, Event event) {
        //Use HTTPUrlConnection object to make async HTTP request,
        //using data from event like device, class name, event props, etc.
    }

    public JSONObject getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(JSONObject userProperties) {
        this.userProperties = userProperties;
    }

}
