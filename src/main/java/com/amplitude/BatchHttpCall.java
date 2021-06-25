package com.amplitude;

public class BatchHttpCall extends HttpCall {
  private static String apiUrl = Constants.BATCH_API_URL;

  protected BatchHttpCall(String apiKey) {
    super(apiKey);
  }

  @Override
  protected String getApiUrl() {
    return apiUrl;
  }
}
