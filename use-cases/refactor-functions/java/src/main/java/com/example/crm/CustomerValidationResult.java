package com.example.crm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomerValidationResult {
    private final List<String> errors;

    public CustomerValidationResult(List<String> errors) {
        if (errors == null) {
            this.errors = new ArrayList<>();
        } else {
            this.errors = new ArrayList<>(errors);
        }
    }

    public static CustomerValidationResult success() {
        return new CustomerValidationResult(Collections.emptyList());
    }

    public static CustomerValidationResult failure(List<String> errors) {
        return new CustomerValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
