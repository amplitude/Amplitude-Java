package com.amplitude;

import java.util.List;

public class BatchHttpCall extends HttpCall {
  private static String apiUrl = Constants.BATCH_API_URL;

  protected BatchHttpCall(String apiKey) {
    this(apiKey, apiUrl);
  }

  protected BatchHttpCall(String apiKey, String apiUrl) {
    super(apiKey, apiUrl);
  }
}
