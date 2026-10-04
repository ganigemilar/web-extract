package com.webworkflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.webcommon.extraction.ExtractionQuery;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestStep {

    @JsonProperty("step")
    private String stepName;

    @JsonProperty("action")
    private String action;

    @JsonProperty("selector")
    private String selector;

    @JsonProperty("value")
    private String value;

    @JsonProperty("waitFor")
    private String waitFor;

    @JsonProperty("waitForNavigation")
    private Boolean waitForNavigation;

    @JsonProperty("delay")
    private long delay = 0;

    @JsonProperty("options")
    private Map<String, Object> options;

    @JsonProperty("assert")
    private Assertion assertion;

    @JsonProperty("continueOnError")
    private boolean continueOnError = false;

    @JsonProperty("extract")
    private List<ExtractionQuery> extract;

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getWaitFor() { return waitFor; }
    public void setWaitFor(String waitFor) { this.waitFor = waitFor; }

    public Boolean getWaitForNavigation() { return waitForNavigation; }
    public void setWaitForNavigation(Boolean waitForNavigation) { this.waitForNavigation = waitForNavigation; }

    public long getDelay() { return delay; }
    public void setDelay(long delay) { this.delay = delay; }

    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }

    public Assertion getAssertion() { return assertion; }
    public void setAssertion(Assertion assertion) { this.assertion = assertion; }

    public boolean isContinueOnError() { return continueOnError; }
    public void setContinueOnError(boolean continueOnError) { this.continueOnError = continueOnError; }

    public List<ExtractionQuery> getExtract() { return extract; }
    public void setExtract(List<ExtractionQuery> extract) { this.extract = extract; }
}