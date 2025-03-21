/**
 * MODULE STRUCTURE:
 * 
 * 1. domain-model
 *    - Contains model classes
 *    - Contains validation annotations
 *    - Contains schema validator implementation
 * 
 * 2. shared-service
 *    - Depends on domain-model
 *    - Contains schema entity model
 *    - Contains validation utilities
 * 
 * 3. spring-boot-app
 *    - Depends on both domain-model and shared-service
 *    - Loads schema on startup
 *    - Contains controllers that use validation
 */

//==============================================================================
// DOMAIN-MODEL MODULE
//==============================================================================

package com.example.domain.model;

// User model class
public class UserRequest {
    private Integer age;
    private String name;
    private LocalDateTime requestedDate;
    
    @SchemaValidation
    private String country;
    
    @Valid
    private Address address;
    
    @SchemaValidation
    private String comment;
    
    @SchemaValidation
    private LocalDateTime commentDate;
    
    @SchemaValidation
    private String commentBy;

    // Getters and setters
    // ...
}

// Address model class
public class Address {
    @SchemaValidation
    private String line1;
    
    @SchemaValidation
    private String line2;
    
    @SchemaValidation
    private String city;
    
    @SchemaValidation
    private String state;

    // Getters and setters
    // ...
}

package com.example.domain.validation;

/**
 * Thread-local context for validation
 * Stores validation context and current property path
 */
public class ValidationThreadLocal {
    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();
    
    /**
     * Initialize the thread-local storage
     */
    public static void initialize() {
        CONTEXT.set(new HashMap<>());
    }
    
    /**
     * Set the validation context
     */
    public static void setValidationContext(ValidationContext context) {
        Map<String, Object> map = CONTEXT.get();
        if (map == null) {
            map = new HashMap<>();
            CONTEXT.set(map);
        }
        map.put("context", context);
    }
    
    /**
     * Get the validation context
     */
    public static ValidationContext getValidationContext() {
        Map<String, Object> map = CONTEXT.get();
        return map != null ? (ValidationContext) map.get("context") : null;
    }
    
    /**
     * Set the current property path being validated
     */
    public static void setCurrentPath(String path) {
        Map<String, Object> map = CONTEXT.get();
        if (map != null) {
            map.put("currentPath", path);
        }
    }
    
    /**
     * Get the current property path being validated
     */
    public static String getCurrentPath() {
        Map<String, Object> map = CONTEXT.get();
        return map != null ? (String) map.get("currentPath") : null;
    }
    
    /**
     * Clear the thread-local storage
     */
    public static void clear() {
        CONTEXT.remove();
    }
}

package com.example.domain.validation;

import java.lang.annotation.*;

/**
 * Custom annotation for schema-based validation
 */
