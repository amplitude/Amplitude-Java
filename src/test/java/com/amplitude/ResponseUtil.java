package com.amplitude;

import java.util.Arrays;
import java.util.ResourceBundle;

import org.json.JSONObject;

class ResponseUtil {
  public static Response getInvalidResponse(boolean withInvalidRequestBody) {
    Response invalidResponse = new Response();
    invalidResponse.status = Status.INVALID;
    invalidResponse.code = 400;
    if (withInvalidRequestBody) {
      invalidResponse.invalidRequestBody = new JSONObject();
      JSONObject eventsWithInvalidFields = new JSONObject();
      eventsWithInvalidFields.put("time", Arrays.asList(2, 3, 8));
      invalidResponse.invalidRequestBody.put("eventsWithInvalidFields", eventsWithInvalidFields);
      JSONObject eventsWithMissingFields = new JSONObject();
      eventsWithMissingFields.put("event_type", Arrays.asList(3, 4, 7));
      invalidResponse.invalidRequestBody.put("eventsWithMissingFields", eventsWithMissingFields);
    }
    return invalidResponse;
  }

  public static Response getPayloadTooLargeResponse() {
    Response payloadTooLargeResponse = new Response();
    payloadTooLargeResponse.status = Status.PAYLOAD_TOO_LARGE;
    payloadTooLargeResponse.code = 413;
    return payloadTooLargeResponse;
  }

  public static Response getSuccessResponse() {
    Response successResponse = new Response();
    successResponse.status = Status.SUCCESS;
    successResponse.code = 200;
    return successResponse;
  }

  public static Response getRateLimitResponse(boolean withExceedQuota) {
    Response rateLimitResponse = new Response();
    rateLimitResponse.status = Status.RATELIMIT;
    rateLimitResponse.code = 429;
    if (withExceedQuota) {
      rateLimitResponse.rateLimitBody = new JSONObject();
      JSONObject exceededDailyQuotaUsers = new JSONObject();
      exceededDailyQuotaUsers.put("test-user-id-0", 19);
      rateLimitResponse.rateLimitBody.put("exceededDailyQuotaUsers", exceededDailyQuotaUsers);
      JSONObject exceededDailyQuotaDevices = new JSONObject();
      exceededDailyQuotaDevices.put("test-device", 28);
      rateLimitResponse.rateLimitBody.put("exceededDailyQuotaDevices", exceededDailyQuotaDevices);
    }
    return rateLimitResponse;
  }

  public static Response getTimeoutResponse() {
    Response timeoutResponse = new Response();
    timeoutResponse.status = Status.TIMEOUT;
    timeoutResponse.code = 408;
    return timeoutResponse;
  }

  public static Response getFailedResponse() {
    Response failedResponse = new Response();
    failedResponse.status = Status.FAILED;
    failedResponse.code = 500;
    return failedResponse;
  }

  public static Response getUnknownResponse() {
    Response unknownResponse = new Response();
    unknownResponse.status = Status.UNKNOWN;
    unknownResponse.code = 0;
    return unknownResponse;
  }
}
