package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class EventTest {
  @Test
  public void testCreateEventWithNullUserAndDeviceThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new Event("test event", null, null);
        },
        "Event must have one defined userId and/or deviceId");
  }

  @Test
  public void testToJsonObject() {
    String eventType = "test event type";
    String userId = "test-user";
    Event event = new Event(eventType, userId);
    String msg = "Long string to be truncated.";
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(msg);
    for (int i = 0; i < 7; i++) {
      stringBuilder.append(stringBuilder.toString());
    }
    String longStr = stringBuilder.toString();
    assertEquals(3584, longStr.length());
    JSONObject eventProperties = new JSONObject();
    eventProperties.put("event_type", eventType);
    JSONArray eventMsgArray = new JSONArray();
    eventMsgArray.put(longStr);
    eventMsgArray.put(longStr);
    eventProperties.put("event_message", eventMsgArray);
    event.eventProperties = eventProperties;

    IngestionMetadata ingestionMetadata = new IngestionMetadata();
    String sourceName = "ampli";
    String sourceVersion = "1.0.0";
    ingestionMetadata.setSourceName(sourceName)
            .setSourceVersion(sourceVersion);
    event.ingestionMetadata = ingestionMetadata;

    JSONObject truncatedEvent = event.toJsonObject();
    JSONArray truncatedEventMsgArray =
        truncatedEvent.getJSONObject("event_properties").getJSONArray("event_message");
    for (int i = 0; i < truncatedEventMsgArray.length(); i++) {
      String truncatedMsg = (String) truncatedEventMsgArray.get(i);
      assertEquals(Constants.MAX_STRING_LENGTH, truncatedMsg.length());
    }
    assertEquals(eventType, truncatedEvent.getString("event_type"));
    assertEquals(userId, truncatedEvent.getString("user_id"));
    assertEquals(
        Constants.SDK_LIBRARY + "/" + Constants.SDK_VERSION, truncatedEvent.getString("library"));
    assertEquals(-1, truncatedEvent.getLong("session_id"));
    assertEquals(sourceName, truncatedEvent.getJSONObject("ingestion_metadata").getString("source_name"));
    assertEquals(sourceVersion, truncatedEvent.getJSONObject("ingestion_metadata").getString("source_version"));
  }

  @Test
  public void testLogEventWithRevenue() {
    String eventType = "test event type";
    String userId = "test-user";
    Double price = 10.34;
    int quantity = 1;
    String productId = "test_product_id";
    String revenueType = "test_revenue_type";
    Event event = new Event(eventType, userId);
    event.revenue = price * quantity;
    event.price = price;
    event.productId = productId;
    event.revenueType = revenueType;
    event.currency = "USD";

    JSONObject truncatedEvent = event.toJsonObject();
    assertEquals(eventType, truncatedEvent.getString("event_type"));
    assertEquals(userId, truncatedEvent.getString("user_id"));
    assertEquals(price * quantity, truncatedEvent.getDouble("revenue"));
    assertEquals(price, truncatedEvent.getDouble("price"));
    assertEquals(quantity, truncatedEvent.getInt("quantity"));
    assertEquals(productId, truncatedEvent.getString("productId"));
    assertEquals(revenueType, truncatedEvent.getString("revenueType"));
    assertEquals("USD", truncatedEvent.getString("currency"));
  }

  @Test
  public void testLogEventWithoutRevenue() {
    String eventType = "test event type";
    String userId = "test-user";
    Event event = new Event(eventType, userId);

    JSONObject truncatedEvent = event.toJsonObject();
    assertEquals(eventType, truncatedEvent.getString("event_type"));
    assertEquals(userId, truncatedEvent.getString("user_id"));
    assertFalse(truncatedEvent.has("revenue"));
    assertFalse(truncatedEvent.has("price"));
    assertFalse(truncatedEvent.has("quantity"));
    assertFalse(truncatedEvent.has("productId"));
    assertFalse(truncatedEvent.has("revenueType"));
  }

  @Test
  public void testSetEventPropertiesWithMap() {
    Event event = new Event("test event", "test-user");
    Map<String, Object> properties = new HashMap<>();
    properties.put("key1", "value1");
    properties.put("key2", 123);
    properties.put("key3", true);

    event.setEventProperties(properties);

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject eventProps = jsonEvent.getJSONObject("event_properties");
    assertEquals("value1", eventProps.getString("key1"));
    assertEquals(123, eventProps.getInt("key2"));
    assertTrue(eventProps.getBoolean("key3"));
  }

  @Test
  public void testSetEventPropertiesWithNull() {
    Event event = new Event("test event", "test-user");
    event.eventProperties = new JSONObject().put("existing", "value");

    event.setEventProperties(null);

    assertNull(event.eventProperties);
  }

  @Test
  public void testAddEventProperty() {
    Event event = new Event("test event", "test-user");

    event.addEventProperty("prop1", "value1")
         .addEventProperty("prop2", 456)
         .addEventProperty("prop3", false);

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject eventProps = jsonEvent.getJSONObject("event_properties");
    assertEquals("value1", eventProps.getString("prop1"));
    assertEquals(456, eventProps.getInt("prop2"));
    assertFalse(eventProps.getBoolean("prop3"));
  }

  @Test
  public void testSetUserPropertiesWithMap() {
    Event event = new Event("test event", "test-user");
    Map<String, Object> properties = new HashMap<>();
    properties.put("name", "John Doe");
    properties.put("age", 30);
    properties.put("premium", true);

    event.setUserProperties(properties);

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject userProps = jsonEvent.getJSONObject("user_properties");
    assertEquals("John Doe", userProps.getString("name"));
    assertEquals(30, userProps.getInt("age"));
    assertTrue(userProps.getBoolean("premium"));
  }

  @Test
  public void testSetUserPropertiesWithNull() {
    Event event = new Event("test event", "test-user");
    event.userProperties = new JSONObject().put("existing", "value");

    event.setUserProperties(null);

    assertNull(event.userProperties);
  }

  @Test
  public void testAddUserProperty() {
    Event event = new Event("test event", "test-user");

    event.addUserProperty("city", "San Francisco")
         .addUserProperty("visits", 5)
         .addUserProperty("subscribed", true);

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject userProps = jsonEvent.getJSONObject("user_properties");
    assertEquals("San Francisco", userProps.getString("city"));
    assertEquals(5, userProps.getInt("visits"));
    assertTrue(userProps.getBoolean("subscribed"));
  }

  @Test
  public void testSetGroupsWithMap() {
    Event event = new Event("test event", "test-user");
    Map<String, Object> groups = new HashMap<>();
    groups.put("org", "engineering");
    groups.put("department", "sdk");

    event.setGroups(groups);

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject groupsJson = jsonEvent.getJSONObject("groups");
    assertEquals("engineering", groupsJson.getString("org"));
    assertEquals("sdk", groupsJson.getString("department"));
  }

  @Test
  public void testSetGroupsWithNull() {
    Event event = new Event("test event", "test-user");
    event.groups = new JSONObject().put("existing", "value");

    event.setGroups(null);

    assertNull(event.groups);
  }

  @Test
  public void testAddGroup() {
    Event event = new Event("test event", "test-user");

    event.addGroup("company", "Amplitude")
         .addGroup("team", "SDK");

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject groupsJson = jsonEvent.getJSONObject("groups");
    assertEquals("Amplitude", groupsJson.getString("company"));
    assertEquals("SDK", groupsJson.getString("team"));
  }

  @Test
  public void testSetGroupPropertiesWithMap() {
    Event event = new Event("test event", "test-user");
    Map<String, Object> groupProps = new HashMap<>();
    groupProps.put("technology", "java");
    groupProps.put("location", "toronto");

    event.setGroupProperties(groupProps);

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject groupPropsJson = jsonEvent.getJSONObject("group_properties");
    assertEquals("java", groupPropsJson.getString("technology"));
    assertEquals("toronto", groupPropsJson.getString("location"));
  }

  @Test
  public void testSetGroupPropertiesWithNull() {
    Event event = new Event("test event", "test-user");
    event.groupProperties = new JSONObject().put("existing", "value");

    event.setGroupProperties(null);

    assertNull(event.groupProperties);
  }

  @Test
  public void testAddGroupProperty() {
    Event event = new Event("test event", "test-user");

    event.addGroupProperty("size", 100)
         .addGroupProperty("region", "us-west");

    JSONObject jsonEvent = event.toJsonObject();
    JSONObject groupPropsJson = jsonEvent.getJSONObject("group_properties");
    assertEquals(100, groupPropsJson.getInt("size"));
    assertEquals("us-west", groupPropsJson.getString("region"));
  }

  @Test
  public void testMethodChaining() {
    Map<String, Object> eventProps = new HashMap<>();
    eventProps.put("event_key", "event_value");

    Map<String, Object> userProps = new HashMap<>();
    userProps.put("user_key", "user_value");

    Event event = new Event("test event", "test-user")
        .setEventProperties(eventProps)
        .setUserProperties(userProps)
        .addEventProperty("additional_event", "value")
        .addUserProperty("additional_user", "value");

    JSONObject jsonEvent = event.toJsonObject();
    assertEquals("event_value", jsonEvent.getJSONObject("event_properties").getString("event_key"));
    assertEquals("value", jsonEvent.getJSONObject("event_properties").getString("additional_event"));
    assertEquals("user_value", jsonEvent.getJSONObject("user_properties").getString("user_key"));
    assertEquals("value", jsonEvent.getJSONObject("user_properties").getString("additional_user"));
  }
}
