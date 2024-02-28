package com.amplitude;

public interface Constants {
  String API_URL = "https://api2.amplitude.com/2/httpapi";
  String BATCH_API_URL = "https://api2.amplitude.com/batch";

  int NETWORK_TIMEOUT_MILLIS = 10000;
  String SDK_LIBRARY = "amplitude-java";
  String SDK_VERSION = "1.12.2";

  int MAX_PROPERTY_KEYS = 1024;
  int MAX_STRING_LENGTH = 1000;

  int HTTP_STATUS_BAD_REQ = 400;

  int EVENT_BUF_COUNT = 10;
  int EVENT_BUF_TIME_MILLIS = 10000;

  long[] RETRY_TIMEOUTS = { 100, 100, 200, 200, 400, 400, 800, 800, 1600, 1600, 3200, 3200 };
  int MAX_CACHED_EVENTS = 16000;

  String AMP_PLAN_BRANCH = "branch";
  String AMP_PLAN_SOURCE = "source";
  String AMP_PLAN_VERSION = "version";
  String AMP_PLAN_VERSION_ID = "versionId";

  String AMP_INGESTION_METADATA_SOURCE_NAME = "source_name";
  String AMP_INGESTION_METADATA_SOURCE_VERSION = "source_version";
}
