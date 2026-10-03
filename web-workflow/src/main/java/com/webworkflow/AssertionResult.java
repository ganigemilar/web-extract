package com.webworkflow;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AssertionResult {

    @JsonProperty("type")
    private String type;

    @JsonProperty("expected")
    private Object expected;

    @JsonProperty("actual")
    private Object actual;

    @JsonProperty("passed")
    private boolean passed;

    @JsonProperty("message")
    private String message;

    public AssertionResult() {}

    public AssertionResult(String type, Object expected, Object actual, boolean passed, String message) {
        this.type = type;
        this.expected = expected;
        this.actual = actual;
        this.passed = passed;
        this.message = message;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Object getExpected() { return expected; }
    public void setExpected(Object expected) { this.expected = expected; }

    public Object getActual() { return actual; }
    public void setActual(Object actual) { this.actual = actual; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}