package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import com.amplitude.util.EventsGenerator;

@ExtendWith(MockitoExtension.class)
public class RetryTest {

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
    assertEquals(expected, Retry.shouldRetryForStatus(status));
  }

  @Test
  public void testSendEventsWithRetry()
      throws AmplitudeInvalidAPIKeyException, InterruptedException {
    Response successResponse = new Response();
    successResponse.status = Status.SUCCESS;

    Response payloadTooLargeResponse = new Response();
    payloadTooLargeResponse.status = Status.PAYLOAD_TOO_LARGE;

    Response invalidResponse = new Response();
    invalidResponse.status = Status.INVALID;

    Response rateLimitResponse = new Response();
    rateLimitResponse.status = Status.RATELIMIT;

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
    Retry.sendEventsWithRetry(events, invalidResponse, httpCall);
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(4)).makeRequest(anyList());
  }
}