@Documented
@Constraint(validatedBy = SchemaValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaValidation {
    String message() default "Field validation failed";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

/**
 * Validator implementation for SchemaValidation annotation
 */
@Component
public class SchemaValidator implements ConstraintValidator<SchemaValidation, Object> {
    
    @Override
    public void initialize(SchemaValidation constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // Get the validation context from thread local
        ValidationContext validationContext = ValidationThreadLocal.getValidationContext();
        if (validationContext == null) {
            return true; // No validation context available
        }
        
        // Get the current property path being validated
        String fieldName = ValidationThreadLocal.getCurrentPath();
        if (fieldName == null) {
            return true; // Cannot determine field name
        }
        
        // Delegate validation to the ValidationContext
        return validationContext.validateField(fieldName, value, context);
    }
}

/**
 * Interface for validation context
 * This interface is implemented in the shared-service module
 */
public interface ValidationContext {
    boolean validateField(String fieldName, Object value, ConstraintValidatorContext context);
}

/**
 * Field Type enum for validation
 */
public enum FieldType {
    STRING,
    INT32,
    INT64,
    DECIMAL,
    BOOLEAN,
    LOCAL_DATE,
    LOCAL_DATE_TIME,
    ZONED_DATE_TIME,
    OBJECT,
    ARRAY,
    MAP,
    EMAIL,  // Special string format
    UUID,   // Special string format
    PHONE,  // Special string format
    URL     // Special string format
}

/**
 * Requirement Type enum for validation
 */
public enum RequirementType {
    REQUIRED,    // Always required
    CONDITIONAL, // Required only when condition is met
    OPTIONAL     // Never required but validate type if present
}

//==============================================================================
// SHARED-SERVICE MODULE
//==============================================================================

package com.example.shared.model;

/**
 * Schema entity model for MongoDB
 */
@Document(collection = "validation_schemas")
public class ValidationSchema {
    @Id
    private String id;
    private String schemaName;
    private Map<String, ValidationRule> rules;
    
    // Getters and setters
    // ...
}

/**
 * Validation rule model for schema
 */
public class ValidationRule {
    private RequirementType requirementType = RequirementType.OPTIONAL;
    private FieldType fieldType;
    private String condition; // SpEL expression for conditional validation
    private String errorMessage;
    private Map<String, Object> typeValidationParams = new HashMap<>();
    
    // Helpers
    public boolean isRequired() {
        return requirementType == RequirementType.REQUIRED;
    }
    
    public boolean isConditional() {
        return requirementType == RequirementType.CONDITIONAL;
    }
    
    // Getters and setters
    // ...
}

package com.example.shared.validation;

import com.example.domain.validation.ValidationContext;

/**
 * Implementation of ValidationContext interface
 */
public class ValidationContextImpl implements ValidationContext {
    private final Object rootObject;
    private final Map<String, ValidationRule> rules;
    
    public ValidationContextImpl(Object rootObject, Map<String, ValidationRule> rules) {
        this.rootObject = rootObject;
        this.rules = rules;
    }
    
    @Override
    public boolean validateField(String fieldName, Object value, ConstraintValidatorContext context) {
        // Get validation rule for field
        ValidationRule rule = rules.get(fieldName);
        if (rule == null) {
            return true; // No rules for this field
        }
        
        // Check if field is required
        boolean isRequired = false;
        if (rule.isRequired()) {
            isRequired = true;
        } else if (rule.isConditional() && rule.getCondition() != null && !rule.getCondition().isEmpty()) {
            isRequired = ValidationUtils.evaluateCondition(rule.getCondition(), rootObject);
        }
        
        // Validate required field
        if (isRequired && ValidationUtils.isEmpty(value)) {
            addConstraintViolation(context, rule.getErrorMessage(), "Field is required");
            return false;
        }
        
        // Validate type if value is not null
        if (value != null && rule.getFieldType() != null) {
            boolean typeValid = ValidationUtils.validateType(value, rule.getFieldType(), rule.getTypeValidationParams());
            if (!typeValid) {
                addConstraintViolation(context, rule.getErrorMessage(), 
                        "Invalid type. Expected: " + rule.getFieldType());
                return false;
            }
        }
        
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, 
                                       String errorMessage, String defaultMessage) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                errorMessage != null ? errorMessage : defaultMessage)
                .addConstraintViolation();
    }
}

/**
 * Validation utilities for the shared service
 */
public final class ValidationUtils {
    
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    
    private ValidationUtils() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Validate an object against a schema
     */
    public static <T> Set<ConstraintViolation<T>> validate(
            T object, Map<String, ValidationRule> rules, Validator validator) {
        
        try {
            // Initialize thread local
            ValidationThreadLocal.initialize();
            
            // Create validation context
            ValidationContext context = new ValidationContextImpl(object, rules);
            
            // Set context in thread local
            ValidationThreadLocal.setValidationContext(context);
            
            // Perform validation
            return validator.validate(object);
        } finally {
            // Clear context
            ValidationThreadLocal.clear();
        }
    }
    
    /**
     * Validate specific fields of an object
     */
    public static <T> Set<ConstraintViolation<T>> validateFields(
            T object, Map<String, ValidationRule> rules, Validator validator, String... fieldNames) {
        
        try {
            // Initialize thread local
            ValidationThreadLocal.initialize();
            
            // Create validation context
            ValidationContext context = new ValidationContextImpl(object, rules);
            
            // Set context in thread local
            ValidationThreadLocal.setValidationContext(context);
            
            // Validate specific fields
            Set<ConstraintViolation<T>> violations = new HashSet<>();
            for (String fieldName : fieldNames) {
                ValidationThreadLocal.setCurrentPath(fieldName);
                violations.addAll(validator.validateProperty(object, fieldName));
            }
            
            return violations;
        } finally {
            // Clear context
            ValidationThreadLocal.clear();
        }
    }
    
