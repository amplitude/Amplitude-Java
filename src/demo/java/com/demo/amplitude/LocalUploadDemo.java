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

    // GROUPS AND GROUP PROPERTIES
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

    // Set group properties (groupIdentify)
    // This sets properties to a group or groups
    Event groupIdentifyEvent = new Event("$groupidentify", userId);
    groupIdentifyEvent.groups = groups;
    groupIdentifyEvent.groupProperties = groupProps;

    client.logEvent(setGroupEvent);
    client.logEvent(groupIdentifyEvent);
    client.logEvent(new Event("Test Event 1", userId));
    client.flushEvents();

    for (int i = 0; i < 10000000; i++) {
      Event ampEvent = new Event("General" + (i % 20), "Test_UserID_B" + (i % 5000));
      while (client.shouldWait(ampEvent)) {
        System.out.println("Client is busy. Waiting for log event " + ampEvent.eventType);
        TimeUnit.SECONDS.sleep(60L);
      }
      ampEvent.userProperties =
          new JSONObject()
              .put("property1", "p" + i)
              .put("property2", "p" + i)
              .put("property3", "p" + i)
              .put("property4", "p" + i)
              .put("property5", "p" + i);
      client.logEvent(ampEvent);
    }
  }
}
