package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import com.amplitude.util.EventsGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(
            invocation -> {
              latch.countDown();
              return response;
            });
    List<AmplitudeEventCallback> eventCallbacks = new ArrayList<>();
    eventCallbacks.add(
        new AmplitudeEventCallback() {
          @Override
          public void onEventSent(Event event, int status, String message) {
            assertEquals(200, status);
          }
        });
    for (Event event : events) {
      amplitude.logEvent(event);
    }
    assertTrue(latch.await(1L, TimeUnit.SECONDS));
    verify(httpCall, times(1)).makeRequest(anyList());
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
  public void testSetEventCallback() throws NoSuchFieldException, IllegalAccessException {
    Amplitude amplitude = Amplitude.getInstance("testSetEventCallback");
    AmplitudeEventCallback eventCallbacks = new AmplitudeEventCallback() {
      @Override public void onEventSent(Event event, int status, String message) {

      }
    };
    amplitude.setEventCallback(eventCallbacks);
    assertEquals(eventCallbacks, getEventCallback(amplitude));
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

  private AmplitudeEventCallback getEventCallback(Amplitude amplitude)
      throws NoSuchFieldException, IllegalAccessException {
    Field httpTransportField = amplitude.getClass().getDeclaredField("httpTransport");
    httpTransportField.setAccessible(true);
    HttpTransport httpTransport = (HttpTransport) httpTransportField.get(amplitude);
    Field eventCallbackField = httpTransport.getClass().getDeclaredField("eventCallback");
    eventCallbackField.setAccessible(true);
    return (AmplitudeEventCallback) eventCallbackField.get(httpTransport);
  }
}
