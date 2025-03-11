package com.example.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for validating fields based on a specific value in another field
 */
@Documented
@Constraint(validatedBy = ValueDependentValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueDependent {
    String message() default "Validation failed: dependent fields required";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Field that contains the trigger value
     */
    String field();
    
    /**
     * Specific value that triggers the validation
     */
    String[] triggerValues();
    
    /**
     * Fields that become required when the condition is met
     */
    String[] dependentFields();
}
