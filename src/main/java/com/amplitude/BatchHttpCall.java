package com.amplitude;

public class BatchHttpCall extends HttpCall {
  private static String apiUrl;

  protected BatchHttpCall(String apiKey, String batchServerUrl) {
    super(apiKey);
    apiUrl = batchServerUrl;
  }

  @Override
  protected String getApiUrl() {
    return apiUrl;
  }
}
