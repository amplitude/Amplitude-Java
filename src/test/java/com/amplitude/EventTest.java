package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  }
}
