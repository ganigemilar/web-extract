package com.webworkflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Assertion {

  @JsonProperty("urlContains")
  private String urlContains;

  @JsonProperty("urlEquals")
  private String urlEquals;

  @JsonProperty("titleContains")
  private String titleContains;

  @JsonProperty("titleEquals")
  private String titleEquals;

  @JsonProperty("selectorExists")
  private String selectorExists;

  @JsonProperty("selectorNotExists")
  private String selectorNotExists;

  @JsonProperty("selectorText")
  private SelectorTextAssertion selectorText;

  @JsonProperty("selectorValue")
  private SelectorValueAssertion selectorValue;

  @JsonProperty("selectorCount")
  private SelectorCountAssertion selectorCount;

  @JsonProperty("jsExpression")
  private String jsExpression;

  public String getUrlContains() {
    return urlContains;
  }

  public void setUrlContains(String urlContains) {
    this.urlContains = urlContains;
  }

  public String getUrlEquals() {
    return urlEquals;
  }

  public void setUrlEquals(String urlEquals) {
    this.urlEquals = urlEquals;
  }

  public String getTitleContains() {
    return titleContains;
  }

  public void setTitleContains(String titleContains) {
    this.titleContains = titleContains;
  }

  public String getTitleEquals() {
    return titleEquals;
  }

  public void setTitleEquals(String titleEquals) {
    this.titleEquals = titleEquals;
  }

  public String getSelectorExists() {
    return selectorExists;
  }

  public void setSelectorExists(String selectorExists) {
    this.selectorExists = selectorExists;
  }

  public String getSelectorNotExists() {
    return selectorNotExists;
  }

  public void setSelectorNotExists(String selectorNotExists) {
    this.selectorNotExists = selectorNotExists;
  }

  public SelectorTextAssertion getSelectorText() {
    return selectorText;
  }

  public void setSelectorText(SelectorTextAssertion selectorText) {
    this.selectorText = selectorText;
  }

  public SelectorValueAssertion getSelectorValue() {
    return selectorValue;
  }

  public void setSelectorValue(SelectorValueAssertion selectorValue) {
    this.selectorValue = selectorValue;
  }

  public SelectorCountAssertion getSelectorCount() {
    return selectorCount;
  }

  public void setSelectorCount(SelectorCountAssertion selectorCount) {
    this.selectorCount = selectorCount;
  }

  public String getJsExpression() {
    return jsExpression;
  }

  public void setJsExpression(String jsExpression) {
    this.jsExpression = jsExpression;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SelectorTextAssertion {
    @JsonProperty("selector")
    private String selector;
    @JsonProperty("equals")
    private String equals;
    @JsonProperty("contains")
    private String contains;

    public String getSelector() {
      return selector;
    }

    public void setSelector(String selector) {
      this.selector = selector;
    }

    public String getEquals() {
      return equals;
    }

    public void setEquals(String equals) {
      this.equals = equals;
    }

    public String getContains() {
      return contains;
    }

    public void setContains(String contains) {
      this.contains = contains;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SelectorValueAssertion {
    @JsonProperty("selector")
    private String selector;
    @JsonProperty("equals")
    private String equals;

    public String getSelector() {
      return selector;
    }

    public void setSelector(String selector) {
      this.selector = selector;
    }

    public String getEquals() {
      return equals;
    }

    public void setEquals(String equals) {
      this.equals = equals;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SelectorCountAssertion {
    @JsonProperty("selector")
    private String selector;
    @JsonProperty("equals")
    private Integer equals;
    @JsonProperty("gt")
    private Integer gt;
    @JsonProperty("lt")
    private Integer lt;

    public String getSelector() {
      return selector;
    }

    public void setSelector(String selector) {
      this.selector = selector;
    }

    public Integer getEquals() {
      return equals;
    }

    public void setEquals(Integer equals) {
      this.equals = equals;
    }

    public Integer getGt() {
      return gt;
    }

    public void setGt(Integer gt) {
      this.gt = gt;
    }

    public Integer getLt() {
      return lt;
    }

    public void setLt(Integer lt) {
      this.lt = lt;
    }
  }
}