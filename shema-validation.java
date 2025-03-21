// Generic field-level validation approach with MongoDB schema configuration

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.PostConstruct;
import java.lang.annotation.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

// Custom annotation for schema-based validation
@Documented
@Constraint(validatedBy = SchemaValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaValidation {
    String message() default "Field validation failed";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// MongoDB schema document structure
@Document(collection = "validation_schemas")
public class ValidationSchema {
    @Id
    private String id;
    private String schemaName;
    private Map<String, ValidationRule> rules;
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getSchemaName() {
        return schemaName;
    }
    
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }
    
    public Map<String, ValidationRule> getRules() {
        return rules;
    }
    
    public void setRules(Map<String, ValidationRule> rules) {
        this.rules = rules;
    }
}

public class ValidationRule {
    // Validation requirement type
    public enum RequirementType {
        REQUIRED,    // Always required
        CONDITIONAL, // Required only when condition is met
        OPTIONAL     // Never required but validate type if present
    }
    
    // Field data type
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
    
    private RequirementType requirementType = RequirementType.OPTIONAL;
    private FieldType fieldType;
    private String condition; // SpEL expression for conditional validation
    private String errorMessage;
    private Map<String, Object> typeValidationParams = new HashMap<>(); // Additional validation params
    
    // Getters and setters for all fields
    public RequirementType getRequirementType() {
        return requirementType;
    }
    
    public void setRequirementType(RequirementType requirementType) {
        this.requirementType = requirementType;
    }
    
    public FieldType getFieldType() {
        return fieldType;
    }
    
    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }
    
    public String getCondition() {
        return condition;
    }
    
    public void setCondition(String condition) {
        this.condition = condition;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, Object> getTypeValidationParams() {
        return typeValidationParams;
    }
    
    public void setTypeValidationParams(Map<String, Object> typeValidationParams) {
        this.typeValidationParams = typeValidationParams;
    }
    
    // Helper method to check if a value is required based on the requirement type
    public boolean isRequired() {
        return requirementType == RequirementType.REQUIRED;
    }
    
    // Helper method to check if a value has conditional requirement
    public boolean isConditional() {
        return requirementType == RequirementType.CONDITIONAL;
    }
}

// Generic validator implementation that uses schema registry
@Component
public class SchemaValidator implements ConstraintValidator<SchemaValidation, Object> {
    
    // ThreadLocal to store the current object being validated to avoid passing it through context
    private static final ThreadLocal<Object> CURRENT_OBJECT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // Get current object being validated
        Object rootObject = CURRENT_OBJECT.get();
        String schemaName = CURRENT_SCHEMA.get();
        
        if (rootObject == null || schemaName == null) {
            return true; // No validation context
        }
        
        // Get field name from property path
        String fieldName = extractFieldName(context);
        if (fieldName == null) {
            return true; // Cannot determine field name
        }
        
        // Check if field has validation rules
        ValidationRule rule = SchemaRegistry.getValidationRule(schemaName, fieldName);
        if (rule == null) {
            return true; // No rules for this field
        }
        
        // Step 1: Check if value is required based on requirement type
        boolean isRequired = false;
        
        if (rule.isRequired()) {
            // Always required
            isRequired = true;
        } else if (rule.isConditional() && rule.getCondition() != null && !rule.getCondition().isEmpty()) {
            // Conditionally required - evaluate the condition
            isRequired = ValidationUtils.evaluateCondition(rule.getCondition(), rootObject);
        }
        
        // If required and value is empty, validation fails
        if (isRequired && ValidationUtils.isEmpty(value)) {
            addConstraintViolation(context, rule.getErrorMessage(), "Field is required");
            return false;
        }
        
        // Step 2: If value is not empty, validate its type
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
    
    /**
     * Set the root object and schema for the current validation context
     */
    public static <T> void setValidationContext(String schemaName, T object) {
        CURRENT_SCHEMA.set(schemaName);
        CURRENT_OBJECT.set(object);
    }
    
    /**
     * Clear the validation context after validation is complete
     */
    public static void clearValidationContext() {
        CURRENT_SCHEMA.remove();
        CURRENT_OBJECT.remove();
    }
    
    private String extractFieldName(ConstraintValidatorContext context) {
        try {
            return context.getPropertyPath().toString();
        } catch (Exception e) {
            // Log error
            return null;
        }
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, 
                                       String errorMessage, String defaultMessage) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                errorMessage != null ? errorMessage : defaultMessage)
                .addConstraintViolation();
    }
}
}

// SchemaRegistry to hold all validation schemas in memory
@Component
public class SchemaRegistry {
    
    private static final Map<String, ValidationSchema> schemaCache = new ConcurrentHashMap<>();
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @PostConstruct
    public void init() {
        // Load all schemas from MongoDB at startup
        List<ValidationSchema> schemas = mongoTemplate.findAll(ValidationSchema.class);
        for (ValidationSchema schema : schemas) {
            schemaCache.put(schema.getSchemaName(), schema);
        }
    }
    
