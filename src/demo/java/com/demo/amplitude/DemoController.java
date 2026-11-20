package com.demo.amplitude;

import com.amplitude.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
  @PostMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE)
  public String index() {
    Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
    amplitude.init("8e07b9d451a7d07bd33f6e9ba5870f21");
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
    // Set custom headers or minIdLength via Options
    amplitude.setOptions(new Options()
            .addHeader("Custom Header", "value")
            .setMinIdLength(5));
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
