package com.amplitude;

public class AmplitudeLog {
  private LogMode logMode = LogMode.ERROR;

  public void setLogMode(LogMode logMode) {
    this.logMode = logMode;
  }

  public LogMode getLogMode() {
    return this.logMode;
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

    public int getLogLevel() {
      return this.level;
    }
  }
}
