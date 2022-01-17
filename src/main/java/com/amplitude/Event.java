package com.amplitude;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.UUID;

public class Event {
  /** A unique identifier for your event. */
  public String eventType;

  /**
   * A readable ID specified by you. Must have a minimum length of 5 characters. Required unless
   * device_id is present.
   */
  public String userId;

  /**
   * A device-specific identifier, such as the Identifier for Vendor on iOS. Required unless user_id
   * is present. If a device_id is not sent with the event, it will be set to a hashed version of
   * the user_id.
   */
  public String deviceId;

  /**
   * The timestamp of the event in milliseconds since epoch. If time is not sent with the event, it
   * will be set to the request upload time.
   */
  public long timestamp = System.currentTimeMillis();

  /** The current Latitude of the user. */
  public double locationLat;

  /** The current Longitude of the user. */
  public double locationLng;

  /** The current version of your application. */
  public String appVersion;

  /** Legacy SDK API, only for use with older API. The application's version name. */
  public String versionName;

  /** Platform of the device. */
  public String platform;

  /** The name of the mobile operating system or browser that the user is using. */
  public String osName;

  /** The device brand that the user is using. */
  public String deviceBrand;

  /** The device manufacturer that the user is using. */
  public String deviceManufacturer;

  /** The device model that the user is using. */
  public String deviceModel;

  /** The carrier that the user is using. */
  public String carrier;

  /** The current country of the user. */
  public String country;

  /** The current region of the user. */
  public String region;

  /** The current city of the user. */
  public String city;

  /** The current Designated Market Area of the user. */
  public String dma;

  /** (iOS) Identifier for Advertiser. */
  public String idfa;

  /** (iOS) Identifier for Vendor. */
  public String idfv;

  /** (Android) Google Play Services advertising ID */
  public String adid;

  /** (Android) Android ID (not the advertising ID) */
  public String androidId;

  /** The (human) language set by the user. */
  public String language;

  /**
   * The IP address of the user. Use "$remote" to use the IP address on the upload request. We will
   * use the IP address to reverse lookup a user's location (city, country, region, and DMA).
   * Amplitude has the ability to drop the location and IP address from events once it reaches our
   * servers. You can submit a request to our platform specialist team here to configure this for
   * you.
   */
  public String ip;

  /**
   * A dictionary of key-value pairs that represent additional data to be sent along with the event.
   * You can store property values in an array. Date values are transformed into string values.
   * Object depth may not exceed 40 layers.
   */
  public JSONObject eventProperties;

  /**
   * A dictionary of key-value pairs that represent additional data tied to the user. You can store
   * property values in an array. Date values are transformed into string values. Object depth may
   * not exceed 40 layers.
   */
  public JSONObject userProperties;

  /**
   * The price of the item purchased. Required for revenue data if the revenue field is not sent.
   * You can use negative values to indicate refunds.
   */
  public Double price;

  /** The quantity of the item purchased. Defaults to 1 if not specified. */
  public int quantity;

  /**
   * revenue = price * quantity. If you send all 3 fields of price, quantity, and revenue, then
   * (price * quantity) will be used as the revenue value. You can use negative values to indicate
   * refunds.
   */
  public Double revenue;

  /**
   * An identifier for the item purchased. You must send a price and quantity or revenue with this
   * field.
   */
  public String productId;

  /**
   * The type of revenue for the item purchased. You must send a price and quantity or revenue with
   * this field.
   */
  public String revenueType;

  /**
   * An incrementing counter to distinguish events with the same user_id and timestamp from each
   * other. We recommend you send an event_id, increasing over time, especially if you expect events
   * to occur simultanenously.
   */
  public int eventId;

  /**
   * The start time of the session in milliseconds since epoch (Unix Timestamp), necessary if you
   * want to associate events with a particular system. A session_id of -1 is the same as no
   * session_id specified.
   */
  public long sessionId = -1;

  /**
   * A unique identifier for the event. We will deduplicate subsequent events sent with an insert_id
   * we have already seen before within the past 7 days. We recommend generation a UUID or using
   * some combination of device_id, user_id, event_type, event_id, and time.
   */
  public String insertId = UUID.randomUUID().toString();

  /**
   * This feature is only available to Enterprise customers who have purchased the Accounts add-on.
   * This field adds a dictionary of key-value pairs that represent groups of users to the event as
   * an event-level group. You can only track up to 5 groups.
   */
  public JSONObject groups;

  /**
   * This feature is only available to Enterprise customers who have purchased the Accounts add-on.
   * A dictionary of key-value pairs that represent data tied to the groups.
   */
  public JSONObject groupProperties;

