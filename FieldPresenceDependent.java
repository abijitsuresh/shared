package com.example.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for validating that if one field is populated, others must be populated too
 */
@Documented
@Constraint(validatedBy = FieldPresenceDependentValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldPresenceDependent {
    String message() default "Validation failed: related fields are required";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Field that triggers validation when populated
     */
    String triggerField();
    
    /**
     * Fields that become required when trigger field is populated
     */
    String[] dependentFields();
}