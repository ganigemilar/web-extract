package com.webcommon.extraction;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExtractionQuery {

  private String query;
  private List<String> attrs = new ArrayList<>();
  private boolean html = false;
  private int limit = 0;

  public ExtractionQuery() {}

  public ExtractionQuery(String query) {
    this.query = query;
  }

  public ExtractionQuery(String query, List<String> attrs, boolean html, int limit) {
    this.query = query;
    this.attrs = attrs != null ? attrs : new ArrayList<>();
    this.html = html;
    this.limit = limit;
  }
}