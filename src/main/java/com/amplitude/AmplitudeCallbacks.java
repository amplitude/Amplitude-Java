package com.amplitude;

/** AmplitudeCallbacks Class */
public class AmplitudeCallbacks {
  /**
   * Triggered when event is sent or failed
   *
   * @param event Amplitude Event
   * @param status server response status code
   * @param message message for callback, success or error
   */
  public void onLogEventServerResponse(Event event, int status, String message) {}
}
