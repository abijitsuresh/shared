import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SchemaBasedTransformer {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator;

    public SchemaBasedTransformer() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    public Map<String, Object> transformData(Map<String, Object> inputData, SchemaDefinition schema) {
        return transformObject(inputData, schema.getProperties());
    }

    private Map<String, Object> transformObject(Map<String, Object> inputData, Map<String, Property> schemaProperties) {
        Map<String, Object> transformed = new HashMap<>();

        for (Map.Entry<String, Property> entry : schemaProperties.entrySet()) {
            String key = entry.getKey();
            Property property = entry.getValue();
            
            Object value = extractValue(inputData, property);
            if (value != null) {
                transformed.put(key, value);
                
                if (property.getFieldMapping() != null) {
                    setNestedValue(transformed, property.getFieldMapping(), value);
                }
            }
        }
        return transformed;
    }

    private Object extractValue(Map<String, Object> inputData, Property property) {
        Object value = inputData.get(property.getName());
        if (value == null) return null;
        
        return switch (property.getType()) {
            case "String" -> value.toString();
            case "Int32" -> Integer.parseInt(value.toString());
            case "LocalDateTime" -> objectMapper.convertValue(value, java.time.LocalDateTime.class);
            case "array" -> transformArray((List<?>) value, property);
            case "object" -> transformObject((Map<String, Object>) value, property.getProperties());
            default -> value;
        };
    }
    
    private List<Object> transformArray(List<?> list, Property property) {
        return list.stream()
                .map(item -> property.getValueType().equals("object") ? transformObject((Map<String, Object>) item, property.getProperties()) : item)
                .collect(Collectors.toList());
    }
    
    private void setNestedValue(Map<String, Object> transformed, String fieldMapping, Object value) {
        String[] keys = fieldMapping.replace("$.", "").split("\\.");
        Map<String, Object> current = transformed;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        current.put(keys[keys.length - 1], value);
    }
}
