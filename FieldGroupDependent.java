package com.example.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for validating that if any field in a group is populated, all others in the group must be populated too
 */
@Documented
@Constraint(validatedBy = FieldGroupDependentValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldGroupDependent {
    String message() default "All fields in the group must be populated if any are populated";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * List of fields that must all be provided if any one is provided
     */
    String[] fields();
}