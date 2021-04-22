package com.amplitude;

public class AmplitudeLog {

    private static LogMode LOG_MODE = LogMode.DEBUG;

    public static void setLogMode(LogMode logMode) {
        AmplitudeLog.LOG_MODE = logMode;
    }

    public static void log(String tag, String message) {
        log(tag, message, LogMode.DEBUG);
    }

    public static void log(String tag, String message, LogMode messageMode) {
        if (messageMode.index >= LOG_MODE.index) {
            if (messageMode.index >= LogMode.ERROR.index) {
                System.err.println(tag + ": " + message);
            }
            else {
                System.out.println(tag + ": " + message);
            }
        }
    }

    public enum LogMode {
        DEBUG(1),
        WARN(2),
        ERROR(3),
        OFF(4);

        private int index;
        LogMode(int index) {
            this.index = index;
        }
    }

}
