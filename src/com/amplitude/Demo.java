package com.amplitude;

import java.util.Map;

public class Demo {

    public static void main(String[] args) {
        Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
        amplitude.init("Example Key");
        amplitude.logEvent("Event name test");
    }

}
