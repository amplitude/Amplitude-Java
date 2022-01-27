package com.amplitude;

public interface Middleware {
  void run(MiddlewarePayload payload, MiddlewareNext next);
}