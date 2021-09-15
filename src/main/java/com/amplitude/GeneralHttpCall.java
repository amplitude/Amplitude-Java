package com.amplitude;

public class GeneralHttpCall extends HttpCall {
  private static String apiUrl;

  protected GeneralHttpCall(String apiKey, String generalServerUrl) {
    super(apiKey);
    apiUrl = generalServerUrl;
  }

  @Override
  protected String getApiUrl() {
    return apiUrl;
  }
}
