package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpCall {
  private final String apiKey;
  private final String serverUrl;
  private final Options options;

  protected HttpCall(String apiKey, String serverUrl) {
    this(apiKey, serverUrl, null);
  }

  protected HttpCall(String apiKey, String serverUrl, Options options) {
    this.apiKey = apiKey;
    this.serverUrl = serverUrl;
    this.options = options;
  }

  protected String getApiUrl() {
    return this.serverUrl;
  }

  protected Response makeRequest(List<Event> events) throws AmplitudeInvalidAPIKeyException {
    String apiUrl = getApiUrl();
    HttpsURLConnection connection;
    InputStream inputStream = null;
    int responseCode = 500;
    Response responseBody = new Response();
    try {
      connection = (HttpsURLConnection) new URL(apiUrl).openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Accept", "application/json");
      connection.setConnectTimeout(Constants.NETWORK_TIMEOUT_MILLIS);
      connection.setReadTimeout(Constants.NETWORK_TIMEOUT_MILLIS);
      connection.setDoOutput(true);

      JSONObject bodyJson = new JSONObject();
      bodyJson.put("api_key", this.apiKey);
      if(options != null) bodyJson.put("options", options.toJsonObject());

      JSONArray eventsArr = new JSONArray();
      for (int i = 0; i < events.size(); i++) {
        eventsArr.put(i, events.get(i).toJsonObject());
      }
      bodyJson.put("events", eventsArr);

      String bodyString = bodyJson.toString();
      OutputStream os = connection.getOutputStream();
      byte[] input = bodyString.getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);

      responseCode = connection.getResponseCode();
      boolean isErrorCode = responseCode >= Constants.HTTP_STATUS_BAD_REQ;
      if (!isErrorCode) {
        inputStream = connection.getInputStream();
      } else {
        inputStream = connection.getErrorStream();
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String output;
      while ((output = br.readLine()) != null) {
        sb.append(output);
      }
      JSONObject responseJson = new JSONObject(sb.toString());
      responseBody = Response.populateResponse(responseJson);
    } catch (IOException e) {
      // This handles UnknownHostException, when there is no internet connection.
      // SocketTimeoutException will be triggered when the HTTP request times out.
      JSONObject timesOutResponse = new JSONObject();
      timesOutResponse.put("status", Status.TIMEOUT);
      timesOutResponse.put("code", 408);
      responseBody = Response.populateResponse(timesOutResponse);
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
        }
      }
    }
    return responseBody;
  }
}
