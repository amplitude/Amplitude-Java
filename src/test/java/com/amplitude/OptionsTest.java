package com.amplitude;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionsTest {
    @Test
    public void testOptions() {
        Options option = new Options();
        option.minIdLength = 5;
        JSONObject optionJson = option.toJsonObject();
        assertEquals(5, optionJson.getInt("min_id_length"));
    }
}
