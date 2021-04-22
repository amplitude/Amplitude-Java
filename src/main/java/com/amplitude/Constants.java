package com.amplitude;

public interface Constants {
    
    String  API_URL                 = "https://api2.amplitude.com/2/httpapi";
    long    NETWORK_TIMEOUT_MILLIS  = 10000;
    String  SDK_LIBRARY             = "amplitude-java";
    String  SDK_VERSION             = "1.0.0";

    int     MAX_PROPERTY_KEYS       = 1024;
    int     MAX_STRING_LENGTH       = 1000;

    int     GOOD_RES_CODE_START     = 100;
    int     GOOD_RES_CODE_END       = 399;
    int     BAD_RES_CODE_START      = 400;

}
