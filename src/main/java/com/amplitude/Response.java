package com.amplitude;

import org.json.JSONObject;

public class Response {
  protected int code;
  protected Status status;
  protected String error;
  protected JSONObject SuccessBody;
  protected JSONObject InvalidRequestBody;
  protected JSONObject RateLimitBody;

  protected static Response populateResponse(JSONObject json) {
    Response res = new Response();
    int code = json.getInt("code");
    Status status = Status.getCodeStatus(code);
    res.code = code;
    res.status = status;
    if (status == Status.SUCCESS) {
      res.SuccessBody = new JSONObject();
      res.SuccessBody.put("eventsIngested", json.getInt("events_ingested"));
      res.SuccessBody.put("payloadSizeBytes", json.getInt("payload_size_bytes"));
      res.SuccessBody.put("serverUploadTime", json.getLong("server_upload_time"));
    } else if (status == Status.INVALID) {
      res.InvalidRequestBody = new JSONObject();
      res.error = Utils.getStringValueWithKey(json, "error");
      res.InvalidRequestBody.put(
          "missingField", Utils.getStringValueWithKey(json, "missing_field"));
      JSONObject eventsWithInvalidFields =
          Utils.getJSONObjectValueWithKey(json, "events_with_invalid_fields");
      res.InvalidRequestBody.put("eventsWithInvalidFields", eventsWithInvalidFields);
      JSONObject eventsWithMissingFields =
          Utils.getJSONObjectValueWithKey(json, "events_with_missing_fields");
      res.InvalidRequestBody.put("eventsWithMissingFields", eventsWithMissingFields);
    } else if (status == Status.PAYLOAD_TOO_LARGE) {
      res.error = Utils.getStringValueWithKey(json, "error");
    } else if (status == Status.RATELIMIT) {
      res.error = Utils.getStringValueWithKey(json, "error");
      res.RateLimitBody = new JSONObject();
      res.RateLimitBody.put("epsThreshold", json.getInt("eps_threshold"));
      JSONObject throttledDevices = Utils.getJSONObjectValueWithKey(json, "throttled_devices");
      res.RateLimitBody.put("throttledDevices", throttledDevices);
      JSONObject throttledUsers = Utils.getJSONObjectValueWithKey(json, "throttled_users");
      res.RateLimitBody.put("throttledUsers", throttledUsers);
      res.RateLimitBody.put(
          "throttledEvents", Utils.convertJSONArrayToIntArray(json, "throttled_events"));
      JSONObject exceededDailyQuotaDevices =
          Utils.getJSONObjectValueWithKey(json, "exceeded_daily_quota_devices");
      res.RateLimitBody.put("exceededDailyQuotaDevices", exceededDailyQuotaDevices);
      JSONObject exceededDailyQuotaUsers =
          Utils.getJSONObjectValueWithKey(json, "exceeded_daily_quota_users");
      res.RateLimitBody.put("exceededDailyQuotaUsers", exceededDailyQuotaUsers);
    } else if (status == Status.SYSTEM_ERROR) {
      res.error = json.getString("error");
      res.code = 0;
    }
    return res;
  }
}
