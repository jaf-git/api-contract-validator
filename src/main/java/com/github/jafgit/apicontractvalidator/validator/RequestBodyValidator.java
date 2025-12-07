package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequestBodyValidator {

    private static final String REQUEST_BODY_ANNOTATION = "org.springframework.web.bind.annotation.RequestBody";

    public static List<ValidationError> validate(@NotNull OpenAPI openApi, @NotNull Operation operation, @NotNull PsiMethod method) {
        Optional<PsiParameter> requestBodyParamOpt = findRequestBodyParameter(method);
        if (requestBodyParamOpt.isEmpty()) {
            return Collections.emptyList();
        }

        PsiParameter requestBodyParam = requestBodyParamOpt.get();
        List<ValidationError> errors = new ArrayList<>();

        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(requestBodyParam.getType());
        if (psiClass == null) {
            return Collections.emptyList();
        }

        Schema<?> schema = getRequestSchema(openApi, operation);
        if (schema == null) {
            return Collections.emptyList();
        }

        if (schema.get$ref() != null) {
            schema = resolveSchemaRef(openApi, schema);
            if (schema == null) {
                errors.add(new ValidationError("Could not resolve schema reference for @RequestBody.", requestBodyParam));
                return errors;
            }
        }

        if (schema.getRequired() != null) {
            for (String requiredFieldName : schema.getRequired()) {
                if (psiClass.findFieldByName(requiredFieldName, true) == null) {
                    errors.add(new ValidationError("RequestBody DTO '" + psiClass.getName() + "' is missing required field '" + requiredFieldName + "'.", requestBodyParam));
                }
            }
        }

        Map<String, Schema> schemaProperties = schema.getProperties();
        if (schemaProperties != null) {
            for (Map.Entry<String, Schema> entry : schemaProperties.entrySet()) {
                String propName = entry.getKey();
                Schema propSchema = entry.getValue();
                PsiField field = psiClass.findFieldByName(propName, true);

                if (field != null) {
                    String javaType = field.getType().getPresentableText().toLowerCase();
                    String specType = propSchema.getType();
                    if (!isTypeMatch(javaType, specType)) {
                        errors.add(new ValidationError("Type mismatch for field '" + propName + "'. Contract expects '" + specType + "', but found '" + javaType + "'.", field));
                    }
                }
            }
        }

        return errors;
    }

    private static Optional<PsiParameter> findRequestBodyParameter(PsiMethod method) {
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (parameter.hasAnnotation(REQUEST_BODY_ANNOTATION)) {
                return Optional.of(parameter);
            }
        }
        return Optional.empty();
    }

    @Nullable
    private static Schema<?> getRequestSchema(OpenAPI openApi, Operation operation) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null) return null;

        if (requestBody.get$ref() != null) {
            requestBody = resolveRequestBodyRef(openApi, requestBody);
            if (requestBody == null) return null;
        }

        return requestBody.getContent().values().stream()
                .findFirst()
                .map(mediaType -> mediaType.getSchema())
                .orElse(null);
    }

    @Nullable
    private static RequestBody resolveRequestBodyRef(OpenAPI openApi, RequestBody requestBody) {
        if (requestBody.get$ref() == null) return requestBody;
        String ref = requestBody.get$ref();
        return openApi.getComponents().getRequestBodies().get(ref.substring(ref.lastIndexOf('/') + 1));
    }

    @Nullable
    private static Schema<?> resolveSchemaRef(OpenAPI openApi, Schema<?> schema) {
        if (schema.get$ref() == null) return schema;
        String ref = schema.get$ref();
        return openApi.getComponents().getSchemas().get(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static boolean isTypeMatch(String javaType, String specType) {
        if (specType == null) return true;
        switch (specType) {
            case "string":
                return javaType.equals("string") || javaType.equals("uuid");
            case "integer":
                return javaType.equals("int") || javaType.equals("integer") || javaType.equals("long");
            case "number":
                return javaType.equals("double") || javaType.equals("float") || javaType.equals("bigdecimal");
            case "boolean":
                return javaType.equals("boolean");
            case "array":
                return javaType.contains("[]") || javaType.startsWith("list") || javaType.startsWith("set");
            default:
                return true;
        }
    }
}
