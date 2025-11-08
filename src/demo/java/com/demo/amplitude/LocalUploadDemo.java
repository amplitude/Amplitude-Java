package com.demo.amplitude;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeCallbacks;
import com.amplitude.AmplitudeLog;
import com.amplitude.Event;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class LocalUploadDemo {

  public static void main(String[] args) throws InterruptedException {
    // Create and initialize Amplitude client
    String userId = "java_sdk_demo_user";
    Amplitude client = Amplitude.getInstance();
    client.init("");

    // use batch mode for higher throttling limits
    client.useBatchMode(true);

    // this config can print debug info into console, including response body of each requests
    client.setLogMode(AmplitudeLog.LogMode.DEBUG);

    // for large amount of events to send, config a higher update threshold to upload more events in
    // one request
    // payload size limit for batch api is 20MB, max events per request is 2000
    // https://developers.amplitude.com/docs/batch-event-upload-api#feature-comparison-between-httpapi-2httpapi--batch
    client.setEventUploadThreshold(1500);

    // config client to record throttled userId and deviceId. shouldWait(event) will return true if
    // userid or deviceid was throttled
    // can wait a short period of time and continue
    client.setRecordThrottledId(true);
    AmplitudeCallbacks callback =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            // call back functions here
            System.out.println(event.eventType + " " + event.userId + " " + status + " " + message);
          }
        };
    client.setCallbacks(callback);

    // GROUPS AND GROUP PROPERTIES - Traditional JSONObject approach
    JSONObject groups = new JSONObject()
            .put("org", "engineering")
            .put("department", "sdk");
    JSONObject groupProps = new JSONObject()
            .put("technology", "java")
            .put("location", "toronto");

    // Set group (setGroup)
    // This assigns a user to a group or groups
    Event setGroupEvent = new Event("$identify", userId);
    setGroupEvent.groups = groups;
    setGroupEvent.userProperties = groups;
    client.logEvent(setGroupEvent);

    // Set group properties (groupIdentify)
    // This sets properties to a group or groups
    Event groupIdentifyEvent = new Event("$groupidentify", userId);
    groupIdentifyEvent.groups = groups;
    groupIdentifyEvent.groupProperties = groupProps;
    client.logEvent(groupIdentifyEvent);

    // GROUPS AND GROUP PROPERTIES - Using new helper methods
    Event groupEventWithHelpers = new Event("$identify", userId)
        .setGroups(java.util.Map.of("org", "engineering", "department", "sdk"))
        .setUserProperties(java.util.Map.of("org", "engineering", "department", "sdk"));
    client.logEvent(groupEventWithHelpers);

    Event groupIdentifyWithHelpers = new Event("$groupidentify", userId)
        .addGroup("org", "engineering")
        .addGroup("department", "sdk")
        .addGroupProperty("technology", "java")
        .addGroupProperty("location", "toronto");
    client.logEvent(groupIdentifyWithHelpers);

    // Track an event
    client.logEvent(new Event("Test Event 1", userId));

    // USING NEW HELPER METHODS - Map-based approach
    Event eventWithMapProps = new Event("User Login", userId)
        .setEventProperties(java.util.Map.of("method", "email", "source", "web"))
        .setUserProperties(java.util.Map.of("plan", "premium", "age", 30));
    client.logEvent(eventWithMapProps);

    // USING NEW HELPER METHODS - Fluent builder approach
    Event eventWithFluentProps = new Event("Purchase Complete", userId)
        .addEventProperty("item_id", "SKU-123")
        .addEventProperty("price", 29.99)
        .addEventProperty("currency", "USD")
        .addUserProperty("total_purchases", 5)
        .addUserProperty("last_purchase_date", "2025-11-07");
    client.logEvent(eventWithFluentProps);

    // Flush events to the server
    client.flushEvents();

    for (int i = 0; i < 10000000; i++) {
      Event ampEvent = new Event("General" + (i % 20), "Test_UserID_B" + (i % 5000));
      while (client.shouldWait(ampEvent)) {
        System.out.println("Client is busy. Waiting for log event " + ampEvent.eventType);
        TimeUnit.SECONDS.sleep(60L);
      }
      // Traditional approach using JSONObject directly
      // ampEvent.userProperties =
      //     new JSONObject()
      //         .put("property1", "p" + i)
      //         .put("property2", "p" + i)
      //         .put("property3", "p" + i)
      //         .put("property4", "p" + i)
      //         .put("property5", "p" + i);

      // New approach using helper methods - cleaner and no JSONObject needed
      ampEvent.addUserProperty("property1", "p" + i)
              .addUserProperty("property2", "p" + i)
              .addUserProperty("property3", "p" + i)
              .addUserProperty("property4", "p" + i)
              .addUserProperty("property5", "p" + i);
      client.logEvent(ampEvent);
    }
  }
}
