package com.amplitude;

import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Amplitude {
  private static Map<String, Amplitude> instances = new HashMap<>();
  private String apiKey;
  private String serverUrl;

  private AmplitudeLog logger;

  private Queue<Event> eventsToSend;
  private boolean aboutToStartFlushing;

  private HttpCallMode httpCallMode;
  private HttpCall httpCall;
  private HttpTransport httpTransport;

  /**
   * A dictionary of key-value pairs that represent additional instructions for server save operation.
   */
  private Options options;

  /**
   * Private internal constructor for Amplitude. Please use `getInstance(String name)` or
   * `getInstance()` to get a new instance.
   */
  private Amplitude() {
    logger = new AmplitudeLog();
    eventsToSend = new ConcurrentLinkedQueue<>();
    aboutToStartFlushing = false;
    httpTransport = new HttpTransport(httpCall, null, logger);
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
    updateHttpCall(HttpCallMode.REGULAR);
  }

  /**
   * Sends events to a different URL other than Constants.API_URL or Constants.BATCH_API_URL. Used
   * for proxy server.
   *
   * @param url the server URL for sending event
   */
  public void setServerUrl(String url) {
    boolean isValidServerUrl = url.startsWith("http://") || url.startsWith("https://");
    if (!isValidServerUrl) return;
    serverUrl = url;
    updateHttpCall(httpCallMode);
  }

  public void setOptions(Options options) {
    this.options = options;
    updateHttpCall(httpCallMode);
  }

  /**
   * Set the Event Upload Mode. If isBatchMode is true, the events will log through the Amplitude
   * HTTP V2 Batch API.
   *
   * @param isBatchMode if using batch upload or not;
   */
  public void useBatchMode(Boolean isBatchMode) {
    updateHttpCall(isBatchMode ? HttpCallMode.BATCH : HttpCallMode.REGULAR);
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
   * Set event callback which are triggered after event sent
   *
   * @param callbacks AmplitudeCallbacks or null to clean up.
   */
  public void setCallbacks(AmplitudeCallbacks callbacks) {
    httpTransport.setCallbacks(callbacks);
  }

  /**
   * Log an event to the Amplitude HTTP V2 API through the Java SDK
   *
   * @param event The event to be sent
   */
  public synchronized void logEvent(Event event) {
    eventsToSend.add(event);
    if (eventsToSend.size() >= Constants.EVENT_BUF_COUNT) {
      flushEvents();
    } else {
      scheduleFlushEvents();
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
      httpTransport.sendEventsWithRetry(eventsInTransit);
    }
  }

  private void updateHttpCall(HttpCallMode updatedHttpCallMode) {
    httpCallMode = updatedHttpCallMode;

    final JSONObject optionsJson = options == null ? null : options.toJsonObject();
    if (updatedHttpCallMode == HttpCallMode.BATCH) {
      httpCall = new HttpCall(apiKey, serverUrl != null ? serverUrl : Constants.BATCH_API_URL, optionsJson);
    } else {
      httpCall = new HttpCall(apiKey, serverUrl != null ? serverUrl : Constants.API_URL, optionsJson);
    }
    httpTransport.setHttpCall(httpCall);
  }

  private void scheduleFlushEvents() {
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
}
