package com.aidigital.reportconstructor.service.common.error;

import java.util.Objects;

/**
 * Named parameter used to format and expose validation messages.
 */
public final class ValidationParameter {

    private final String code;
    private final String value;

    public ValidationParameter(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ValidationParameter that = (ValidationParameter) o;
        return Objects.equals(code, that.code) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, value);
    }

    @Override
    public String toString() {
        return String.format("%s: %s", code, value);
    }
}