    /**
     * Validate and throw exception if validation fails
     */
    public static <T> void validateAndThrow(
            T object, Map<String, ValidationRule> rules, Validator validator) {
        
        Set<ConstraintViolation<T>> violations = validate(object, rules, validator);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
    
    /**
     * Evaluate a SpEL condition
     */
    public static boolean evaluateCondition(String condition, Object root) {
        if (condition == null || condition.trim().isEmpty()) {
            return false;
        }
        
        try {
            Expression expression = PARSER.parseExpression(condition);
            StandardEvaluationContext evalContext = new StandardEvaluationContext(root);
            return Boolean.TRUE.equals(expression.getValue(evalContext, Boolean.class));
        } catch (Exception e) {
            // Log error
            return false;
        }
    }
    
    /**
     * Check if an object is empty
     */
    public static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0;
        }
        return false;
    }
    
    /**
     * Validate type of a value
     */
    public static boolean validateType(
            Object value, FieldType expectedType, Map<String, Object> validationParams) {
        // Type validation logic
        if (value == null) {
            return true;
        }
        
        // Implementation of type validation code based on expectedType
        switch (expectedType) {
            case STRING:
                return value instanceof String && validateStringConstraints((String) value, validationParams);
            
            case INT32:
                // Implementation for INT32...
                return value instanceof Integer && validateNumberConstraints(value, validationParams);
            
            // Other type validations...
            
            default:
                return true;
        }
    }
    
    // Helper validation methods for different types...
    private static boolean validateStringConstraints(String value, Map<String, Object> params) {
        // String validation logic
        return true;
    }
    
    private static boolean validateNumberConstraints(Object value, Map<String, Object> params) {
        // Number validation logic
        return true;
    }
    
    // Other helper methods...
}

//==============================================================================
// SPRING-BOOT-APP MODULE
//==============================================================================

package com.example.app.config;

/**
 * Schema registry to load and cache validation schemas
 */
@Component
public class SchemaRegistry {
    
    private static final Map<String, Map<String, ValidationRule>> schemaCache = new ConcurrentHashMap<>();
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @PostConstruct
    public void init() {
        // Load all schemas from MongoDB at startup
        List<ValidationSchema> schemas = mongoTemplate.findAll(ValidationSchema.class);
        for (ValidationSchema schema : schemas) {
            schemaCache.put(schema.getSchemaName(), schema.getRules());
        }
    }
    
    /**
     * Get validation rules for a schema
     */
    public static Map<String, ValidationRule> getSchemaRules(String schemaName) {
        return schemaCache.get(schemaName);
    }
    
    /**
     * Refresh a specific schema
     */
    public void refreshSchema(String schemaName) {
        ValidationSchema schema = mongoTemplate.findOne(
                Query.query(Criteria.where("schemaName").is(schemaName)), 
                ValidationSchema.class);
        
        if (schema != null) {
            schemaCache.put(schemaName, schema.getRules());
        } else {
            schemaCache.remove(schemaName);
        }
    }
    
    /**
     * Refresh all schemas
     */
    public void refreshAllSchemas() {
        schemaCache.clear();
        init();
    }
}return ResponseEntity.badRequest().body("Validation failed");
            }
            
            // Process the validated request
            return ResponseEntity.ok("User updated successfully");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body("Validation failed: " + ex.getMessage());
        }
    }
    
    // Helper methods
    private UserRequest getUserById(String id) {
        // Implementation
        return null;
    }
    
    private void applyUpdates(UserRequest user, Map<String, Object> updates) {
        // Implementation
    }
}

package com.example.app.service;

/**
 * Example service using validation
 */
@Service
public class UserService {
    
    @Autowired
    private Validator validator;
    
