package com.amplitude;

import java.util.List;

public class GeneralHttpCall extends HttpCall {
  private static String apiUrl = Constants.API_URL;

  protected GeneralHttpCall(List<Event> events, String apiKey) {
    this(events, apiKey, apiUrl);
  }

  protected GeneralHttpCall(List<Event> events, String apiKey, String apiUrl) {
    super(events, apiKey, apiUrl);
  }
}
