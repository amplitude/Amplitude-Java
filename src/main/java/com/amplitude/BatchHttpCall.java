package com.amplitude;

import java.util.List;

public class BatchHttpCall extends HttpCall {
  private static String apiUrl = Constants.BATCH_API_URL;

  protected BatchHttpCall(List<Event> events, String apiKey) {
    this(events, apiKey, apiUrl);
  }

  protected BatchHttpCall(List<Event> events, String apiKey, String apiUrl) {
    super(events, apiKey, apiUrl);
  }
}
