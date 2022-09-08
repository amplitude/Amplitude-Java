package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    JSONObject truncatedEvent = event.toJsonObject();
    assertEquals(eventType, truncatedEvent.getString("event_type"));
    assertEquals(userId, truncatedEvent.getString("user_id"));
    assertEquals(price * quantity, truncatedEvent.getDouble("revenue"));
    assertEquals(price, truncatedEvent.getDouble("price"));
    assertEquals(quantity, truncatedEvent.getInt("quantity"));
    assertEquals(productId, truncatedEvent.getString("productId"));
    assertEquals(revenueType, truncatedEvent.getString("revenueType"));
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
}
