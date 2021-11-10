package com.amplitude;

/** AmplitudeCallbacks Interface */
public interface AmplitudeCallbacks {
  /**
   * Triggered when event is sent or failed
   *
   * @param event Amplitude Event
   * @param status server response status code
   * @param message message for callback, success or error
   */
  void onLogEventServerResponse(Event event, int status, String message);
}
