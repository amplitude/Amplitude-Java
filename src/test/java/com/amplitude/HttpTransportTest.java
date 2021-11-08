package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import com.amplitude.util.EventsGenerator;

@ExtendWith(MockitoExtension.class)
public class HttpTransportTest {

  private HttpTransport httpTransport;

  @BeforeEach
  public void setUp() {
    httpTransport = new HttpTransport(null, null, new AmplitudeLog());
  }

  @ParameterizedTest
  @CsvSource({
    "SUCCESS, false",
    "INVALID, true",
    "RATELIMIT, true",
    "PAYLOAD_TOO_LARGE, true",
    "TIMEOUT, false",
    "FAILED, false",
    "UNKNOWN, false"
  })
  public void testShouldRetryForStatus(Status status, boolean expected) {
    assertEquals(expected, httpTransport.shouldRetryForStatus(status));
  }

  @Test
  public void testRetryEvents() throws AmplitudeInvalidAPIKeyException, InterruptedException {
    Response successResponse = getSuccessResponse();
    Response payloadTooLargeResponse = getPayloadTooLargeResponse();
    Response invalidResponse = getInvalidResponse(false);
    Response rateLimitResponse = getRateLimitResponse(false);

    HttpCall httpCall = mock(HttpCall.class);
    CountDownLatch latch = new CountDownLatch(4);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return invalidResponse;
            })
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return rateLimitResponse;
            })
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return payloadTooLargeResponse;
            })
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return successResponse;
            });

    List<Event> events = EventsGenerator.generateEvents(10);
    List<AmplitudeEventCallback> eventCallbacks = new ArrayList<>();
    Map<Event, Integer> resultMap = new HashMap<>();
    eventCallbacks.add(
        new AmplitudeEventCallback() {
          @Override
          public void onEventSent(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        });
    httpTransport.setHttpCall(httpCall);
    httpTransport.setEventCallbacks(eventCallbacks);
    httpTransport.retryEvents(events, invalidResponse);
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(4)).makeRequest(anyList());
    for (int i = 0; i < events.size(); i++) {
      if (i < (events.size() / 2)) {
        assertEquals(200, resultMap.get(events.get(i)));
      } else {
        assertEquals(413, resultMap.get(events.get(i)));
      }
    }
  }

  @Test
  public void testRetryEventsWithInvalidEvents()
      throws AmplitudeInvalidAPIKeyException, InterruptedException {
    Response invalidResponse = getInvalidResponse(true);
    Response successResponse = getSuccessResponse();

    HttpCall httpCall = mock(HttpCall.class);
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return successResponse;
            });

    List<Event> events = EventsGenerator.generateEvents(10);
    List<AmplitudeEventCallback> eventCallbacks = new ArrayList<>();
    Map<Event, Integer> resultMap = new HashMap<>();
    eventCallbacks.add(
        new AmplitudeEventCallback() {
          @Override
          public void onEventSent(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        });
    httpTransport.setHttpCall(httpCall);
    httpTransport.setEventCallbacks(eventCallbacks);
    httpTransport.retryEvents(events, invalidResponse);
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).makeRequest(anyList());
    int[] failedEventIndexes = {2, 3, 4, 7, 8};
    for (int i = 0; i < events.size(); i++) {
      if (Arrays.binarySearch(failedEventIndexes, i) < 0) {
        assertEquals(200, resultMap.get(events.get(i)));
      } else {
        assertEquals(400, resultMap.get(events.get(i)));
      }
    }
  }

  @Test
  public void testRetryEventsWithInvalidFieldsDuringRetry()
      throws AmplitudeInvalidAPIKeyException, InterruptedException {
    Response rateLimitResponse = getRateLimitResponse(false);
    Response invalidResponse = getInvalidResponse(true);
    Response successResponse = getSuccessResponse();
    HttpCall httpCall = mock(HttpCall.class);
    CountDownLatch latch = new CountDownLatch(2);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return invalidResponse;
            })
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return successResponse;
            });

    List<Event> events = EventsGenerator.generateEvents(10);
    List<AmplitudeEventCallback> eventCallbacks = new ArrayList<>();
    Map<Event, Integer> resultMap = new HashMap<>();
    eventCallbacks.add(
        new AmplitudeEventCallback() {
          @Override
          public void onEventSent(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        });
    httpTransport.setHttpCall(httpCall);
    httpTransport.setEventCallbacks(eventCallbacks);
    httpTransport.retryEvents(events, rateLimitResponse);
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(2)).makeRequest(anyList());
    int[] failedEventIndexes = {2, 3, 4, 7, 8};
    for (int i = 0; i < events.size(); i++) {
      if (Arrays.binarySearch(failedEventIndexes, i) < 0) {
        assertEquals(200, resultMap.get(events.get(i)));
      } else {
        assertEquals(400, resultMap.get(events.get(i)));
      }
    }
  }

  @Test
  public void testRetryEventWithUserExceedQuota() {
    Response rateLimitResponse = getRateLimitResponse(true);
    List<Event> events = EventsGenerator.generateEvents(10);
    List<AmplitudeEventCallback> eventCallbacks = new ArrayList<>();
    Map<Event, Integer> resultMap = new HashMap<>();
    eventCallbacks.add(
        new AmplitudeEventCallback() {
          @Override
          public void onEventSent(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        });
    httpTransport.setEventCallbacks(eventCallbacks);
    httpTransport.retryEvents(events, rateLimitResponse);
    for (int i = 0; i < events.size(); i++) {
      assertEquals(429, resultMap.get(events.get(i)));
    }
  }

  @Test
  public void testRetryEventWithUserExceedQuotaDuringRetry()
      throws AmplitudeInvalidAPIKeyException, InterruptedException {
    Response invalidResponse = getInvalidResponse(false);
    Response rateLimitResponse = getRateLimitResponse(true);
    Response successResponse = getSuccessResponse();
    HttpCall httpCall = mock(HttpCall.class);
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return rateLimitResponse;
            });

    List<Event> events = EventsGenerator.generateEvents(10);
    List<AmplitudeEventCallback> eventCallbacks = new ArrayList<>();
    Map<Event, Integer> resultMap = new HashMap<>();
    eventCallbacks.add(
        new AmplitudeEventCallback() {
          @Override
          public void onEventSent(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        });
    httpTransport.setHttpCall(httpCall);
    httpTransport.setEventCallbacks(eventCallbacks);
    httpTransport.retryEvents(events, invalidResponse);
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).makeRequest(anyList());
    for (int i = 0; i < events.size(); i++) {
      assertEquals(429, resultMap.get(events.get(i)));
    }
  }

  private Response getInvalidResponse(boolean withInvalidRequestBody) {
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

  private Response getPayloadTooLargeResponse() {
    Response payloadTooLargeResponse = new Response();
    payloadTooLargeResponse.status = Status.PAYLOAD_TOO_LARGE;
    payloadTooLargeResponse.code = 413;
    return payloadTooLargeResponse;
  }

  private Response getSuccessResponse() {
    Response successResponse = new Response();
    successResponse.status = Status.SUCCESS;
    successResponse.code = 200;
    return successResponse;
  }

  private Response getRateLimitResponse(boolean withExceedQuota) {
    Response rateLimitResponse = new Response();
    rateLimitResponse.status = Status.RATELIMIT;
    rateLimitResponse.code = 429;
    if (withExceedQuota) {
      rateLimitResponse.rateLimitBody = new JSONObject();
      JSONObject exceededDailyQuotaUsers = new JSONObject();
      exceededDailyQuotaUsers.put("test-user-id-0", true);
      rateLimitResponse.rateLimitBody.put("exceededDailyQuotaUsers", exceededDailyQuotaUsers);
      JSONObject exceededDailyQuotaDevices = new JSONObject();
      exceededDailyQuotaDevices.put("test-device", true);
      rateLimitResponse.rateLimitBody.put("exceededDailyQuotaDevices", exceededDailyQuotaDevices);
    }
    return rateLimitResponse;
  }
}
