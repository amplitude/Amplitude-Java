package com.amplitude;

import java.util.Map;

public class Demo {

    public static void main(String[] args) {
        Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
        amplitude.init("bcfd68add3ac9532f2f0920bdee7f9e2");
        amplitude.logEvent("Event name test");
    }

}
