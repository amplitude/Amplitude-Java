package com.amplitude.exception;

public class AmplitudeInvalidAPIKeyException extends Exception {

    private final int code;

    public AmplitudeInvalidAPIKeyException(int code) {
        super();
        this.code = code;
    }

    public AmplitudeInvalidAPIKeyException(String message, Throwable cause, int code) {
        super(message, cause);
        this.code = code;
    }

    public AmplitudeInvalidAPIKeyException(String message, int code) {
        super(message);
        this.code = code;
    }

    public AmplitudeInvalidAPIKeyException(Throwable cause, int code) {
        super(cause);
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }
}
