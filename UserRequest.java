package com.example.model;

import com.example.validation.ValidateFields;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@ValidateFields({
    // Scenario 1: If userType is PREMIUM or BUSINESS, taxId and companyName are required
    @ValidateFields.FieldValidation(
        field = "taxId",
        type = ValidateFields.ValidationType.DEPENDS_ON_VALUE,
        dependsOnField = "userType",
        triggerValues = {"PREMIUM", "BUSINESS"},
        message = "Tax ID is required for Premium and Business users"
    ),
    @ValidateFields.FieldValidation(
        field = "companyName",
        type = ValidateFields.ValidationType.DEPENDS_ON_VALUE,
        dependsOnField = "userType",
        triggerValues = {"PREMIUM", "BUSINESS"},
        message = "Company Name is required for Premium and Business users"
    ),
    
    // Scenario 2: If shippingAddress is provided, shippingCity and shippingZipCode are required
    @ValidateFields.FieldValidation(
        field = "shippingCity",
        type = ValidateFields.ValidationType.DEPENDS_ON_PRESENCE,
        dependsOnPresenceOf = "shippingAddress",
        message = "City is required when Shipping Address is provided"
    ),
    @ValidateFields.FieldValidation(
        field = "shippingZipCode",
        type = ValidateFields.ValidationType.DEPENDS_ON_PRESENCE,
        dependsOnPresenceOf = "shippingAddress",
        message = "Zip Code is required when Shipping Address is provided"
    ),
    
    // Scenario 3: If any payment field is filled, all must be filled
    @ValidateFields.FieldValidation(
        field = "creditCardNumber",
        type = ValidateFields.ValidationType.DEPENDS_ON_GROUP,
        groupFields = {"expiryDate", "cvv"},
        message = "All payment fields must be filled if credit card number is provided"
    ),
    @ValidateFields.FieldValidation(
        field = "expiryDate",
        type = ValidateFields.ValidationType.DEPENDS_ON_GROUP,
        groupFields = {"creditCardNumber", "cvv"},
        message = "All payment fields must be filled if expiry date is provided"
    ),
    @ValidateFields.FieldValidation(
        field = "cvv",
        type = ValidateFields.ValidationType.DEPENDS_ON_GROUP,
        groupFields = {"creditCardNumber", "expiryDate"},
        message = "All payment fields must be filled if CVV is provided"
    )
})
public class UserRequest {
    @NotNull
    private String username;
    
    private String userType;  // Scenario 1: If value is "PREMIUM" or "BUSINESS", taxId and companyName are required
    private String taxId;
    private String companyName;
    
    private String shippingAddress;  // Scenario 2: If populated, shippingCity and shippingZipCode are required
    private String shippingCity;
    private String shippingZipCode;
    
    // Scenario 3: If any of these three is populated, all must be populated
    private String creditCardNumber;
    private String expiryDate;
    private String cvv;
}