package com.amplitude;

import org.json.JSONException;
import org.json.JSONObject;

public class IngestionMetadata {
    /**
     * The source name, e.g. "ampli"
     */
    private String sourceName;
    /**
     * The source version, e.g. "2.0.0"
     */
    private String sourceVersion;

    private JSONObject jsonObject;

    /**
     * Set the ingestion metadata source name information.
     * @param sourceName source name for ingestion metadata
     * @return the same IngestionMetadata object
     */
    public IngestionMetadata setSourceName(String sourceName) {
        this.sourceName = sourceName;
        this.jsonObject = null;
        return this;
    }

    /**
     * Set the ingestion metadata source version information.
     * @param sourceVersion source version for ingestion metadata
     * @return the same IngestionMetadata object
     */
    public IngestionMetadata setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
        this.jsonObject = null;
        return this;
    }

    /**
     * Get JSONObject of current ingestion metadata
     * @return JSONObject including ingestion metadata information
     */
    protected JSONObject toJSONObject() throws JSONException {
        if (this.jsonObject != null) {
            return this.jsonObject;
        }

        JSONObject jsonObject = new JSONObject();
        if (!Utils.isEmptyString(sourceName)) {
            jsonObject.put(Constants.AMP_INGESTION_METADATA_SOURCE_NAME, sourceName);
        }
        if (!Utils.isEmptyString(sourceVersion)) {
            jsonObject.put(Constants.AMP_INGESTION_METADATA_SOURCE_VERSION, sourceVersion);
        }
        this.jsonObject = jsonObject;
        return jsonObject;
    }
}
