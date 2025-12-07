package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ParameterValidator {

    private static final Logger LOG = Logger.getInstance(ParameterValidator.class);

    /**
     * Validates the parameters of a method against the OpenAPI operation. This is pure logic.
     *
     * @param operation The OpenAPI Operation to validate against.
     * @param method    The PSI method whose parameters are being validated.
     * @return A list of error messages for any validation failures. An empty list means success.
     */
    public static List<String> validate(@NotNull Operation operation, @NotNull PsiMethod method) {
        LOG.warn("[ParameterValidator] Pure validate() called for method: " + method.getName());
        List<String> errors = new ArrayList<>();

        for (PsiParameter psiParameter : method.getParameterList().getParameters()) {
            for (PsiAnnotation annotation : psiParameter.getAnnotations()) {
                String annotationName = annotation.getQualifiedName();
                if (annotationName != null) {
                    if (annotationName.endsWith("PathVariable")) {
                        validateParameter(operation, psiParameter, "path").ifPresent(errors::add);
                    } else if (annotationName.endsWith("RequestParam")) {
                        validateParameter(operation, psiParameter, "query").ifPresent(errors::add);
                    }
                }
            }
        }
        return errors;
    }

    /**
     * Validates method parameters and registers problems if validation fails. Used by inspections.
     *
     * @param operation The OpenAPI Operation.
     * @param method    The PSI method.
     * @param holder    The ProblemsHolder to register problems with.
     */
    public static void validate(@NotNull Operation operation, @NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        LOG.warn("[ParameterValidator] Inspection validate() called for method: " + method.getName());
        // This implementation is simplified. A real implementation would need to map errors back to the specific PsiParameter element.
        // For now, we'll highlight the method name for any parameter error.
        List<String> errors = validate(operation, method);
        for (String error : errors) {
            holder.registerProblem(
                    method.getNameIdentifier() != null ? method.getNameIdentifier() : method,
                    "API Drift Detected: " + error,
                    ProblemHighlightType.WARNING
            );
        }
    }

    private static Optional<String> validateParameter(@NotNull Operation operation, @NotNull PsiParameter psiParameter, @NotNull String in) {
        String paramName = psiParameter.getName();
        List<Parameter> specParams = operation.getParameters();
        if (specParams == null) {
            return Optional.of("Parameter '" + paramName + "' is not defined in the contract (no parameters found).");
        }

        Optional<Parameter> specParamOpt = specParams.stream()
                .filter(p -> p.getName().equals(paramName) && p.getIn().equals(in))
                .findFirst();

        if (specParamOpt.isEmpty()) {
            return Optional.of(in + " parameter '" + paramName + "' is not defined in the contract.");
        } else {
            Parameter specParam = specParamOpt.get();
            String javaType = psiParameter.getType().getPresentableText().toLowerCase();
            String specType = specParam.getSchema().getType();
            if (!isTypeMatch(javaType, specType)) {
                return Optional.of("Type mismatch for parameter '" + paramName + "'. Expected '" + specType + "', found '" + javaType + "'.");
            }
        }
        return Optional.empty();
    }

    private static boolean isTypeMatch(String javaType, String specType) {
        if (specType == null) return true; // No type defined in spec, so we can't validate it.
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
