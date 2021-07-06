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
    Event event = new Event("test event", "test-user");
    String longStr =
        "This is a long string that is too long for an event, and going to be truncated later when we send the event.";
    for (int i = 0; i < 7; i++) {
      longStr += longStr;
    }
    JSONObject eventProperties = new JSONObject();
    eventProperties.put("event_type", "test event type");
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
  }
}
