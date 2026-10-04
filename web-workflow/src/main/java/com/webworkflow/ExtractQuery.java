package com.webworkflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractQuery {

    @JsonProperty("query")
    private String query;

    @JsonProperty("attrs")
    private List<String> attrs = new ArrayList<>();

    @JsonProperty("html")
    private boolean html = false;

    @JsonProperty("limit")
    private int limit = 0;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<String> getAttrs() { return attrs; }
    public void setAttrs(List<String> attrs) { this.attrs = attrs; }

    public boolean isHtml() { return html; }
    public void setHtml(boolean html) { this.html = html; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}