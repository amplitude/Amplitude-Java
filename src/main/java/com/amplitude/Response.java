package com.amplitude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
      if (hasInvalidAPIKey(res.error)) throw new AmplitudeInvalidAPIKeyException();
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

  protected boolean isUserOrDeviceExceedQuote(String userId, String deviceId) {
    if (status == Status.RATELIMIT && rateLimitBody != null) {
      JSONObject exceededDailyQuotaUsers = rateLimitBody.getJSONObject("exceededDailyQuotaUsers");
      JSONObject exceededDailyQuotaDevices = rateLimitBody.getJSONObject("exceededDailyQuotaDevices");
      if ((userId.length() > 0 && exceededDailyQuotaUsers.has(userId))
          || (deviceId.length() > 0 && exceededDailyQuotaDevices.has(deviceId))) {
        return true;
      }
    }
    return false;
  }

  protected int[] collectInvalidEventIndices() {
    if (status == Status.INVALID && invalidRequestBody != null) {
      List<Integer> invalidFieldsIndices =
          collectIndicesWithRequestBody(invalidRequestBody, "eventsWithInvalidFields");
      List<Integer> missingFieldsIndices =
          collectIndicesWithRequestBody(invalidRequestBody, "eventsWithMissingFields");
      invalidFieldsIndices.addAll(missingFieldsIndices);
      Collections.sort(invalidFieldsIndices);
      return invalidFieldsIndices.stream().distinct().mapToInt(i -> i).toArray();
    }
    return new int[] {};
  }

  private List<Integer> collectIndicesWithRequestBody(JSONObject requestBody, String key) {
    List<Integer> invalidIndices = new ArrayList<>();
    JSONObject fields = requestBody.getJSONObject(key);
    Iterator<String> fieldKeys = fields.keys();
    while (fieldKeys.hasNext()) {
      String fieldKey = fieldKeys.next();
      int[] eventIndices = Utils.jsonArrayToIntArray(fields.getJSONArray(fieldKey));
      for (int eventIndex : eventIndices) {
        invalidIndices.add(eventIndex);
      }
    }
    Collections.sort(invalidIndices);
    return invalidIndices;
  }
}
