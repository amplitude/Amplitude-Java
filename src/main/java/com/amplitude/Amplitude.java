package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;

import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.*;

public class Amplitude {

  public static final String TAG = Amplitude.class.getName();

  private static Map<String, Amplitude> instances = new HashMap<>();
  private String apiKey;

  private AmplitudeLog logger;

  private Queue<Event> eventsToSend;
  private boolean aboutToStartFlushing;

  private HttpCallMode httpCallMode;
  private HttpCall httpCall;

  /**
   * Private internal constructor for Amplitude. Please use `getInstance(String name)` or
   * `getInstance()` to get a new instance.
   */
  private Amplitude() {
    logger = new AmplitudeLog();
    eventsToSend = new ConcurrentLinkedQueue<>();
    aboutToStartFlushing = false;
  }

  /**
   * Return the default class instance of Amplitude that is associated with "" or no string (null).
   *
   * @return the Amplitude instance that should be used for instrumentation
   */
  public static Amplitude getInstance() {
    return getInstance("");
  }

  /**
   * Return the class instance of Amplitude that is associated with this name
   *
   * @param instanceName The key (unique identifier) that matches to the Amplitude instance
   * @return the Amplitude instance that should be used for instrumentation
   */
  public static Amplitude getInstance(String instanceName) {
    if (!instances.containsKey(instanceName)) {
      Amplitude ampInstance = new Amplitude();
      instances.put(instanceName, ampInstance);
    }
    return instances.get(instanceName);
  }

  /**
   * Set the API key for this instance of Amplitude. API key is necessary to authorize and route
   * events to the current Amplitude project.
   *
   * @param key the API key from Amplitude website
   */
  public void init(String key) {
    apiKey = key;
    updateHttpCall(HttpCallMode.REGULAR_HTTPCALL);
  }

  /**
   * Set the Event Upload Mode. If isBatchMode is true, the events will log through the Amplitude
   * HTTP V2 Batch API.
   *
   * @param isBatchMode if using batch upload or not;
   */
  public void useBatchMode(Boolean isBatchMode) {
    updateHttpCall(isBatchMode ? HttpCallMode.BATCH_HTTPCALL : HttpCallMode.REGULAR_HTTPCALL);
  }

  private void updateHttpCall(HttpCallMode updatedHttpCallMode) {
    if (httpCallMode == null || httpCallMode != updatedHttpCallMode) {
      httpCallMode = updatedHttpCallMode;
      httpCall =
          (updatedHttpCallMode == HttpCallMode.BATCH_HTTPCALL)
              ? new BatchHttpCall(apiKey)
              : new GeneralHttpCall(apiKey);
    }
  }

  /**
   * Set the level at which to filter out debug messages from the Java SDK.
   *
   * @param logMode Messages at this level and higher (more urgent) will be logged in the console.
   */
  public void setLogMode(AmplitudeLog.LogMode logMode) {
    this.logger.setLogMode(logMode);
  }

  /**
   * Log an event to the Amplitude HTTP V2 API through the Java SDK
   *
   * @param event The event to be sent
   */
  public void logEvent(Event event) {
    eventsToSend.add(event);
    if (eventsToSend.size() >= Constants.EVENT_BUF_COUNT) {
      flushEvents();
    } else {
      tryToFlushEventsIfNotFlushing();
    }
  }

  private void tryToFlushEventsIfNotFlushing() {
    if (!aboutToStartFlushing) {
      aboutToStartFlushing = true;
      Thread flushThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(Constants.EVENT_BUF_TIME_MILLIS);
                } catch (InterruptedException e) {

                }
                flushEvents();
                aboutToStartFlushing = false;
              });
      flushThread.start();
    }
  }

  /**
   * Forces events currently in the event buffer to be sent to Amplitude API endpoint. Only one
   * thread may flush at a time. Next flushes will happen immediately after.
   */
  public synchronized void flushEvents() {
    if (eventsToSend.size() > 0) {
      List<Event> eventsInTransit = new ArrayList<>(eventsToSend);
      eventsToSend.clear();
      CompletableFuture<Response> asyncHandler =
          CompletableFuture.supplyAsync(
              () -> {
                Response response = null;
                try {
                  response = httpCall.syncHttpCallWithEventsBuffer(eventsInTransit);
                } catch (AmplitudeInvalidAPIKeyException e) {
                  e.printStackTrace();
                }
                return response;
              });

      try {
        Response response = asyncHandler.join();
        Status status = response.status;
        if (Retry.shouldRetryForStatus(status)) {
          Retry.sendEventsWithRetry(eventsInTransit, response, httpCall);
        }
      } catch (CompletionException e) {
        e.printStackTrace();
      }
    }
  }
}
