package net.calickrosmp.builder.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationResult {
    private final List<ValidationIssue> issues = new ArrayList<>();

    public static ValidationResult success() {
        return new ValidationResult();
    }

    public static ValidationResult failure(ValidationIssue issue) {
        ValidationResult result = new ValidationResult();
        result.addIssue(issue);
        return result;
    }

    public void addIssue(ValidationIssue issue) {
        issues.add(issue);
    }

    public boolean isAllowed() {
        return issues.isEmpty();
    }

    public List<ValidationIssue> issues() {
        return Collections.unmodifiableList(issues);
    }
}
