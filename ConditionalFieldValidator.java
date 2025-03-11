package com.example.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.StringUtils;

import java.util.Arrays;

public class ConditionalFieldValidator implements ConstraintValidator<ConditionalField, Object> {
    private ConditionalField.ConditionType conditionType;
    private String dependsOn;
    private String[] values;
    private String[] fieldGroup;

    @Override
    public void initialize(ConditionalField constraintAnnotation) {
        this.conditionType = constraintAnnotation.condition();
        this.dependsOn = constraintAnnotation.dependsOn();
        this.values = constraintAnnotation.values();
        this.fieldGroup = constraintAnnotation.fieldGroup();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // If the field is null, we need to check conditions to see if it should be required
        if (value == null || (value instanceof String && !StringUtils.hasText((String) value))) {
            // Get the object that contains this field
            Object object = context.unwrap(BeanValidationConstraintContext.class).getObject();
            if (object == null) {
                // Can't validate without the parent object
                return true;
            }
            
            BeanWrapperImpl wrapper = new BeanWrapperImpl(object);
            
            switch (conditionType) {
                case FIELD_EQUALS:
                    // Check if the dependsOn field equals one of the specified values
                    Object dependsOnValue = wrapper.getPropertyValue(dependsOn);
                    if (dependsOnValue != null && Arrays.asList(values).contains(dependsOnValue.toString())) {
                        // The condition is met, this field should not be null
                        return false;
                    }
                    break;
                    
                case FIELD_NOT_EMPTY:
                    // Check if the dependsOn field is not empty
                    Object dependentFieldValue = wrapper.getPropertyValue(dependsOn);
                    if (isPopulated(dependentFieldValue)) {
                        // The dependent field is populated, this field should not be null
                        return false;
                    }
                    break;
                    
                case FIELD_GROUP:
                    // For FIELD_GROUP, the validation happens on non-null fields
                    // If this field is null, it passes
                    return true;
            }
        } else {
            // The field is not null, for FIELD_GROUP we need to check that other fields are also not null
            if (conditionType == ConditionalField.ConditionType.FIELD_GROUP) {
                Object object = context.unwrap(BeanValidationConstraintContext.class).getObject();
                if (object == null) {
                    return true;
                }
                
                BeanWrapperImpl wrapper = new BeanWrapperImpl(object);
                
                // Check that all fields in the group are not empty
                for (String field : fieldGroup) {
                    Object fieldValue = wrapper.getPropertyValue(field);
                    if (!isPopulated(fieldValue)) {
                        return false;
                    }
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
    
    /**
     * Interface for accessing the bean being validated
     */
    public interface BeanValidationConstraintContext {
        Object getObject();
    }
}