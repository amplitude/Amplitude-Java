package com.amplitude;

import org.json.JSONArray;
import org.json.JSONObject;

public class Utils {
  public static String getStringValueWithKey(JSONObject json, String key) {
    return json.has(key) && json.getString(key) != null ? json.getString(key) : "";
  }

  public static JSONObject getJSONObjectValueWithKey(JSONObject json, String key) {
    return (json.has(key) && !json.isNull(key)) ? json.getJSONObject(key) : new JSONObject();
  }

  public static int[] jsonArrayToIntArray(JSONArray jsonArray) {
    int[] intArray = new int[jsonArray.length()];
    for (int i = 0; i < intArray.length; i++) {
      intArray[i] = jsonArray.optInt(i);
    }
    return intArray;
  }

  public static int[] convertJSONArrayToIntArray(JSONObject json, String key) {
    boolean hasKey = json.has(key) && !json.isNull(key);
    if (!hasKey) return new int[] {};
    else {
      JSONArray jsonArray = json.getJSONArray(key);
      return jsonArrayToIntArray(jsonArray);
    }
  }
}
