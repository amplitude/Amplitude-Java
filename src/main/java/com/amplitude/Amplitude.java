package com.amplitude;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
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

    private Amplitude() {
        logger = new AmplitudeLog();
        eventsToSend = new ConcurrentLinkedQueue<>();
        aboutToStartFlushing = false;
    }

    public static Amplitude getInstance() {
        return getInstance("");
    }

    public static Amplitude getInstance(String instanceName) {
        if (!instances.containsKey(instanceName)) {
            Amplitude ampInstance = new Amplitude();
            instances.put(instanceName, ampInstance);
        }
        return instances.get(instanceName);
    }

    public void init(String key) {
        apiKey = key;
    }

    public void setLogMode(AmplitudeLog.LogMode logMode) {
        this.logger.setLogMode(logMode);
    }

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
                    new Thread(() -> {
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

    public synchronized void flushEvents() {
        if (eventsToSend.size() > 0) {
            List<Event> eventsInTransit = new ArrayList<>(eventsToSend);
            eventsToSend.clear();
            CompletableFuture.supplyAsync(() -> {
                Response response = HttpCall.syncHttpCallWithEventsBuffer(eventsInTransit, apiKey);
                int responseCode = response.code;
                //System.out.println(responseCode);
                if (responseCode >= Constants.HTTP_STATUS_MIN_RETRY && responseCode <= Constants.HTTP_STATUS_MAX_RETRY) {
                    eventsToSend.addAll(eventsInTransit);
                    tryToFlushEventsIfNotFlushing();
                }
                return null;
            });
        }
    }


}