    /**
     * Validate user request
     */
    public boolean validateUser(String schemaName, UserRequest request) {
        // Get validation rules for schema
        Map<String, ValidationRule> rules = SchemaRegistry.getSchemaRules(schemaName);
        if (rules == null) {
            return false;
        }
        
        // Validate user request
        Set<ConstraintViolation<UserRequest>> violations = 
            ValidationUtils.validate(request, rules, validator);
        
        return violations.isEmpty();
    }
    
    /**
     * Validate specific fields
     */
    public boolean validateUserFields(String schemaName, UserRequest request, String... fieldNames) {
        // Get validation rules for schema
        Map<String, ValidationRule> rules = SchemaRegistry.getSchemaRules(schemaName);
        if (rules == null) {
            return false;
        }
        
        // Validate specific fields
        Set<ConstraintViolation<UserRequest>> violations = 
            ValidationUtils.validateFields(request, rules, validator, fieldNames);
        
        return violations.isEmpty();
    }
    
    /**
     * Custom manual validation without using annotations
     */
    public boolean validateManually(Map<String, Object> data, String schemaName) {
        // Get schema rules
        Map<String, ValidationRule> rules = SchemaRegistry.getSchemaRules(schemaName);
        if (rules == null) {
            return false;
        }
        
        // For each field that needs validation
        for (Map.Entry<String, ValidationRule> entry : rules.entrySet()) {
            String fieldPath = entry.getKey();
            ValidationRule rule = entry.getValue();
            
            // Get value from nested path if needed (e.g. "address.city")
            Object value = getValueFromPath(data, fieldPath);
            
            // Is field required?
            boolean isRequired = false;
            
            if (rule.isRequired()) {
                isRequired = true;
            } else if (rule.isConditional() && rule.getCondition() != null) {
                isRequired = ValidationUtils.evaluateCondition(rule.getCondition(), data);
            }
            
            if (isRequired && ValidationUtils.isEmpty(value)) {
                return false; // Validation failed - required field is missing
            }
            
            // Validate type if value is not null
            if (value != null && rule.getFieldType() != null) {
                boolean typeValid = ValidationUtils.validateType(
                    value, rule.getFieldType(), rule.getTypeValidationParams());
                
                if (!typeValid) {
                    return false; // Validation failed - type mismatch
                }
            }
        }
        
        return true; // All validations passed
    }
    
    /**
     * Helper method to get a value from a nested path in a map
     */
    private Object getValueFromPath(Map<String, Object> data, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        String[] parts = path.split("\\.");
        Object current = data;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return current;
    }
}

/**
 * Example MongoDB schema document
 */
/*
{
  "_id": "user_request_validation",
  "schemaName": "user_request_validation",
  "rules": {
    "country": {
      "requirementType": "REQUIRED",
      "fieldType": "STRING",
      "condition": null,
      "errorMessage": "Country is required",
      "typeValidationParams": {}
    },
    "address.line1": {
      "requirementType": "CONDITIONAL",
      "fieldType": "STRING",
      "condition": "country == 'US'",
      "errorMessage": "Address line1 is required for US addresses",
      "typeValidationParams": {}
    },
    "address.line2": {
      "requirementType": "CONDITIONAL",
      "fieldType": "STRING",
      "condition": "country == 'US'",
      "errorMessage": "Address line2 is required for US addresses",
      "typeValidationParams": {}
    },
    "address.city": {
      "requirementType": "CONDITIONAL",
      "fieldType": "STRING",
      "condition": "country == 'US'",
      "errorMessage": "City is required for US addresses",
      "typeValidationParams": {}
    },
    "address.state": {
      "requirementType": "CONDITIONAL",
      "fieldType": "STRING",
      "condition": "country == 'US'",
      "errorMessage": "State is required for US addresses",
      "typeValidationParams": {
        "maxLength": 2
      }
    },
    "comment": {
      "requirementType": "CONDITIONAL",
      "fieldType": "STRING",
      "condition": "commentDate != null || commentBy != null",
      "errorMessage": "Comment is required when commentDate or commentBy is provided",
      "typeValidationParams": {}
    },
    "commentDate": {
      "requirementType": "CONDITIONAL",
      "fieldType": "LOCAL_DATE_TIME",
      "condition": "comment != null || commentBy != null",
      "errorMessage": "CommentDate is required when comment or commentBy is provided",
      "typeValidationParams": {}
    },
    "commentBy": {
      "requirementType": "CONDITIONAL",
      "fieldType": "STRING",
      "condition": "comment != null || commentDate != null",
      "errorMessage": "CommentBy is required when comment or commentDate is provided",
      "typeValidationParams": {
        "minLength": 2
      }
    }
  }
}
*/

