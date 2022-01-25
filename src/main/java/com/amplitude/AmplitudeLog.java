package com.amplitude;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AmplitudeLog {
  private LogMode logMode = LogMode.ERROR;
  private static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

  public void setLogMode(LogMode logMode) {
    this.logMode = logMode;
  }

  public void debug(String tag, String message) {
    log(tag, message, LogMode.DEBUG);
  }

  public void warn(String tag, String message) {
    log(tag, message, LogMode.WARN);
  }

  public void error(String tag, String message) {
    log(tag, message, LogMode.ERROR);
  }

  public void debug(String tag, List<Event> events, Response response) {
    if (logMode == LogMode.DEBUG) {
      String debugMessage = sdfDate.format(new Date()) + " " + tag + ": ";
      debugMessage += "Events count " + events.size() + ".\n" + response.toString();
      System.out.println(debugMessage);
    }
  }

  public void log(String tag, String message, LogMode messageMode) {
    if (messageMode.level >= logMode.level) {
      if (messageMode.level >= LogMode.ERROR.level) {
        System.err.println(tag + ": " + message);
      } else {
        System.out.println(tag + ": " + message);
      }
    }
  }

  public enum LogMode {
    DEBUG(1),
    WARN(2),
    ERROR(3),
    OFF(4);

    private int level;

    LogMode(int level) {
      this.level = level;
    }
  }
}
