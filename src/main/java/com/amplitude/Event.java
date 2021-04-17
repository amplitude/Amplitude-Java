package com.amplitude;

import java.util.Iterator;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Event {

    public static final String TAG = "com.amplitude.Event"; //AmplitudeClient.class.getName();
    @JsonProperty("event_type")
    private String event_type;
    @JsonProperty("user_id")
    public String user_id;
    @JsonProperty("device_id")
    public String device_id;

    @JsonProperty("time")
    public long time;
    @JsonProperty("loation_lat")
    public double loation_lat;
    @JsonProperty("location_lng")
    public double location_lng;

    @JsonProperty("app_version")
    public String app_version;
    @JsonProperty("version_name")
    public String version_name;
    @JsonProperty("library")
    public String library;

    @JsonProperty("platform")
    public String platform;
    @JsonProperty("os_name")
    public String os_name;
    @JsonProperty("device_brand")
    public String device_brand;
    @JsonProperty("device_manufacturer")
    public String device_manufacturer;
    @JsonProperty("device_model")
    public String device_model;
    @JsonProperty("carrier")
    public String carrier;

    @JsonProperty("country")
    public String country;
    @JsonProperty("region")
    public String region;
    @JsonProperty("city")
    public String city;
    @JsonProperty("dma")
    public String dma;

    @JsonProperty("idfa")
    public String idfa;
    @JsonProperty("idfv")
    public String idfv;
    @JsonProperty("adid")
    public String adid;
    @JsonProperty("android_id")
    public String android_id;

    @JsonProperty("language")
    public String language;
    @JsonProperty("ip")
    public String ip;
    @JsonProperty("uuid")
    public String uuid;
    @JsonProperty("event_properties")
    public JSONObject event_properties;
    @JsonProperty("user_properties")
    public JSONObject user_properties;

    @JsonProperty("price")
    public double price;
    @JsonProperty("quantity")
    public int quantity;
    @JsonProperty("revenue")
    public double revenue;
    @JsonProperty("productId")
    public int productId;
    @JsonProperty("revenueType")
    public String revenueType;

    @JsonProperty("event_id")
    public int event_id;
    @JsonProperty("session_id")
    public int session_id;
    @JsonProperty("insert_id")
    public int insert_id;

    @JsonProperty("groups")
    public JSONObject groups;

    public Event(String _eventName) {
        this.event_type = _eventName;
    };
}
