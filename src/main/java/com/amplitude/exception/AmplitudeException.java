package com.amplitude.exception;

public abstract class AmplitudeException extends Exception {
  public AmplitudeException(String message) {
    super("Amplitude Exception: " + message);
  }
}