//==============================================================================
// CUSTOM PATH-TRACKING VALIDATOR (OPTIONAL ENHANCEMENT)
//==============================================================================

/**
 * Optional custom validator factory that tracks property paths automatically
 * This would replace standard validator injection in the app
 */
@Component
public class PathTrackingValidatorFactory implements ValidatorFactory {
    
    private final ValidatorFactory defaultFactory = Validation.buildDefaultValidatorFactory();
    
    @Override
    public Validator getValidator() {
        return new PathTrackingValidator(defaultFactory.getValidator());
    }
    
    @Override
    public MessageInterpolator getMessageInterpolator() {
        return defaultFactory.getMessageInterpolator();
    }
    
    @Override
    public TraversableResolver getTraversableResolver() {
        return defaultFactory.getTraversableResolver();
    }
    
    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return defaultFactory.getConstraintValidatorFactory();
    }
    
    @Override
    public ParameterNameProvider getParameterNameProvider() {
        return defaultFactory.getParameterNameProvider();
    }
    
    @Override
    public ClockProvider getClockProvider() {
        return defaultFactory.getClockProvider();
    }
    
    @Override
    public void close() {
        defaultFactory.close();
    }
    
    @Override
    public <T> T unwrap(Class<T> type) {
        return defaultFactory.unwrap(type);
    }
    
    /**
     * Custom validator that tracks property paths during validation
     */
    private static class PathTrackingValidator implements Validator {
        private final Validator delegate;
        
        public PathTrackingValidator(Validator delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
            // For full object validation, individual paths will be handled
            // by validateProperty for each property with constraints
            return delegate.validate(object, groups);
        }
        
        @Override
        public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
            try {
                // Set the current property path in the thread-local context
                ValidationThreadLocal.setCurrentPath(propertyName);
                
                // Delegate to the standard validator
                return delegate.validateProperty(object, propertyName, groups);
            } finally {
                // Always clear the path even if an exception occurs
                ValidationThreadLocal.setCurrentPath(null);
            }
        }
        
        @Override
        public <T> Set<ConstraintViolation<T>> validateValue(Class<T> beanType, String propertyName, Object value, Class<?>... groups) {
            try {
                // Set the current property path in the thread-local context
                ValidationThreadLocal.setCurrentPath(propertyName);
                
                // Delegate to the standard validator
                return delegate.validateValue(beanType, propertyName, value, groups);
            } finally {
                // Always clear the path even if an exception occurs
                ValidationThreadLocal.setCurrentPath(null);
            }
        }
    }
}

/**
 * Spring configuration to use the custom validator factory
 */
@Configuration
public class ValidationConfig {
    
    @Bean
    public LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean() {
            @Override
            public Validator getValidator() {
                return new PathTrackingValidator(super.getValidator());
            }
        };
    }
    
    /**
     * Custom validator that tracks property paths during validation
     */
    private static class PathTrackingValidator implements Validator {
        private final Validator delegate;
        
        public PathTrackingValidator(Validator delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
            return delegate.validate(object, groups);
        }
        
        @Override
        public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
            try {
                ValidationThreadLocal.setCurrentPath(propertyName);
                return delegate.validateProperty(object, propertyName, groups);
            } finally {
                ValidationThreadLocal.setCurrentPath(null);
            }
        }
        
        @Override
        public <T> Set<ConstraintViolation<T>> validateValue(Class<T> beanType, String propertyName, Object value, Class<?>... groups) {
            try {
                ValidationThreadLocal.setCurrentPath(propertyName);
                return delegate.validateValue(beanType, propertyName, value, groups);
            } finally {
                ValidationThreadLocal.setCurrentPath(null);
            }
        }
    }
}
