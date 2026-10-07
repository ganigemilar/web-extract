package com.webworkflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestPlan {

  @JsonProperty("name")
  private String name;

  @JsonProperty("url")
  private String url;

  @JsonProperty("session")
  private Path session;

  @JsonProperty("updateSession")
  private boolean updateSession = false;

  @JsonProperty("browser")
  private String browser = "chromium";

  @JsonProperty("headed")
  private boolean headed = false;

  @JsonProperty("timeout")
  private long timeout = 30000;

  @JsonProperty("output")
  private Path output = Path.of("action-result.json");

  @JsonProperty("stayOpen")
  private Long stayOpen = null;

  @JsonProperty("delay")
  private long delay = 0;

  @JsonProperty("steps")
  private List<TestStep> steps;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Path getSession() {
    return session;
  }

  public void setSession(Path session) {
    this.session = session;
  }

  public boolean isUpdateSession() {
    return updateSession;
  }

  public void setUpdateSession(boolean updateSession) {
    this.updateSession = updateSession;
  }

  public String getBrowser() {
    return browser;
  }

  public void setBrowser(String browser) {
    this.browser = browser;
  }

  public boolean isHeaded() {
    return headed;
  }

  public void setHeaded(boolean headed) {
    this.headed = headed;
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }

  public Path getOutput() {
    return output;
  }

  public void setOutput(Path output) {
    this.output = output;
  }

  public Long getStayOpen() {
    return stayOpen;
  }

  public void setStayOpen(Long stayOpen) {
    this.stayOpen = stayOpen;
  }

  public long getDelay() {
    return delay;
  }

  public void setDelay(long delay) {
    this.delay = delay;
  }

  public List<TestStep> getSteps() {
    return steps;
  }

  public void setSteps(List<TestStep> steps) {
    this.steps = steps;
  }
}