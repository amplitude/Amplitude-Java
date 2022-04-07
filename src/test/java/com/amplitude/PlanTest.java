package com.amplitude;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlanTest {
    @Test
    public void testToJSONObject() throws JSONException {
        Plan testPlan = new Plan();
        String branch = "main";
        String version = "1.0.0";
        String source = "mobile";
        String versionId = "9ec23ba0-275f-468f-80d1-66b88bff9529";
        testPlan.setBranch(branch)
            .setSource(source)
            .setVersion(version)
            .setVersionId(versionId);
        JSONObject result = testPlan.toJSONObject();
        assertEquals(branch, result.getString(Constants.AMP_PLAN_BRANCH));
        assertEquals(source, result.getString(Constants.AMP_PLAN_SOURCE));
        assertEquals(version, result.getString(Constants.AMP_PLAN_VERSION));
        assertEquals(versionId, result.getString(Constants.AMP_PLAN_VERSION_ID));
    }
}