  /**
   * Callback for Event
   */
  protected AmplitudeCallbacks callback;

  /**
   * Create a new event with the required items. `eventType` is required, and at least one of
   * `userId` or `deviceId` is required. This method sets deviceId to null.
   *
   * @param eventType A unique identifier for your event
   * @param userId A readable ID specified by you. Must have a minimum length of 5 characters.
   *     Required unless device_id is present.
   */
  public Event(String eventType, String userId) {
    this(eventType, userId, null);
  }

  /**
   * Create a new event with the required items. `eventType` is required, and at least one of
   * `userId` or `deviceId` is required.
   *
   * @param eventType A unique identifier for your event
   * @param userId A readable ID specified by you. Must have a minimum length of 5 characters.
   *     Required unless device_id is present.
   * @param deviceId A device-specific identifier, such as the Identifier for Vendor on iOS.
   *     Required unless user_id is present. If a device_id is not sent with the event, it will be
   *     set to a hashed version of the user_id.
   */
  public Event(String eventType, String userId, String deviceId) {
    this.eventType = eventType;
    if (userId == null && deviceId == null) {
      throw new IllegalArgumentException("Event must have one defined userId and/or deviceId");
    }
    this.userId = userId;
    this.deviceId = deviceId;
  }

  /** @return the JSONObject that represents the event data of this event */
  public JSONObject toJsonObject() {
    JSONObject event = new JSONObject();
    try {
      event.put("event_type", eventType);
      event.put("user_id", replaceWithJSONNull(userId));
      event.put("device_id", replaceWithJSONNull(deviceId));
      event.put("time", timestamp);
      event.put("location_lat", locationLat);
      event.put("location_lng", locationLng);
      event.put("app_version", appVersion);
      event.put("version_name", replaceWithJSONNull(versionName));
      event.put("library", Constants.SDK_LIBRARY + "/" + Constants.SDK_VERSION);
      event.put("platform", replaceWithJSONNull(platform));
      event.put("os_name", replaceWithJSONNull(osName));
      event.put("device_brand", replaceWithJSONNull(deviceBrand));
      event.put("device_manufacturer", replaceWithJSONNull(deviceManufacturer));
      event.put("device_model", replaceWithJSONNull(deviceModel));
      event.put("carrier", replaceWithJSONNull(carrier));
      event.put("country", replaceWithJSONNull(country));
      event.put("region", replaceWithJSONNull(region));
      event.put("city", replaceWithJSONNull(city));
      event.put("dma", replaceWithJSONNull(dma));
      event.put("idfa", replaceWithJSONNull(idfa));
      event.put("idfv", replaceWithJSONNull(idfv));
      event.put("adid", replaceWithJSONNull(adid));
      event.put("android_id", replaceWithJSONNull(androidId));
      event.put("language", replaceWithJSONNull(language));
      event.put("ip", replaceWithJSONNull(ip));
      event.put(
          "event_properties",
          (eventProperties == null) ? new JSONObject() : truncate(eventProperties));
      event.put(
          "user_properties",
          (userProperties == null) ? new JSONObject() : truncate(userProperties));

      boolean shouldLogRevenueProps = (revenue != null || price != null);
      if (shouldLogRevenueProps) {
        int eventQuantity = quantity > 0 ? quantity : 1;
        event.put("price", price);
        event.put("quantity", eventQuantity);
        event.put("revenue", revenue);
        event.put("productId", productId);
        event.put("revenueType", revenueType);
      }

      event.put("event_id", replaceWithJSONNull(eventId));
      event.put("session_id", sessionId); // session_id = -1 if outOfSession = true;
      event.put("insert_id", insertId);
      event.put("groups", (groups == null) ? new JSONObject() : truncate(groups));
      event.put(
          "group_properties",
          (groupProperties == null) ? new JSONObject() : truncate(groupProperties));
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return event;
  }

  /** internal method */
  protected Object replaceWithJSONNull(Object obj) {
    return obj == null ? JSONObject.NULL : obj;
  }

  protected JSONObject truncate(JSONObject object) {
    if (object == null) {
      return new JSONObject();
    }

    if (object.length() > Constants.MAX_PROPERTY_KEYS) {
      throw new IllegalArgumentException(
          "Too many properties (more than " + Constants.MAX_PROPERTY_KEYS + ") in JSON");
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
        throw new IllegalArgumentException(
            "JSON parsing error. Too long (>"
                + Constants.MAX_STRING_LENGTH
                + " chars) or invalid JSON");
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

  protected String truncate(String value) {
    return value.length() <= Constants.MAX_STRING_LENGTH
        ? value
        : value.substring(0, Constants.MAX_STRING_LENGTH);
  }
}
