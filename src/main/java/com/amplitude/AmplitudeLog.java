package com.amplitude;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AmplitudeLog {
  private LogMode logMode = LogMode.ERROR;
  private static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

  public static String getTimeStamp(){
    return sdfDate.format(new Date());
  }

  public void setLogMode(LogMode logMode) {
    this.logMode = logMode;
  }

  public LogMode getLogMode() {
    return logMode;
  }

  public void log(String tag, String message) {
    log(tag, message, LogMode.DEBUG);
  }

  public void warn(String tag, String message) {
    log(tag, message, LogMode.WARN);
  }

  public void error(String tag, String message) {
    log(tag, message, LogMode.ERROR);
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
