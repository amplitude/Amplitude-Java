package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    "TIMEOUT, true",
    "FAILED, false",
    "UNKNOWN, false"
  })
  public void testShouldRetryForStatus(Status status, boolean expected) {
    assertEquals(expected, httpTransport.shouldRetryForStatus(status));
  }

  @Test
  public void testRetryEvents() throws AmplitudeInvalidAPIKeyException, InterruptedException {
    Response successResponse = ResponseUtil.getSuccessResponse();
    Response payloadTooLargeResponse = ResponseUtil.getPayloadTooLargeResponse();
    Response invalidResponse = ResponseUtil.getInvalidResponse(false);
    Response rateLimitResponse = ResponseUtil.getRateLimitResponse(false);

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
    Map<Event, Integer> resultMap = new HashMap<>();
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        };
    httpTransport.setHttpCall(httpCall);
    httpTransport.setCallbacks(callbacks);
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
    Response invalidResponse = ResponseUtil.getInvalidResponse(true);
    Response successResponse = ResponseUtil.getSuccessResponse();

    HttpCall httpCall = mock(HttpCall.class);
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return successResponse;
            });

    List<Event> events = EventsGenerator.generateEvents(10);
    Map<Event, Integer> resultMap = new HashMap<>();
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        };
    httpTransport.setHttpCall(httpCall);
    httpTransport.setCallbacks(callbacks);
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
    Response rateLimitResponse = ResponseUtil.getRateLimitResponse(false);
    Response invalidResponse = ResponseUtil.getInvalidResponse(true);
    Response successResponse = ResponseUtil.getSuccessResponse();
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
    Map<Event, Integer> resultMap = new HashMap<>();
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        };
    httpTransport.setHttpCall(httpCall);
    httpTransport.setCallbacks(callbacks);
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
    Response rateLimitResponse = ResponseUtil.getRateLimitResponse(true);
    List<Event> events = EventsGenerator.generateEvents(10);
    Map<Event, Integer> resultMap = new HashMap<>();
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        };
    httpTransport.setCallbacks(callbacks);
    httpTransport.retryEvents(events, rateLimitResponse);
    for (int i = 0; i < events.size(); i++) {
      assertEquals(429, resultMap.get(events.get(i)));
    }
  }

  @Test
  public void testRetryEventWithUserExceedQuotaDuringRetry()
      throws AmplitudeInvalidAPIKeyException, InterruptedException {
    Response invalidResponse = ResponseUtil.getInvalidResponse(false);
    Response rateLimitResponse = ResponseUtil.getRateLimitResponse(true);
    Response successResponse = ResponseUtil.getSuccessResponse();
    HttpCall httpCall = mock(HttpCall.class);
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return rateLimitResponse;
            });

    List<Event> events = EventsGenerator.generateEvents(10);
    Map<Event, Integer> resultMap = new HashMap<>();
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            resultMap.put(event, status);
          }
        };
    httpTransport.setHttpCall(httpCall);
    httpTransport.setCallbacks(callbacks);
    httpTransport.retryEvents(events, invalidResponse);
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).makeRequest(anyList());
    for (int i = 0; i < events.size(); i++) {
      assertEquals(429, resultMap.get(events.get(i)));
    }
  }

    @Test
    public void testRetryEventWithTimeout()
            throws AmplitudeInvalidAPIKeyException, InterruptedException {
        Response timeoutResponse = ResponseUtil.getTimeoutResponse();
        Response successResponse = ResponseUtil.getSuccessResponse();
        HttpCall httpCall = mock(HttpCall.class);
        CountDownLatch latch = new CountDownLatch(2);
        when(httpCall.makeRequest(anyList()))
        .thenAnswer(
                invocation -> {
                    latch.countDown();
                    return timeoutResponse;
                })
        .thenAnswer(
                invocation -> {
                    latch.countDown();
                    return successResponse;
                });

        List<Event> events = EventsGenerator.generateEvents(10);
        Map<Event, Integer> resultMap = new HashMap<>();
        AmplitudeCallbacks callbacks =
                new AmplitudeCallbacks() {
                    @Override
                    public void onLogEventServerResponse(Event event, int status, String message) {
                        resultMap.put(event, status);
                    }
                };
        httpTransport.setHttpCall(httpCall);
        httpTransport.setCallbacks(callbacks);
        httpTransport.retryEvents(events, timeoutResponse);
        assertTrue(latch.await(1L, TimeUnit.SECONDS));
        verify(httpCall, times(2)).makeRequest(anyList());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(200, resultMap.get(events.get(i)));
        }
    }

    @Test
    public void testFailedResponse()
            throws AmplitudeInvalidAPIKeyException, InterruptedException {
        Response failedResponse = ResponseUtil.getFailedResponse();
        HttpCall httpCall = mock(HttpCall.class);
        CountDownLatch latch = new CountDownLatch(1);
        when(httpCall.makeRequest(anyList()))
                .thenAnswer(
                        invocation -> {
                            latch.countDown();
                            return failedResponse;
                        });

        List<Event> events = EventsGenerator.generateEvents(10);
        Map<Event, Integer> resultMap = new HashMap<>();
        AmplitudeCallbacks callbacks =
                new AmplitudeCallbacks() {
                    @Override
                    public void onLogEventServerResponse(Event event, int status, String message) {
                        resultMap.put(event, status);
                    }
                };
        httpTransport.setHttpCall(httpCall);
        httpTransport.setCallbacks(callbacks);
        httpTransport.retryEvents(events, ResponseUtil.getInvalidResponse(false));
        assertTrue(latch.await(1L, TimeUnit.SECONDS));
        verify(httpCall, times(1)).makeRequest(anyList());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(500, resultMap.get(events.get(i)));
        }
    }

    @Test
    public void testUnknownResponse()
            throws AmplitudeInvalidAPIKeyException, InterruptedException {
        Response unknownResponse = ResponseUtil.getUnknownResponse();
        HttpCall httpCall = mock(HttpCall.class);
        CountDownLatch latch = new CountDownLatch(1);
        when(httpCall.makeRequest(anyList()))
                .thenAnswer(
                        invocation -> {
                            latch.countDown();
                            return unknownResponse;
                        });

        List<Event> events = EventsGenerator.generateEvents(10);
        Map<Event, Integer> resultMap = new HashMap<>();
        AmplitudeCallbacks callbacks =
                new AmplitudeCallbacks() {
                    @Override
                    public void onLogEventServerResponse(Event event, int status, String message) {
                        resultMap.put(event, status);
                    }
                };
        httpTransport.setHttpCall(httpCall);
        httpTransport.setCallbacks(callbacks);
        httpTransport.retryEvents(events, ResponseUtil.getInvalidResponse(false));
        assertTrue(latch.await(1L, TimeUnit.SECONDS));
        verify(httpCall, times(1)).makeRequest(anyList());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(0, resultMap.get(events.get(i)));
        }
    }
}
