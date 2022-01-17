package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import com.amplitude.util.EventsGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

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
    Amplitude amplitude = Amplitude.getInstance("testsuccess");
    amplitude.init(apiKey);
    amplitude.useBatchMode(useBatch);
    amplitude.setLogMode(AmplitudeLog.LogMode.OFF);
    amplitude.setEventUploadThreshold(5);
    List<Event> events = EventsGenerator.generateEvents(10, 5, 6);
    HttpCall httpCall = getMockHttpCall(amplitude, useBatch);
    Response response = ResponseUtil.getSuccessResponse();
    CountDownLatch latch = new CountDownLatch(2);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return response;
            });
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            assertEquals(200, status);
          }
        };
    amplitude.setCallbacks(callbacks);
    for (Event event : events) {
      amplitude.logEvent(event);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(2)).makeRequest(anyList());
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
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              throw new AmplitudeInvalidAPIKeyException();
            });
    for (Event event : events) {
      amplitude.logEvent(event);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).makeRequest(anyList());
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
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return response;
            });
    for (Event event : events) {
      amplitude.logEvent(event);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, atLeast(1)).makeRequest(anyList());
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
    assertEquals(httpCall.getApiUrl(), useBatch ? Constants.BATCH_API_URL : Constants.API_URL);
  }

  @Test
  public void testSetEventUploadThreshold() throws NoSuchFieldException, IllegalAccessException {
    Amplitude amplitude = Amplitude.getInstance("test");
    amplitude.init(apiKey);
    int updatedEventUploadThreshold = 5;
    amplitude.setEventUploadThreshold(updatedEventUploadThreshold);
    Field eventUploadThresholdField = amplitude.getClass().getDeclaredField("eventUploadThreshold");
    eventUploadThresholdField.setAccessible(true);
    int eventUploadThreshold = (Integer) eventUploadThresholdField.get(amplitude);
    assertEquals(eventUploadThreshold, updatedEventUploadThreshold);
  }

  @Test
  public void testSetEventUploadPeriodMillis() throws NoSuchFieldException, IllegalAccessException {
    Amplitude amplitude = Amplitude.getInstance("test");
    amplitude.init(apiKey);
    int updatedEventUploadPeriodMillis = 20000;
    amplitude.setEventUploadPeriodMillis(updatedEventUploadPeriodMillis);
    Field eventUploadPeriodMillisdField =
        amplitude.getClass().getDeclaredField("eventUploadPeriodMillis");
    eventUploadPeriodMillisdField.setAccessible(true);
    int eventUploadPeriodMillis = (Integer) eventUploadPeriodMillisdField.get(amplitude);
    assertEquals(eventUploadPeriodMillis, updatedEventUploadPeriodMillis);
  }

  @Test
  public void testEUBatchApiUrlPassedIntoHttpCallInstance()
      throws NoSuchFieldException, IllegalAccessException {
    String euBatchApiUrl = "https://api.eu.amplitude.com/batch";
    Amplitude amplitude = Amplitude.getInstance("testServerUrl");
    amplitude.init(apiKey);
    amplitude.useBatchMode(true);
    amplitude.setServerUrl(euBatchApiUrl);

    Field httpCallField = amplitude.getClass().getDeclaredField("httpCall");
    httpCallField.setAccessible(true);
    HttpCall httpCall = (HttpCall) httpCallField.get(amplitude);
    assertEquals(httpCall.getApiUrl(), euBatchApiUrl);
  }

  @Test
  public void testEUApiUrlPassedIntoHttpCallInstance()
      throws NoSuchFieldException, IllegalAccessException {
    String euApiUrl = "https://api.eu.amplitude.com/2/httpapi";
    Amplitude amplitude = Amplitude.getInstance("testServerUrl");
    amplitude.init(apiKey);
    amplitude.setServerUrl(euApiUrl);

    Field httpCallField = amplitude.getClass().getDeclaredField("httpCall");
    httpCallField.setAccessible(true);
    HttpCall httpCall = (HttpCall) httpCallField.get(amplitude);
    assertEquals(httpCall.getApiUrl(), euApiUrl);
  }

  @Test
  public void testSetCallback() throws NoSuchFieldException, IllegalAccessException {
    Amplitude amplitude = Amplitude.getInstance("testSetCallbacks");
    AmplitudeCallbacks callbacks =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {}
        };
    amplitude.setCallbacks(callbacks);
    assertEquals(callbacks, getCallbacks(amplitude));
  }

  @Test
  public void testLogEventWithCallbacks()
      throws InterruptedException, NoSuchFieldException, IllegalAccessException,
          AmplitudeInvalidAPIKeyException {
    Amplitude amplitude = Amplitude.getInstance("test");
    amplitude.init(apiKey);
    List<Event> events = EventsGenerator.generateEvents(10, 5, 6);
    HttpCall httpCall = getMockHttpCall(amplitude, false);
    Response successResponse = ResponseUtil.getSuccessResponse();
    CountDownLatch latch = new CountDownLatch(1);
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return successResponse;
            });
    AtomicInteger callbackCount1 = new AtomicInteger(0);
    AtomicInteger callbackCount2 = new AtomicInteger(0);
    AmplitudeCallbacks callbacks1 =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            assertEquals(200, status);
            callbackCount1.incrementAndGet();
          }
        };
    AmplitudeCallbacks callbacks2 =
        new AmplitudeCallbacks() {
          @Override
          public void onLogEventServerResponse(Event event, int status, String message) {
            assertEquals(200, status);
            callbackCount2.incrementAndGet();
          }
        };
    for (int i = 0; i < events.size(); i++) {
      AmplitudeCallbacks callbackForEvent = i % 2 == 0 ? callbacks1 : callbacks2;
      amplitude.logEvent(events.get(i), callbackForEvent);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).makeRequest(anyList());
    assertEquals(5, callbackCount1.get());
    assertEquals(5, callbackCount2.get());
  }

  private HttpCall getMockHttpCall(Amplitude amplitude, boolean useBatch)
      throws NoSuchFieldException, IllegalAccessException {
    HttpCall httpCall = mock(HttpCall.class);

    Field httpCallField = amplitude.getClass().getDeclaredField("httpCall");
    Field httpTransportField = amplitude.getClass().getDeclaredField("httpTransport");
    httpCallField.setAccessible(true);
    httpCallField.set(amplitude, httpCall);
    httpTransportField.setAccessible(true);
    HttpTransport httpTransport = (HttpTransport) httpTransportField.get(amplitude);
    httpTransport.setHttpCall(httpCall);
    return httpCall;
  }

  private AmplitudeCallbacks getCallbacks(Amplitude amplitude)
      throws NoSuchFieldException, IllegalAccessException {
    Field httpTransportField = amplitude.getClass().getDeclaredField("httpTransport");
    httpTransportField.setAccessible(true);
    HttpTransport httpTransport = (HttpTransport) httpTransportField.get(amplitude);
    Field callbackField = httpTransport.getClass().getDeclaredField("callbacks");
    callbackField.setAccessible(true);
    return (AmplitudeCallbacks) callbackField.get(httpTransport);
  }
}
