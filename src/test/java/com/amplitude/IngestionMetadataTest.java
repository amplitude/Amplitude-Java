package com.amplitude;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IngestionMetadataTest {
    @Test
    public void testToJSONObject() throws JSONException {
        IngestionMetadata ingestionMetadata = new IngestionMetadata();
        String sourceName = "ampli";
        String sourceVersion = "1.0.0";
        ingestionMetadata.setSourceName(sourceName)
            .setSourceVersion(sourceVersion);
        JSONObject result = ingestionMetadata.toJSONObject();
        assertEquals(sourceName, result.getString(Constants.INGESTION_METADATA_SOURCE_NAME));
        assertEquals(sourceVersion, result.getString(Constants.INGESTION_METADATA_SOURCE_VERSION));
    }
}
