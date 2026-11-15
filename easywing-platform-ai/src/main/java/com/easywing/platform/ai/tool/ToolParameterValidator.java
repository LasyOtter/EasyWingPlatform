package com.easywing.platform.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具参数验证器 - 使用 JSON Schema 验证工具参数
 */
@Slf4j
@Component
public class ToolParameterValidator {

    private final JsonSchemaFactory schemaFactory;
    private final ObjectMapper objectMapper;

    public ToolParameterValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    /**
     * 验证工具参数
     */
    public ValidationResult validate(ToolDefinition toolDef, Map<String, Object> arguments) {
        if (toolDef.getParametersSchema() == null) {
            log.debug("Tool {} has no schema, skipping validation", toolDef.getName());
            return ValidationResult.valid();
        }

        try {
            JsonSchema schema = schemaFactory.getSchema(toolDef.getParametersSchema());
            JsonNode argsNode = objectMapper.valueToTree(arguments);

            Set<ValidationMessage> errors = schema.validate(argsNode);

            if (errors.isEmpty()) {
                log.debug("Tool {} parameters validated successfully", toolDef.getName());
                return ValidationResult.valid();
            } else {
                String errorMsg = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                log.warn("Tool {} parameter validation failed: {}", toolDef.getName(), errorMsg);
                return ValidationResult.invalid(errorMsg, errors);
            }
        } catch (Exception e) {
            log.error("Failed to validate tool parameters", e);
            return ValidationResult.invalid("Schema validation error: " + e.getMessage(), null);
        }
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Set<ValidationMessage> validationMessages;

        private ValidationResult(boolean valid, String errorMessage, Set<ValidationMessage> validationMessages) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.validationMessages = validationMessages;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult invalid(String errorMessage, Set<ValidationMessage> messages) {
            return new ValidationResult(false, errorMessage, messages);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Set<ValidationMessage> getValidationMessages() {
            return validationMessages;
        }
    }
}
