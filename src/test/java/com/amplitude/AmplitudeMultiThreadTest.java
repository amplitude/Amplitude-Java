package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;
import com.amplitude.util.EventsGenerator;

@ExtendWith(MockitoExtension.class)
public class AmplitudeMultiThreadTest {
  private final String apiKey = "test-apiKey";
  private AtomicInteger totalEvents;
  private AtomicInteger successEvents;
  private AtomicInteger failedEvents;
  private AtomicInteger pendingEvents;

  @BeforeEach
  public void setUp() {
    totalEvents = new AtomicInteger();
    successEvents = new AtomicInteger();
    failedEvents = new AtomicInteger();
    pendingEvents = new AtomicInteger();
  }

  @Test
  public void testMultipleThreadLogAndSendEventSuccess()
      throws NoSuchFieldException, IllegalAccessException, AmplitudeInvalidAPIKeyException {
    Amplitude amplitude = Amplitude.getInstance("Multiple threads log event success.");
    amplitude.init(apiKey);
    HttpCall httpCall = getMockHttpCall(amplitude);
    Response response = ResponseUtil.getSuccessResponse();
    when(httpCall.makeRequest(anyList())).thenAnswer(invocation -> response);
    int total = 1000;
    CountDownCallbacks callbacks = new CountDownCallbacks(new CountDownLatch(total));
    amplitude.setCallbacks(callbacks);
    int threads = 10;
    CountDownLatch threadLatch = new CountDownLatch(threads);
    ExecutorService executorService = Executors.newFixedThreadPool(threads);

    for (int i = 0; i < threads; i++) {
      executorService.execute(new LogEventRunnable(amplitude, total / threads, threadLatch));
    }

    try {
      threadLatch.await();
      callbacks.blockWithTimeout(15);
      assertEquals(total, totalEvents.get(), "Total events not matching.");
      assertEquals(0, pendingEvents.get(), "Pending events should be 0.");
      assertEquals(total, successEvents.get(), "Success events count should be total");
      assertEquals(0, failedEvents.get(), "Failed event should be 0.");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testMultipleThreadLogAndSendEventFailure()
      throws NoSuchFieldException, IllegalAccessException, AmplitudeInvalidAPIKeyException {
    Amplitude amplitude = Amplitude.getInstance("Multiple threads log event failure.");
    amplitude.init(apiKey);
    HttpCall httpCall = getMockHttpCall(amplitude);
    Response payloadTooLargeResponse = ResponseUtil.getRateLimitResponse(false);
    when(httpCall.makeRequest(anyList())).thenAnswer(invocation -> payloadTooLargeResponse);
    int total = 1000;
    CountDownCallbacks callbacks = new CountDownCallbacks(new CountDownLatch(total));
    amplitude.setCallbacks(callbacks);
    int threads = 10;
    CountDownLatch threadLatch = new CountDownLatch(threads);
    ExecutorService executorService = Executors.newFixedThreadPool(threads);

    for (int i = 0; i < threads; i++) {
      executorService.execute(new LogEventRunnable(amplitude, total / threads, threadLatch));
    }

    try {
      threadLatch.await();
      callbacks.blockWithTimeout(15);
      assertEquals(total, totalEvents.get(), "Total events not matching.");
      assertEquals(0, pendingEvents.get(), "Pending events should be 0.");
      assertEquals(0, successEvents.get(), "Success events count should be 0.");
      assertEquals(total, failedEvents.get(), "Failed event should be total.");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testMultipleThreadLogAndSendEventWithSuccessAndFailure()
      throws NoSuchFieldException, IllegalAccessException, AmplitudeInvalidAPIKeyException {
    Amplitude amplitude =
        Amplitude.getInstance("Multiple threads log event with success and failure.");
    amplitude.init(apiKey);
    HttpCall httpCall = getMockHttpCall(amplitude);
    Response successResponse = ResponseUtil.getSuccessResponse();
    Response payloadTooLargeResponse = ResponseUtil.getPayloadTooLargeResponse();
    Response invalidResponse = ResponseUtil.getInvalidResponse(false);
    Response rateLimitResponse = ResponseUtil.getRateLimitResponse(false);
    List<Response> responseList =
        Arrays.asList(successResponse, payloadTooLargeResponse, invalidResponse, rateLimitResponse);
    Random rand = new Random();
    when(httpCall.makeRequest(anyList()))
        .thenAnswer(invocation -> responseList.get(rand.nextInt(responseList.size())));
    int total = 1000;
    CountDownCallbacks callbacks = new CountDownCallbacks(new CountDownLatch(total));
    amplitude.setCallbacks(callbacks);
    int threads = 10;
    CountDownLatch threadLatch = new CountDownLatch(threads);
    ExecutorService executorService = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      executorService.execute(new LogEventRunnable(amplitude, total / threads, threadLatch));
    }

    try {
      threadLatch.await();
      callbacks.blockWithTimeout(15);
      assertEquals(total, totalEvents.get(), "Total events not matching.");
      assertEquals(0, pendingEvents.get(), "Pending events should be 0.");
      assertEquals(
          total,
          failedEvents.get() + successEvents.get(),
          "Success plus failed events should be total.");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  class LogEventRunnable implements Runnable {

    private Amplitude sender;
    private int count;
    private CountDownLatch threadFinishedLatch;

    LogEventRunnable(Amplitude sender, int count, CountDownLatch latch) {
      this.sender = sender;
      this.count = count;
      this.threadFinishedLatch = latch;
    }

    @Override
    public void run() {
      List<Event> eventList = EventsGenerator.generateEvents(count);
      for (Event e : eventList) {
        totalEvents.incrementAndGet();
        pendingEvents.incrementAndGet();
        sender.logEvent(e);
      }
      sender.flushEvents();
      threadFinishedLatch.countDown();
    }
  }

  class CountDownCallbacks extends AmplitudeCallbacks {

    final CountDownLatch latch;

    CountDownCallbacks(CountDownLatch latch) {
      this.latch = latch;
    }

    public void onLogEventServerResponse(Event event, int status, String message) {
      boolean success = status >= 200 && status <= 300;
      if (success) {
        successEvents.incrementAndGet();
      } else {
        failedEvents.incrementAndGet();
      }
      pendingEvents.decrementAndGet();
      latch.countDown();
    }

    public void blockWithTimeout(int timeoutInSecond) throws InterruptedException {
      int timeout = Math.max(timeoutInSecond, 15);
      this.latch.await(timeout, TimeUnit.SECONDS);
    }
  }

  private HttpCall getMockHttpCall(Amplitude amplitude)
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
}
