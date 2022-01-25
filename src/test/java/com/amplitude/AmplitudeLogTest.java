package com.amplitude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.amplitude.util.EventsGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AmplitudeLogTest {
  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private final AmplitudeLog amplitudeLog = new AmplitudeLog();

  @BeforeEach
  public void setUpStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  public void restoreStreams() throws IOException {
    outContent.close();
    errContent.close();
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @ParameterizedTest
  @MethodSource("logArguments")
  public void testLog(
      AmplitudeLog.LogMode logMode,
      String expectedErrorLog,
      String expectedWarnLog,
      String expectedDebugLog) {
    amplitudeLog.setLogMode(logMode);
    amplitudeLog.error("Test", "error message");
    assertEquals(expectedErrorLog, errContent.toString().trim());
    amplitudeLog.warn("Test", "warn message");
    assertEquals(expectedWarnLog, outContent.toString().trim());
    amplitudeLog.debug("Test", "debug message");
    assertEquals(expectedDebugLog, outContent.toString().trim());
  }

  @Test
  public void testDebugWithEventsResponse() {
    amplitudeLog.setLogMode(AmplitudeLog.LogMode.DEBUG);
    List<Event> events = EventsGenerator.generateEvents(10);
    Response response = ResponseUtil.getSuccessResponse();
    amplitudeLog.debug("TEST", events, response);
    assertEquals("TEST: Events count 10.\n{\n    \"code\": 200,\n    \"status\": \"SUCCESS\"\n}", outContent.toString().substring(23).trim());
  }

  static Stream<Arguments> logArguments() {
    return Stream.of(
        arguments(AmplitudeLog.LogMode.ERROR, "Test: error message", "", ""),
        arguments(
            AmplitudeLog.LogMode.WARN,
            "Test: error message",
            "Test: warn message",
            "Test: warn message"),
        arguments(
            AmplitudeLog.LogMode.DEBUG,
            "Test: error message",
            "Test: warn message",
            "Test: warn message\nTest: debug message"),
        arguments(AmplitudeLog.LogMode.OFF, "", "", ""));
  }
}
