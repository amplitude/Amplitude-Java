package com.amplitude;

public class GeneralHttpCall extends HttpCall {
  private static String apiUrl = Constants.API_URL;

  protected GeneralHttpCall(String apiKey, AmplitudeLog logger) {
    super(apiKey, logger);
  }

  @Override
  protected String getApiUrl() {
    return apiUrl;
  }
}
