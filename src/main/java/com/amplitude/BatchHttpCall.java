package com.amplitude;

import java.util.List;

public class BatchHttpCall extends HttpCall {

    public BatchHttpCall(List<Event> events, String apiKey, String apiUrl) {
        super(events, apiKey, apiUrl);
    }
}
