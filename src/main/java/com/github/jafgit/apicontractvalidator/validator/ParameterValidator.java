package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ParameterValidator {

    private static final Logger LOG = Logger.getInstance(ParameterValidator.class);

    public static List<ValidationError> validate(@NotNull OpenAPI openApi, @NotNull Operation operation, @NotNull PsiMethod method) {
        LOG.warn("[ParameterValidator] Pure validate() called for method: " + method.getName());
        List<ValidationError> errors = new ArrayList<>();

        for (PsiParameter psiParameter : method.getParameterList().getParameters()) {
            for (PsiAnnotation annotation : psiParameter.getAnnotations()) {
                String annotationName = annotation.getQualifiedName();
                if (annotationName != null) {
                    if (annotationName.endsWith("PathVariable")) {
                        validateSimpleParameter(operation, psiParameter, "path").ifPresent(errors::add);
                    } else if (annotationName.endsWith("RequestParam")) {
                        validateSimpleParameter(operation, psiParameter, "query").ifPresent(errors::add);
                    }
                }
            }
        }

        errors.addAll(RequestBodyValidator.validate(openApi, operation, method));

        return errors;
    }

    public static void validate(@NotNull OpenAPI openApi, @NotNull Operation operation, @NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        LOG.warn("[ParameterValidator] Inspection validate() called for method: " + method.getName());
        List<ValidationError> errors = validate(openApi, operation, method);
        for (ValidationError error : errors) {
            holder.registerProblem(
                    error.getElement(),
                    "API Drift Detected: " + error.getMessage(),
                    ProblemHighlightType.WARNING
            );
        }
    }

    private static java.util.Optional<ValidationError> validateSimpleParameter(@NotNull Operation operation, @NotNull PsiParameter psiParameter, @NotNull String in) {
        String paramName = psiParameter.getName();
        List<Parameter> specParams = operation.getParameters();
        if (specParams == null) {
            return java.util.Optional.of(new ValidationError("Parameter '" + paramName + "' is not defined in the contract (no parameters found).", psiParameter));
        }

        java.util.Optional<Parameter> specParamOpt = specParams.stream()
                .filter(p -> p.getName().equals(paramName) && p.getIn().equals(in))
                .findFirst();

        if (specParamOpt.isEmpty()) {
            return java.util.Optional.of(new ValidationError(in + " parameter '" + paramName + "' is not defined in the contract.", psiParameter));
        } else {
            Parameter specParam = specParamOpt.get();
            String javaType = psiParameter.getType().getPresentableText().toLowerCase();
            String specType = specParam.getSchema().getType();
            if (!isTypeMatch(javaType, specType)) {
                return java.util.Optional.of(new ValidationError("Type mismatch for parameter '" + paramName + "'. Expected '" + specType + "', found '" + javaType + "'.", psiParameter.getTypeElement()));
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean isTypeMatch(String javaType, String specType) {
        if (specType == null) return true;
        switch (specType) {
            case "string":
                return javaType.equals("string");
            case "integer":
                return javaType.equals("int") || javaType.equals("long") || javaType.equals("integer");
            case "number":
                return javaType.equals("double") || javaType.equals("float") || javaType.equals("bigdecimal");
            case "boolean":
                return javaType.equals("boolean");
            default:
                return true;
        }
    }
}
