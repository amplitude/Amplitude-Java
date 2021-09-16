package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import com.amplitude.util.EventsGenerator;

@ExtendWith(MockitoExtension.class)
public class AmplitudeTest {

  private final String apiKey = "test-apiKey";

  @Test
  public void testGetInstance() {
    Amplitude a = Amplitude.getInstance();
    Amplitude b = Amplitude.getInstance("");
    Amplitude d = Amplitude.getInstance("app1");
    Amplitude e = Amplitude.getInstance("app2");

    assertSame(a, b);
    assertNotSame(d, e);
    assertNotSame(a, d);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testLogEventSuccess(boolean useBatch)
      throws InterruptedException, NoSuchFieldException, IllegalAccessException,
          AmplitudeInvalidAPIKeyException {
    Amplitude amplitude = Amplitude.getInstance("test");
    amplitude.init(apiKey);
    amplitude.useBatchMode(useBatch);
    amplitude.setLogMode(AmplitudeLog.LogMode.OFF);
    List<Event> events = EventsGenerator.generateEvents(10, 5, 6);
    HttpCall httpCall = getMockHttpCall(amplitude, useBatch);
    Response response = new Response();
    response.code = 200;
    response.status = Status.SUCCESS;
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.syncHttpCallWithEventsBuffer(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return response;
            });
    for (Event event : events) {
      amplitude.logEvent(event);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).syncHttpCallWithEventsBuffer(anyList());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testLogEventWithInvalidKeyException(boolean useBatch)
      throws InterruptedException, NoSuchFieldException, IllegalAccessException,
          AmplitudeInvalidAPIKeyException {
    Amplitude amplitude = Amplitude.getInstance("test");
    amplitude.init(apiKey);
    amplitude.useBatchMode(useBatch);
    amplitude.setLogMode(AmplitudeLog.LogMode.OFF);
    List<Event> events = EventsGenerator.generateEvents(10, 5, 6);
    HttpCall httpCall = getMockHttpCall(amplitude, useBatch);
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.syncHttpCallWithEventsBuffer(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              throw new AmplitudeInvalidAPIKeyException("test");
            });
    for (Event event : events) {
      amplitude.logEvent(event);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).syncHttpCallWithEventsBuffer(anyList());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testLogEventWithInvalidResponse(boolean useBatch)
      throws InterruptedException, NoSuchFieldException, IllegalAccessException,
          AmplitudeInvalidAPIKeyException {
    Amplitude amplitude = Amplitude.getInstance("test");
    amplitude.init(apiKey);
    amplitude.useBatchMode(useBatch);
    amplitude.setLogMode(AmplitudeLog.LogMode.OFF);
    List<Event> events = EventsGenerator.generateEvents(10, 5, 6);
    HttpCall httpCall = getMockHttpCall(amplitude, useBatch);
    Response response = new Response();
    response.code = 400;
    response.status = Status.INVALID;
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.syncHttpCallWithEventsBuffer(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return response;
            });
    for (Event event : events) {
      amplitude.logEvent(event);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, atLeast(1)).syncHttpCallWithEventsBuffer(anyList());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testDefaultServerUrl(boolean useBatch)
      throws NoSuchFieldException, IllegalAccessException {
    Amplitude amplitude = Amplitude.getInstance("test");
    amplitude.init(apiKey);
    amplitude.useBatchMode(useBatch);

    Field httpCallField = amplitude.getClass().getDeclaredField("httpCall");
    httpCallField.setAccessible(true);
    HttpCall httpCall = (HttpCall) httpCallField.get(amplitude);
    System.out.println(httpCall.getApiUrl());
    assertEquals(httpCall.getApiUrl(), useBatch ? Constants.BATCH_API_URL : Constants.API_URL);
  }

  @Test
  public void testSetServerUrl() throws NoSuchFieldException, IllegalAccessException {
    String testServerUrl = "https://api.eu.amplitude.com/2/httpapi";
    Amplitude amplitude = Amplitude.getInstance("testServerUrl");
    amplitude.init(apiKey);
    amplitude.setServerUrl(testServerUrl);

    Field httpCallField = amplitude.getClass().getDeclaredField("httpCall");
    httpCallField.setAccessible(true);
    HttpCall httpCall = (HttpCall) httpCallField.get(amplitude);
    assertEquals(httpCall.getApiUrl(), testServerUrl);
  }

  private HttpCall getMockHttpCall(Amplitude amplitude, boolean useBatch)
      throws NoSuchFieldException, IllegalAccessException {
    HttpCall httpCall;
    if (useBatch) {
      httpCall = mock(BatchHttpCall.class);
    } else {
      httpCall = mock(GeneralHttpCall.class);
    }
    Field httpCallField = amplitude.getClass().getDeclaredField("httpCall");
    httpCallField.setAccessible(true);
    httpCallField.set(amplitude, httpCall);
    return httpCall;
  }
}
