package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;

import org.json.JSONObject;

public class Response {
  protected int code;
  protected Status status;
  protected String error;
  protected JSONObject successBody;
  protected JSONObject invalidRequestBody;
  protected JSONObject rateLimitBody;

  private static boolean hasInvalidAPIKey(String errorMsg) {
    String invalidAPIKeyError = "Invalid API key: .*";
    return errorMsg.matches(invalidAPIKeyError);
  }

  protected static Response populateResponse(JSONObject json)
      throws AmplitudeInvalidAPIKeyException {
    Response res = new Response();
    int code = json.getInt("code");
    Status status = Status.getCodeStatus(code);
    res.code = code;
    res.status = status;
    if (status == Status.SUCCESS) {
      res.successBody = new JSONObject();
      res.successBody.put("eventsIngested", json.getInt("events_ingested"));
      res.successBody.put("payloadSizeBytes", json.getInt("payload_size_bytes"));
      res.successBody.put("serverUploadTime", json.getLong("server_upload_time"));
    } else if (status == Status.INVALID) {
      res.error = Utils.getStringValueWithKey(json, "error");
      if (hasInvalidAPIKey(res.error)) throw new AmplitudeInvalidAPIKeyException(res.error);
      res.invalidRequestBody = new JSONObject();
      res.invalidRequestBody.put(
          "missingField", Utils.getStringValueWithKey(json, "missing_field"));
      JSONObject eventsWithInvalidFields =
          Utils.getJSONObjectValueWithKey(json, "events_with_invalid_fields");
      res.invalidRequestBody.put("eventsWithInvalidFields", eventsWithInvalidFields);
      JSONObject eventsWithMissingFields =
          Utils.getJSONObjectValueWithKey(json, "events_with_missing_fields");
      res.invalidRequestBody.put("eventsWithMissingFields", eventsWithMissingFields);
    } else if (status == Status.PAYLOAD_TOO_LARGE) {
      res.error = Utils.getStringValueWithKey(json, "error");
    } else if (status == Status.RATELIMIT) {
      res.error = Utils.getStringValueWithKey(json, "error");
      res.rateLimitBody = new JSONObject();
      res.rateLimitBody.put("epsThreshold", json.getInt("eps_threshold"));
      JSONObject throttledDevices = Utils.getJSONObjectValueWithKey(json, "throttled_devices");
      res.rateLimitBody.put("throttledDevices", throttledDevices);
      JSONObject throttledUsers = Utils.getJSONObjectValueWithKey(json, "throttled_users");
      res.rateLimitBody.put("throttledUsers", throttledUsers);
      res.rateLimitBody.put(
          "throttledEvents", Utils.convertJSONArrayToIntArray(json, "throttled_events"));
      JSONObject exceededDailyQuotaDevices =
          Utils.getJSONObjectValueWithKey(json, "exceeded_daily_quota_devices");
      res.rateLimitBody.put("exceededDailyQuotaDevices", exceededDailyQuotaDevices);
      JSONObject exceededDailyQuotaUsers =
          Utils.getJSONObjectValueWithKey(json, "exceeded_daily_quota_users");
      res.rateLimitBody.put("exceededDailyQuotaUsers", exceededDailyQuotaUsers);
    }
    return res;
  }
}