    /**
     * Get schema by name
     */
    public static ValidationSchema getSchema(String schemaName) {
        return schemaCache.get(schemaName);
    }
    
    /**
     * Get validation rule for a specific field in a schema
     */
    public static ValidationRule getValidationRule(String schemaName, String fieldName) {
        ValidationSchema schema = schemaCache.get(schemaName);
        return schema != null ? schema.getRules().get(fieldName) : null;
    }
    
    /**
     * Refresh a specific schema from the database
     */
    public void refreshSchema(String schemaName) {
        ValidationSchema schema = mongoTemplate.findOne(
                Query.query(Criteria.where("schemaName").is(schemaName)), 
                ValidationSchema.class);
        if (schema != null) {
            schemaCache.put(schemaName, schema);
        } else {
            schemaCache.remove(schemaName);
        }
    }
    
    /**
     * Refresh all schemas from the database
     */
    public void refreshAllSchemas() {
        schemaCache.clear();
        init();
    }
}

// Static validation utility class
public final class ValidationUtils {
    
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    
    private ValidationUtils() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Validate an object against a schema
     */
    public static <T> Set<ConstraintViolation<T>> validate(String schemaName, T object, Validator validator) {
        ValidationSchema schema = SchemaRegistry.getSchema(schemaName);
        if (schema == null) {
            return Collections.emptySet();
        }
        
        try {
            SchemaValidator.setValidationContext(schemaName, object);
            return validator.validate(object);
        } finally {
            SchemaValidator.clearValidationContext();
        }
    }
    
    /**
     * Validate specific fields of an object
     */
    public static <T> Set<ConstraintViolation<T>> validateFields(
            String schemaName, T object, Validator validator, String... fieldNames) {
        ValidationSchema schema = SchemaRegistry.getSchema(schemaName);
        if (schema == null) {
            return Collections.emptySet();
        }
        
        try {
            SchemaValidator.setValidationContext(schemaName, object);
            
            Set<ConstraintViolation<T>> violations = new HashSet<>();
            for (String fieldName : fieldNames) {
                violations.addAll(validator.validateProperty(object, fieldName));
            }
            
            return violations;
        } finally {
            SchemaValidator.clearValidationContext();
        }
    }
    
