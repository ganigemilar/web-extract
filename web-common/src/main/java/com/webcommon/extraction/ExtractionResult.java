package com.webcommon.extraction;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ExtractionResult {

  private String query;
  private int count = 0;
  private List<Map<String, Object>> matches = new ArrayList<>();
  private String error;

  public ExtractionResult() {}

  public ExtractionResult(String query) {
    this.query = query;
  }

  public boolean hasError() {
    return error != null && !error.isEmpty();
  }

  public static Map<String, Object> createMatch(String text, Map<String, String> attributes, String html) {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("text", text);
    if (attributes != null && !attributes.isEmpty()) {
      match.put("attributes", attributes);
    }
    if (html != null) {
      match.put("html", html);
    }
    return match;
  }
}