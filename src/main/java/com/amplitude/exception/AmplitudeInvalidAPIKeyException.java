package com.amplitude.exception;

public class AmplitudeInvalidAPIKeyException extends AmplitudeException {
    public AmplitudeInvalidAPIKeyException() {
        super("Invalid API key");
    }
}
