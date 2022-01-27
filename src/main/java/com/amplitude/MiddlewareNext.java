package com.amplitude;

public interface MiddlewareNext {
  void run(MiddlewarePayload curPayload);
}
