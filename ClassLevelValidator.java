package com.example.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.StringUtils;

import java.util.Arrays;

public class ClassLevelValidator implements ConstraintValidator<ValidateFields, Object> {
    private ValidateFields.FieldValidation[] fieldValidations;
    
    @Override
    public void initialize(ValidateFields constraintAnnotation) {
        this.fieldValidations = constraintAnnotation.value();
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Cannot validate null objects
        }
        
        boolean isValid = true;
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        
        // Disable default violation message
        context.disableDefaultConstraintViolation();
        
        // Process each field validation
        for (ValidateFields.FieldValidation validation : fieldValidations) {
            String fieldName = validation.field();
            Object fieldValue = wrapper.getPropertyValue(fieldName);
            boolean fieldValid = true;
            
            switch (validation.type()) {
                case DEPENDS_ON_VALUE:
                    fieldValid = validateDependsOnValue(wrapper, validation, fieldValue);
                    break;
                    
                case DEPENDS_ON_PRESENCE:
                    fieldValid = validateDependsOnPresence(wrapper, validation, fieldValue);
                    break;
                    
                case DEPENDS_ON_GROUP:
                    fieldValid = validateDependsOnGroup(wrapper, validation, fieldValue);
                    break;
            }
            
            if (!fieldValid) {
                context.buildConstraintViolationWithTemplate(validation.message())
                       .addPropertyNode(fieldName)
                       .addConstraintViolation();
                isValid = false;
            }
        }
        
        return isValid;
    }
    
    /**
     * Validate field when its value depends on another field's value
     */
    private boolean validateDependsOnValue(BeanWrapperImpl wrapper, 
                                          ValidateFields.FieldValidation validation, 
                                          Object fieldValue) {
        String dependsOnField = validation.dependsOnField();
        String[] triggerValues = validation.triggerValues();
        
        if (dependsOnField.isEmpty() || triggerValues.length == 0) {
            return true; // Not configured properly
        }
        
        Object dependsOnValue = wrapper.getPropertyValue(dependsOnField);
        
        if (dependsOnValue != null && Arrays.asList(triggerValues).contains(dependsOnValue.toString())) {
            // Trigger condition met, field must be populated
            return isPopulated(fieldValue);
        }
        
        return true;
    }
    
    /**
     * Validate field when its value depends on another field's presence
     */
    private boolean validateDependsOnPresence(BeanWrapperImpl wrapper, 
                                             ValidateFields.FieldValidation validation, 
                                             Object fieldValue) {
        String dependsOnField = validation.dependsOnPresenceOf();
        
        if (dependsOnField.isEmpty()) {
            return true; // Not configured properly
        }
        
        Object dependsOnValue = wrapper.getPropertyValue(dependsOnField);
        
        if (isPopulated(dependsOnValue)) {
            // Trigger field is populated, this field must be populated
            return isPopulated(fieldValue);
        }
        
        return true;
    }
    
    /**
     * Validate that if this field is populated, all fields in group are populated
     */
    private boolean validateDependsOnGroup(BeanWrapperImpl wrapper, 
                                          ValidateFields.FieldValidation validation, 
                                          Object fieldValue) {
        String[] groupFields = validation.groupFields();
        
        if (groupFields.length == 0) {
            return true; // Not configured properly
        }
        
        // If this field is populated, all group fields must be populated
        if (isPopulated(fieldValue)) {
            for (String groupField : groupFields) {
                Object groupFieldValue = wrapper.getPropertyValue(groupField);
                if (!isPopulated(groupFieldValue)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Helper to check if a value is populated
     */
    private boolean isPopulated(Object value) {
        if (value == null) {
            return false;
        }
        
        if (value instanceof String) {
            return StringUtils.hasText((String) value);
        }
        
        return true;
    }
}