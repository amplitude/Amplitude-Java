package com.amplitude;

import java.util.List;

public class GeneralHttpCall extends HttpCall {

  public GeneralHttpCall(List<Event> events, String apiKey, String apiUrl) {
    super(events, apiKey, apiUrl);
  }
}
