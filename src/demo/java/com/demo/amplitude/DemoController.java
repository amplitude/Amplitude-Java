package com.demo.amplitude;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeCallbacks;
import com.amplitude.AmplitudeLog;
import com.amplitude.Event;

@RestController
public class DemoController {
  @RequestMapping("/")
  public String index() {
    Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
    amplitude.init("");
    amplitude.logEvent(new Event("Test Event", "test_user_id"));
    amplitude.setLogMode(AmplitudeLog.LogMode.DEBUG);
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            System.out.println(
                String.format(
                    "Event: %s sent. Status: %s, Message: %s", event.eventType, status, message));
          }
        };
    amplitude.setCallbacks(callbacks);
    amplitude.logEvent(new Event("Test Event", "test_user_id"));
    amplitude.logEvent(
        new Event("Test Event with Callback", "test_user_id"),
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            System.out.println(
                String.format("Event: %s sent with event callbacks", event.eventType));
          }
        });
    return "Amplitude Java SDK Demo: sending test event.";
  }
}
