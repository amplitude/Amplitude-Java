package com.amplitude;

public interface Constants {

    String  API_URL                 = "https://api2.amplitude.com/2/httpapi";
    long    NETWORK_TIMEOUT_MILLIS  = 10000;
    String  SDK_LIBRARY             = "amplitude-java";
    String  SDK_VERSION             = "1.1.0";

    int     MAX_PROPERTY_KEYS       = 1024;
    int     MAX_STRING_LENGTH       = 1000;

    int     HTTP_STATUS_CONTINUE    = 100;
    int     HTTP_STATUS_BAD_REQ     = 400;

}
