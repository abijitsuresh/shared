package com.example.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A class-level validation that inspects specific fields based on conditions
 */
@Documented
@Constraint(validatedBy = ClassLevelValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateFields {
    String message() default "Field validation failed";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Field validations to apply
     */
    FieldValidation[] value() default {};
    
    /**
     * Nested annotation for field validation
     */
    @interface FieldValidation {
        /**
         * Field to validate
         */
        String field();
        
        /**
         * Validation type
         */
        ValidationType type();
        
        /**
         * Messages for validation errors
         */
        String message() default "Field validation failed";
        
        /**
         * For DEPENDS_ON_VALUE: the field that contains the conditional value
         */
        String dependsOnField() default "";
        
        /**
         * For DEPENDS_ON_VALUE: the values that trigger the validation
         */
        String[] triggerValues() default {};
        
        /**
         * For DEPENDS_ON_PRESENCE: the field whose presence triggers this validation
         */
        String dependsOnPresenceOf() default "";
        
        /**
         * For DEPENDS_ON_GROUP: other fields that must be filled if this field is filled
         */
        String[] groupFields() default {};
    }
    
    /**
     * Types of field validations
     */
    enum ValidationType {
        /**
         * Field is required when another field has a specific value
         */
        DEPENDS_ON_VALUE,
        
        /**
         * Field is required when another field is present
         */
        DEPENDS_ON_PRESENCE,
        
        /**
         * If this field is filled, all fields in group must be filled
         */
        DEPENDS_ON_GROUP
    }
}