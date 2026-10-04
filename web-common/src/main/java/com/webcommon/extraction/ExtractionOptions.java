package com.webcommon.extraction;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExtractionOptions {

  private List<String> attrs = new ArrayList<>();
  private boolean html = false;
  private int limit = 0;

  public ExtractionOptions() {}

  public ExtractionOptions(List<String> attrs, boolean html, int limit) {
    this.attrs = attrs != null ? attrs : new ArrayList<>();
    this.html = html;
    this.limit = limit;
  }

  public ExtractionQuery toQuery(String query) {
    return new ExtractionQuery(query, attrs, html, limit);
  }
}