    /**
     * Validate and throw exception if validation fails
     */
    public static <T> void validateAndThrow(String schemaName, T object, Validator validator) {
        Set<ConstraintViolation<T>> violations = validate(schemaName, object, validator);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
    
    /**
     * Validate a single property directly
     */
    public static <T> Set<ConstraintViolation<T>> validateProperty(
            String schemaName, T object, String propertyName, Validator validator) {
        try {
            SchemaValidator.setValidationContext(schemaName, object);
            return validator.validateProperty(object, propertyName);
        } finally {
            SchemaValidator.clearValidationContext();
        }
    }
    
    /**
     * Perform manual validation of a specific field using a validation rule
     * This is useful for adhoc validation without annotations
     */
    public static boolean validateField(String schemaName, Object object, String fieldName, Object fieldValue) {
        ValidationRule rule = SchemaRegistry.getValidationRule(schemaName, fieldName);
        if (rule == null) {
            return true; // No rule, assume valid
        }
        
        // Check if required
        boolean isRequired = false;
        if (rule.isRequired()) {
            isRequired = true;
        } else if (rule.isConditional() && rule.getCondition() != null) {
            isRequired = evaluateCondition(rule.getCondition(), object);
        }
        
        if (isRequired && isEmpty(fieldValue)) {
            return false;
        }
        
        // Check type
        if (fieldValue != null && rule.getFieldType() != null) {
            return validateType(fieldValue, rule.getFieldType(), rule.getTypeValidationParams());
        }
        
        return true;
    }
    
    /**
     * Evaluate a SpEL condition with the given object as root
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
     * Check if an object is empty (null or empty string/collection/map/array)
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
     * Validate that a value is of the expected type
     */
    public static boolean validateType(Object value, ValidationRule.FieldType expectedType, 
                                Map<String, Object> validationParams) {
        if (value == null) {
            return true; // Null values are handled by the required check
        }
        
        switch (expectedType) {
            case STRING:
                return value instanceof String;
                
            case INT32:
                if (value instanceof Integer) {
                    return validateNumberConstraints(value, validationParams);
                }
                if (value instanceof Number) {
                    int intValue = ((Number) value).intValue();
                    boolean isIntegral = ((Number) value).doubleValue() == intValue;
                    return isIntegral && validateNumberConstraints(intValue, validationParams);
                }
                return false;
                
            case INT64:
                if (value instanceof Long) {
                    return validateNumberConstraints(value, validationParams);
                }
                if (value instanceof Number) {
                    long longValue = ((Number) value).longValue();
                    boolean isIntegral = ((Number) value).doubleValue() == longValue;
                    return isIntegral && validateNumberConstraints(longValue, validationParams);
                }
                return false;
                
            case DECIMAL:
                return value instanceof Number && validateNumberConstraints(value, validationParams);
                
            case BOOLEAN:
                return value instanceof Boolean;
                
            case LOCAL_DATE:
                return value instanceof LocalDate;
                
            case LOCAL_DATE_TIME:
                return value instanceof LocalDateTime;
                
            case ZONED_DATE_TIME:
                return value instanceof ZonedDateTime;
                
            case OBJECT:
                // Can be any non-primitive object
                return !(value instanceof Number || value instanceof String || 
                        value instanceof Boolean || value instanceof Collection || 
                        value instanceof Map || value.getClass().isArray());
                
            case ARRAY:
                return (value.getClass().isArray() || value instanceof Collection) && 
                        validateCollectionConstraints(value, validationParams);
                
            case MAP:
                return value instanceof Map && validateMapConstraints((Map<?,?>) value, validationParams);
                
            case EMAIL:
                if (!(value instanceof String)) {
                    return false;
                }
                String email = (String) value;
                boolean basicEmailValid = email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
                return basicEmailValid && validateStringConstraints(email, validationParams);
                
            case UUID:
                if (!(value instanceof String)) {
                    return false;
                }
                try {
                    UUID.fromString((String) value);
                    return validateStringConstraints((String) value, validationParams);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                
            case PHONE:
                if (!(value instanceof String)) {
                    return false;
                }
                String phone = (String) value;
                boolean phoneValid = phone.matches("^[0-9+\\-().\\s]+$");
                return phoneValid && validateStringConstraints(phone, validationParams);
                
            case URL:
                if (!(value instanceof String)) {
                    return false;
                }
                try {
                    new URL((String) value);
                    return validateStringConstraints((String) value, validationParams);
                } catch (MalformedURLException e) {
                    return false;
                }
                
            default:
                return true; // Unknown type, assume valid
        }
    }
    
    // Helper methods for type-specific validations
    
    private static boolean validateNumberConstraints(Object number, Map<String, Object> params) {
        if (params == null || params.isEmpty() || !(number instanceof Number)) {
            return true;
        }
        
        double value = ((Number) number).doubleValue();
        
        if (params.containsKey("min")) {
            double min = Double.parseDouble(params.get("min").toString());
            if (value < min) {
                return false;
            }
        }
        
        if (params.containsKey("max")) {
            double max = Double.parseDouble(params.get("max").toString());
            if (value > max) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean validateStringConstraints(String text, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return true;
        }
        
        if (params.containsKey("minLength")) {
            int minLength = Integer.parseInt(params.get("minLength").toString());
            if (text.length() < minLength) {
                return false;
            }
        }
        
        if (params.containsKey("maxLength")) {
            int maxLength = Integer.parseInt(params.get("maxLength").toString());
            if (text.length() > maxLength) {
                return false;
            }
        }
        
        if (params.containsKey("pattern")) {
            String pattern = params.get("pattern").toString();
            if (!text.matches(pattern)) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean validateCollectionConstraints(Object collection, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return true;
        }
        
        int size;
        if (collection.getClass().isArray()) {
            size = Array.getLength(collection);
        } else if (collection instanceof Collection) {
            size = ((Collection<?>) collection).size();
        } else {
            return false;
        }
        
        if (params.containsKey("minSize")) {
            int minSize = Integer.parseInt(params.get("minSize").toString());
            if (size < minSize) {
                return false;
            }
        }
        
        if (params.containsKey("maxSize")) {
            int maxSize = Integer.parseInt(params.get("maxSize").toString());
            if (size > maxSize) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean validateMapConstraints(Map<?,?> map, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return true;
        }
        
        if (params.containsKey("minSize")) {
            int minSize = Integer.parseInt(params.get("minSize").toString());
            if (map.size() < minSize) {
                return false;
            }
        }
        
        if (params.containsKey("maxSize")) {
            int maxSize = Integer.parseInt(params.get("maxSize").toString());
            if (map.size() > maxSize) {
                return false;
            }
        }
        
        return true;
    }
}

// Example use in a controller
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private Validator validator;
    
    @PostMapping("/{schemaName}")
    public ResponseEntity<String> createUser(
            @PathVariable String schemaName,
            @RequestBody UserRequest userRequest) {
        
        try {
            // Set validation context
            SchemaValidator.setValidationContext(schemaName, userRequest);
            
            // Validate
            Set<ConstraintViolation<UserRequest>> violations = validator.validate(userRequest);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            
            // Process the validated request
            return ResponseEntity.ok("User created successfully");
        } finally {
            // Clear validation context
            SchemaValidator.clearValidationContext();
        }
    }
    
    // Example of validating only specific fields
    @PatchMapping("/{schemaName}/{id}")
    public ResponseEntity<String> updateUser(
            @PathVariable String schemaName,
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        
        // Fetch existing user
        UserRequest existingUser = getUserById(id);
        if (existingUser == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Apply updates to the user
        applyUpdates(existingUser, updates);
        
        // Validate only the updated fields
        try {
            SchemaValidator.setValidationContext(schemaName, existingUser);
            
            Set<ConstraintViolation<UserRequest>> violations = new HashSet<>();
            for (String fieldName : updates.keySet()) {
                violations.addAll(validator.validateProperty(existingUser, fieldName));
            }
            
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            
            // Save the updated user
            return ResponseEntity.ok("User updated successfully");
        } finally {
            SchemaValidator.clearValidationContext();
        }
    }
    
    // Example of using ValidationUtils directly in a service
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateRequest(
            @RequestParam String schemaName,
            @RequestBody UserRequest userRequest) {
        
        Map<String, Object> result = new HashMap<>();
        
        // Use ValidationUtils directly
        try {
            Set<ConstraintViolation<UserRequest>> violations = 
                ValidationUtils.validate(schemaName, userRequest, validator);
            
            if (violations.isEmpty()) {
                result.put("valid", true);
            } else {
                result.put("valid", false);
                
                Map<String, String> errors = new HashMap<>();
                violations.forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
                result.put("errors", errors);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "Validation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    // Helper method to fetch a user by ID (implementation would depend on your repository)
    private UserRequest getUserById(String id) {
        // Example implementation
        return null; // Replace with actual implementation
    }
    
    // Helper method to apply updates to a user object
    private void applyUpdates(UserRequest user, Map<String, Object> updates) {
        // Example implementation
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(path, message);
        });
        return ResponseEntity.badRequest().body(errors);
    }
}
//     @PostMapping("/{schemaName}")
//     public ResponseEntity<String> createUser(
//             @PathVariable String schemaName,
//             @RequestBody UserRequest userRequest) {
        
//         // Validate with service
//         validationService.validateAndThrow(schemaName, userRequest);
        
//         // Process the validated request
//         return ResponseEntity.ok("User created successfully");
//     }
    
//     @ExceptionHandler(ConstraintViolationException.class)
//     public ResponseEntity<Map<String, String>> handleValidationExceptions(ConstraintViolationException ex) {
//         Map<String, String> errors = new HashMap<>();
//         ex.getConstraintViolations().forEach(violation -> {
//             String path = violation.getPropertyPath().toString();
//             String message = violation.getMessage();
//             errors.put(path, message);
//         });
//         return ResponseEntity.badRequest().body(errors);
//     }
// }

// Below is an example of how the MongoDB schema document would look in JSON format:

/**
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
    "age": {
      "requirementType": "REQUIRED",
      "fieldType": "INT32",
      "condition": null,
      "errorMessage": "Age must be a valid integer value",
      "typeValidationParams": {
        "min": 0,
        "max": 120
      }
    },
    "requestedDate": {
      "requirementType": "REQUIRED",
      "fieldType": "LOCAL_DATE_TIME",
      "condition": null,
      "errorMessage": "Requested date is required and must be a valid date/time",
      "typeValidationParams": {}
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

// Example of manually using the validation without annotations in a service class
public class UserService {
    
    @Autowired
    private Validator validator;
    
    // Non-annotated validation example
    public boolean manualValidation(UserRequest request) {
        // Using the utility methods
        Set<ConstraintViolation<UserRequest>> violations = 
            ValidationUtils.validate("user_request_validation", request, validator);
        
        return violations.isEmpty();
    }
    
    // Validate a subset of fields example
    public boolean validateSpecificFields(UserRequest request) {
        Set<ConstraintViolation<UserRequest>> violations = 
            ValidationUtils.validateFields("user_request_validation", request, validator, 
                "country", "age", "requestedDate");
                
        return violations.isEmpty();
    }
    
    // Completely manual validation without annotations
    public boolean adhocValidation(Map<String, Object> data) {
        String schemaName = "user_request_validation";
        
        // Validate country
        String country = (String) data.get("country");
        if (!ValidationUtils.validateField(schemaName, data, "country", country)) {
            return false;
        }
        
        // Validate age
        Integer age = (Integer) data.get("age");
        if (!ValidationUtils.validateField(schemaName, data, "age", age)) {
            return false;
        }
        
        // Build address data structure for nested validation
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            // Check address fields conditionally
            String addressLine1 = (String) address.get("line1");
            if (!ValidationUtils.validateField(schemaName, data, "address.line1", addressLine1)) {
                return false;
            }
        }
        
        return true;
    }
}
