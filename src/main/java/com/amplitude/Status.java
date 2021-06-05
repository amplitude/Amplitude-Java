package com.amplitude;

public enum Status {
    /**
     * The status could not be determined.
     */
    UNKNOWN,
    /**
     * The event was skipped due to configuration or callbacks.
     */
    SKIPPED,
    /**
     * The event was sent successfully.
     */
    SUCCESS,
    /**
     * A user or device in the payload is currently rate limited and should try again later.
     */
    RATELIMIT,
    /**
     * The sent payload was too large to be processed.
     */
    PAYLOAD_TOO_LARGE,
    /**
     * The event could not be processed.
     */
    INVALID,
    /**
     * A server-side error ocurred during submission.
     */
    FAILED,
    /**
     * a server or client side error occuring when a request takes too long and is cancelled
     */
    TIMEOUT,
    /**
     * environment error.. E.g. disconnected from network
     */
    SYSTEM_ERROR;

    protected static Status getCodeStatus(int code) {
        if (code >= 200 && code < 300) {
            return Status.SUCCESS;
        }
        if (code == 429) {
            return Status.RATELIMIT;
        }
        if (code == 413) {
            return Status.PAYLOAD_TOO_LARGE;
        }
        if (code == 408) {
            return Status.TIMEOUT;
        }
        if (code >= 400 && code < 500) {
            return Status.INVALID;
        }
        if (code >= 500) {
            return Status.FAILED;
        }
        return Status.UNKNOWN;
    }
}