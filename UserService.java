package com.example.service;

import com.example.model.UserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class UserService {
    private final Validator validator;
    
    public UserService() {
        // Create validator instance that will be used to programmatically validate objects
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }
    
    /**
     * Validates the user request and returns validation errors
     * @param request The request to validate
     * @return Map of field names to error messages, empty if validation passes
     */
    public Map<String, String> validateUserRequest(UserRequest request) {
        Set<ConstraintViolation<UserRequest>> violations = validator.validate(request);
        
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<UserRequest> violation : violations) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(propertyPath, message);
        }
        
        return errors;
    }
    
    /**
     * Process the user request
     * @param request The request to process
     * @return true if processed successfully, false if validation fails
     */
    public boolean processUserRequest(UserRequest request) {
        // Validate the request
        Map<String, String> errors = validateUserRequest(request);
        
        if (!errors.isEmpty()) {
            // Handle validation errors
            System.out.println("Validation errors: " + errors);
            return false;
        }
        
        // Process the valid request
        // ... business logic here ...
        
        return true;
    }
    
    /**
     * Example of programmatic validation usage
     */
    public static void main(String[] args) {
        UserService service = new UserService();
        
        // Example 1: Invalid case - PREMIUM user without required fields
        UserRequest request1 = new UserRequest();
        request1.setUsername("john");
        request1.setUserType("PREMIUM");
        // Missing taxId and companyName
        
        System.out.println("Example 1 validation result: " + service.validateUserRequest(request1));
        
        // Example 2: Valid case
        UserRequest request2 = new UserRequest();
        request2.setUsername("john");
        request2.setUserType("PREMIUM");
        request2.setTaxId("123456789");
        request2.setCompanyName("ACME Inc");
        
        System.out.println("Example 2 validation result: " + service.validateUserRequest(request2));
        
        // Example 3: Using the validator to process a request
        boolean result = service.processUserRequest(request2);
        System.out.println("Processing result: " + result);
    }
}