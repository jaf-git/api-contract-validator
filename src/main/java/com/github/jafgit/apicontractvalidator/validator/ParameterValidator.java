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

import java.util.List;
import java.util.Optional;

public class ParameterValidator {

    private static final Logger LOG = Logger.getInstance(ParameterValidator.class);

    public static void validate(@NotNull OpenAPI openApi, @NotNull Operation operation, @NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        LOG.warn("[ParameterValidator] validate() called for method: " + method.getName());

        for (PsiParameter psiParameter : method.getParameterList().getParameters()) {
            for (PsiAnnotation annotation : psiParameter.getAnnotations()) {
                String annotationName = annotation.getQualifiedName();
                if (annotationName != null) {
                    if (annotationName.endsWith("PathVariable")) {
                        validateParameter(operation, psiParameter, "path", holder);
                    } else if (annotationName.endsWith("RequestParam")) {
                        validateParameter(operation, psiParameter, "query", holder);
                    }
                }
            }
        }
    }

    private static void validateParameter(@NotNull Operation operation, @NotNull PsiParameter psiParameter, @NotNull String in, @NotNull ProblemsHolder holder) {
        String paramName = psiParameter.getName();
        List<Parameter> specParams = operation.getParameters();
        if (specParams == null) {
            holder.registerProblem(psiParameter, "API Drift Detected: Parameter '" + paramName + "' is not defined in the contract.", ProblemHighlightType.WARNING);
            return;
        }

        Optional<Parameter> specParamOpt = specParams.stream()
                .filter(p -> p.getName().equals(paramName) && p.getIn().equals(in))
                .findFirst();

        if (specParamOpt.isEmpty()) {
            holder.registerProblem(psiParameter, "API Drift Detected: " + in + " parameter '" + paramName + "' is not defined in the contract.", ProblemHighlightType.WARNING);
        } else {
            Parameter specParam = specParamOpt.get();
            // Basic type check
            String javaType = psiParameter.getType().getPresentableText().toLowerCase();
            String specType = specParam.getSchema().getType();
            if (!isTypeMatch(javaType, specType)) {
                holder.registerProblem(psiParameter, "API Drift Detected: Type mismatch for parameter '" + paramName + "'. Expected '" + specType + "', found '" + javaType + "'.", ProblemHighlightType.WARNING);
            }
        }
    }

    private static boolean isTypeMatch(String javaType, String specType) {
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
                return true; // Assume complex types match for now
        }
    }
}
