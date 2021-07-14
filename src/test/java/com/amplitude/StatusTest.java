package com.amplitude;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusTest {

  @ParameterizedTest
  @CsvSource({
    "200, SUCCESS",
    "400, INVALID",
    "429, RATELIMIT",
    "413, PAYLOAD_TOO_LARGE",
    "408, TIMEOUT",
    "500, FAILED",
    "401, INVALID",
    "404, INVALID",
    "409, INVALID",
    "300, UNKNOWN",
    "503, FAILED"
  })
  public void getCodeStatus(int code, Status status) {
    assertEquals(status, Status.getCodeStatus(code));
  }
}
