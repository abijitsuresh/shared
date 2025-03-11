package com.example.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level validation annotation that can check conditions on other fields
 */
@Documented
@Constraint(validatedBy = ConditionalFieldValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConditionalField {
    String message() default "This field is required based on other field values";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * The condition type to check
     */
    ConditionType condition() default ConditionType.FIELD_EQUALS;
    
    /**
     * The field name to check against
     */
    String dependsOn() default "";
    
    /**
     * The value to compare with (for FIELD_EQUALS condition)
     */
    String[] values() default {};
    
    /**
     * For FIELD_NOT_EMPTY condition, additional field names that must not be empty
     * when this field is not empty
     */
    String[] fieldGroup() default {};
    
    /**
     * Types of conditions to check
     */
    enum ConditionType {
        /**
         * This field is required when another field equals a specific value
         */
        FIELD_EQUALS,
        
        /**
         * This field is required when another field is not empty
         */
        FIELD_NOT_EMPTY,
        
        /**
         * If this field is not empty, all fields in fieldGroup must also not be empty
         */
        FIELD_GROUP
    }
}