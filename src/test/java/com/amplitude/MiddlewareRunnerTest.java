package com.amplitude;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class MiddlewareRunnerTest {
  MiddlewareRunner middlewareRunner = new MiddlewareRunner();

  @Test
  public void testMiddlewareRun() {
    String middlewareDevice = "middleware_device";
    Middleware updateDeviceIdMiddleware = (payload, next) -> {
      payload.event.deviceModel = middlewareDevice;
      next.run(payload);
    };
    middlewareRunner.add(updateDeviceIdMiddleware);

    Event event = new Event("sample_event", "sample_user");
    event.deviceModel = "sample_device";
    boolean middlewareCompleted = middlewareRunner.run(new MiddlewarePayload(event, new MiddlewareExtra()));

    assertTrue(middlewareCompleted);
    assertEquals(event.deviceModel, middlewareDevice);
  }

  @Test
  public void testRunWithNotPassMiddleware() {
    // first middleware
    String middlewareDevice = "middleware_device";
    Middleware updateDeviceIdMiddleware = (payload, next) -> {
      payload.event.deviceModel = middlewareDevice;
      next.run(payload);
    };

    // swallow middleware
    String middlewareUser = "middleware_user";
    Middleware swallowMiddleware = (payload, next) -> payload.event.userId = middlewareUser;
    middlewareRunner.add(updateDeviceIdMiddleware);
    middlewareRunner.add(swallowMiddleware);

    Event event = new Event("sample_event", "sample_user");
    event.deviceModel = "sample_device";
    boolean middlewareCompleted = middlewareRunner.run(new MiddlewarePayload(event));

    assertFalse(middlewareCompleted);
    assertEquals(event.deviceModel, middlewareDevice);
    assertEquals(event.userId, middlewareUser);
  }
}
