package com.amplitude;

/**
 * AmplitudeEventCallback Interface
 * Triggered when event is sent or failed
 */
public interface AmplitudeEventCallback {
    void onEventSent(Event event, int status, String message);
}
