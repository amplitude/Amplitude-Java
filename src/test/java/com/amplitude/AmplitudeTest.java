package com.amplitude;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AmplitudeTest {

    @Test
    public void testGetInstance() {
        Amplitude a = Amplitude.getInstance();
        Amplitude b = Amplitude.getInstance("");
        Amplitude c = Amplitude.getInstance(null);
        Amplitude d = Amplitude.getInstance("app1");
        Amplitude e = Amplitude.getInstance("app2");

        assertSame(a, b);
        assertSame(a, c);
        assertNotSame(d, e);
        assertNotSame(a, d);
    }
}
