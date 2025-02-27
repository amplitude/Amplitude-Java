package com.amplitude;

import java.net.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

public class Amplitude {
  private static Map<String, Amplitude> instances = new HashMap<>();
  private final String instanceName;
  private String apiKey;
  private String serverUrl;

  private AmplitudeLog logger;

  private Queue<Event> eventsToSend;
  private boolean aboutToStartFlushing;

  private HttpCallMode httpCallMode;
  private HttpCall httpCall;
  private HttpTransport httpTransport;
  private int eventUploadThreshold = Constants.EVENT_BUF_COUNT;
  private int eventUploadPeriodMillis = Constants.EVENT_BUF_TIME_MILLIS;
  private Object eventQueueLock = new Object();
  private Plan plan;
  private IngestionMetadata ingestionMetadata;
  private long flushTimeout;

  /**
   * A dictionary of key-value pairs that represent additional instructions for server save
   * operation.
   */
  private Options options;

  /** Custom proxy for https requests */
  private Proxy proxy = Proxy.NO_PROXY;

  /** The runner for middleware */
  MiddlewareRunner middlewareRunner = new MiddlewareRunner();

  /**
   * Private internal constructor for Amplitude. Please use `getInstance(String name)` or
   * `getInstance()` to get a new instance.
   */
  private Amplitude(String name) {
    instanceName = name;
    logger = new AmplitudeLog();
    eventsToSend = new ConcurrentLinkedQueue<>();
    aboutToStartFlushing = false;
    httpTransport = new HttpTransport(httpCall, null, logger, flushTimeout);
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
      Amplitude ampInstance = new Amplitude(instanceName);
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
   * Sets event upload threshold. The SDK will attempt to batch upload unsent events every
   * eventUploadPeriodMillis milliseconds, or if the unsent event count exceeds the event upload
   * threshold.
   *
   * @param eventUploadThreshold the event upload threshold
   */
  public Amplitude setEventUploadThreshold(int eventUploadThreshold) {
    this.eventUploadThreshold = eventUploadThreshold;
    return this;
  }

  /**
   * Sets event upload period millis. The SDK will attempt to batch upload unsent events * every
   * eventUploadPeriodMillis milliseconds, or if the unsent event count exceeds the * event upload
   * threshold.
   *
   * @param eventUploadPeriodMillis the event upload period millis
   */
  public Amplitude setEventUploadPeriodMillis(int eventUploadPeriodMillis) {
    this.eventUploadPeriodMillis = eventUploadPeriodMillis;
    return this;
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
   * Set tracking plan information.
   *
   * @param plan Plan object
   */
  public Amplitude setPlan(Plan plan) {
    this.plan = plan;
    return this;
  }

  /**
   * Set ingestion metadata information.
   *
   * @param ingestionMetadata IngestionMetadata object
   */
  public Amplitude setIngestionMetadata(IngestionMetadata ingestionMetadata) {
    this.ingestionMetadata = ingestionMetadata;
    return this;
  }

  /**
   * Set custom proxy for https requests
   *
   * @param proxy Proxy object, default to Proxy.NO_PROXY for direct connection
   */
  public Amplitude setProxy(Proxy proxy) {
    this.proxy = proxy;
    updateHttpCall(httpCallMode);
    return this;
  }

  /**
   * Set custom logger instance for amplitude client
   *
   * @param logger AmplitudeLog object
   */
  public Amplitude setLogger(AmplitudeLog logger) {
    this.logger = logger;
    this.httpTransport.setLogger(logger);
    return this;
  }

  /**
   * Set flush events threads execution timeout in milliseconds
   *
   * @param timeout the flush events threads timeout millis
   */
  public Amplitude setFlushTimeout(long timeout) {
    flushTimeout = timeout;
    this.httpTransport.setFlushTimeout(timeout);
    return this;
  }

  /**
   * Set the thread pool for sending events via {@link HttpTransport}
   *
   * @param sendThreadPool the thread pool for sending events
   */
  public Amplitude setSendThreadPool(ExecutorService sendThreadPool) {
    this.httpTransport.setSendThreadPool(sendThreadPool);
    return this;
  }

  /**
   * Set the thread pool for retrying events via {@link HttpTransport}
   *
   * @param retryThreadPool the thread pool for retrying events
   */
  public Amplitude setRetryThreadPool(ExecutorService retryThreadPool) {
    this.httpTransport.setRetryThreadPool(retryThreadPool);
    return this;
  }

  /** Add middleware to the middleware runner */
  public synchronized void addEventMiddleware(Middleware middleware) {
    middlewareRunner.add(middleware);
  }

  /**
   * Log an event to the Amplitude HTTP V2 API through the Java SDK
   *
   * @param event The event to be sent
   */
  public void logEvent(Event event) {
    logEvent(event, null, null);
  }

  /**
   * Log an event to the Amplitude HTTP V2 API through the Java SDK
   *
   * @param event The event to be sent
   * @param extra The extra unstructured data for middleware
   */
  public void logEvent(Event event, MiddlewareExtra extra) {
    logEvent(event, null, extra);
  }

  /**
   * Log an event and set a callback for this event.
   *
   * @param event The event to be sent
   * @param callbacks The callback for the event, this will run in addition to client level callback
   */
  public void logEvent(Event event, AmplitudeCallbacks callbacks) {
    logEvent(event, callbacks, null);
  }

  /**
   * Log an event and set a callback for this event.
   *
   * @param event The event to be sent
   * @param callbacks The callback for the event, this will run in addition to client level callback
   * @param extra The extra unstructured data for middleware
   */
  public void logEvent(Event event, AmplitudeCallbacks callbacks, MiddlewareExtra extra) {
    if (event.plan == null) {
      event.plan = this.plan;
    }

    if (event.ingestionMetadata == null) {
      event.ingestionMetadata = this.ingestionMetadata;
    }

    if (!middlewareRunner.run(new MiddlewarePayload(event, extra))) {
      return;
    }

    if (callbacks != null) {
      event.callback = callbacks;
    }
    int queueSize = 0;
    synchronized (eventQueueLock) {
      eventsToSend.add(event);
      queueSize = eventsToSend.size();
    }
    if (queueSize >= this.eventUploadThreshold) {
      flushEvents();
    } else {
      scheduleFlushEvents();
    }
  }

  /**
   * Forces events currently in the event buffer to be sent to Amplitude API endpoint. Only one
   * thread may flush at a time. Next flushes will happen immediately after.
   */
  public void flushEvents() {
    List<Event> eventsInTransit = new ArrayList<>();
    synchronized (eventQueueLock) {
      if (eventsToSend.size() > 0) {
        eventsInTransit.addAll(eventsToSend);
        eventsToSend.clear();
      }
    }
    if (!eventsInTransit.isEmpty()) {
      httpTransport.sendEventsWithRetry(eventsInTransit);
    }
  }

  /**
   * Check the status of the client, return true if any of the following situation:
   * 1. Events sit in memory waiting for flush are more than flush threshold
   * 2. Events in retry buffer are more than 16,000
   * 3. When setRecordThrottledId(true), the userId or deviceId of the input event was throttled and retry attempt for the userId/deviceId not finished.
   * Can be called before logEvent(event) and add some wait time if return true.
   *
   * @param event The event to be sent.
   * @return true if client is busy or event may be throttled.
   */
  public boolean shouldWait(Event event) {
    return eventsToSend.size() > eventUploadThreshold || httpTransport.shouldWait(event);
  }

  /**
   * Config whether the client record the recent throttled userId/deviceId and make suggestion base
   * on it.
   *
   * @param record true to record
   */
  public void setRecordThrottledId(boolean record) {
    httpTransport.setRecordThrottledId(record);
  }

  /**
   * Release resource by:
   * Shutdown threadspool for executing flush events task.
   * All events hold by these threads will trigger callbacks if amplitude client have configured callbacks method.
   * Shutdown client instance stop sending further events.
   */
  public void shutdown() throws InterruptedException {
    httpTransport.shutdown();
    instances.remove(instanceName);
  }

  private void updateHttpCall(HttpCallMode updatedHttpCallMode) {
    httpCallMode = updatedHttpCallMode;

    if (updatedHttpCallMode == HttpCallMode.BATCH) {
      httpCall =
          new HttpCall(
              apiKey, serverUrl != null ? serverUrl : Constants.BATCH_API_URL, options, proxy);
    } else {
      httpCall =
          new HttpCall(apiKey, serverUrl != null ? serverUrl : Constants.API_URL, options, proxy);
    }
    httpTransport.setHttpCall(httpCall);
  }

  private synchronized void scheduleFlushEvents() {
    if (!aboutToStartFlushing) {
      aboutToStartFlushing = true;
      Thread flushThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(this.eventUploadPeriodMillis);
                } catch (InterruptedException e) {
                  logger.warn("Error schedule flush events.", e.getMessage());
                }
                flushEvents();
                aboutToStartFlushing = false;
              });
      flushThread.start();
    }
  }
}
