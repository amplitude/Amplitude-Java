package com.amplitude;

import java.util.List;

public class GeneralHttpCall extends HttpCall {
  private static String apiUrl = Constants.API_URL;

  protected GeneralHttpCall(String apiKey) {
    this(apiKey, apiUrl);
  }

  protected GeneralHttpCall(String apiKey, String apiUrl) {
    super(apiKey, apiUrl);
  }
}
