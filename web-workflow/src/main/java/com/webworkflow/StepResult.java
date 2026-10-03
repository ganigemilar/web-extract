package com.webworkflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class StepResult {

    @JsonProperty("step")
    private String stepName;

    @JsonProperty("action")
    private String action;

    @JsonProperty("selector")
    private String selector;

    @JsonProperty("value")
    private String value;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("matches")
    private int matches = 0;

    @JsonProperty("error")
    private String error;

    @JsonProperty("durationMs")
    private long durationMs = 0;

    @JsonProperty("navigated")
    private boolean navigated = false;

    @JsonProperty("newUrl")
    private String newUrl;

    @JsonProperty("assertions")
    private List<AssertionResult> assertions;

    @JsonProperty("options")
    private Map<String, Object> options;

    public StepResult() {}

    public StepResult(TestStep step) {
        this.stepName = step.getStepName();
        this.action = step.getAction();
        this.selector = step.getSelector();
        this.value = step.getValue();
        this.options = step.getOptions();
    }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public int getMatches() { return matches; }
    public void setMatches(int matches) { this.matches = matches; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isNavigated() { return navigated; }
    public void setNavigated(boolean navigated) { this.navigated = navigated; }

    public String getNewUrl() { return newUrl; }
    public void setNewUrl(String newUrl) { this.newUrl = newUrl; }

    public List<AssertionResult> getAssertions() { return assertions; }
    public void setAssertions(List<AssertionResult> assertions) { this.assertions = assertions; }

    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
}