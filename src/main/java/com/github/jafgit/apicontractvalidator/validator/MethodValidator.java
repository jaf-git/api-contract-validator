package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class MethodValidator {

    private static final Logger LOG = Logger.getInstance(MethodValidator.class);

    /**
     * Validates an HTTP method against the OpenAPI spec for a given path. This is pure logic.
     *
     * @param openApi    The OpenAPI specification.
     * @param path       The path of the endpoint.
     * @param httpMethod The HTTP method to validate.
     * @return An Optional containing an error message if validation fails, otherwise empty.
     */
    public static Optional<String> validate(@NotNull OpenAPI openApi, @NotNull String path, @NotNull String httpMethod) {
        LOG.warn("[MethodValidator] Pure validate() called for path: '" + path + "', method: '" + httpMethod + "'");
        PathItem pathItem = openApi.getPaths() != null ? openApi.getPaths().get(path) : null;
        if (pathItem == null) {
            // This case is handled by PathValidator, so we don't return an error here.
            LOG.warn("[MethodValidator] Path '" + path + "' not found in spec. Skipping method validation.");
            return Optional.empty();
        }

        if (!isHttpMethodInSpec(pathItem, httpMethod)) {
            String errorMessage = "HTTP method '" + httpMethod.toUpperCase() + "' is not defined for this path in the contract.";
            LOG.warn("[MethodValidator] DRIFT DETECTED: " + errorMessage);
            return Optional.of(errorMessage);
        }

        LOG.warn("[MethodValidator] SUCCESS: HTTP method '" + httpMethod.toUpperCase() + "' is valid for this path.");
        return Optional.empty();
    }

    /**
     * Validates an HTTP method and registers a problem if validation fails. Used by inspections.
     *
     * @param openApi            The OpenAPI specification.
     * @param path               The path of the endpoint.
     * @param httpMethod         The HTTP method to validate.
     * @param elementToHighlight The PSI element to highlight.
     * @param holder             The ProblemsHolder to register the problem with.
     */
    public static void validate(@NotNull OpenAPI openApi, @NotNull String path, @NotNull String httpMethod, @NotNull PsiElement elementToHighlight, @NotNull ProblemsHolder holder) {
        validate(openApi, path, httpMethod).ifPresent(errorMessage ->
                holder.registerProblem(
                        elementToHighlight,
                        "API Drift Detected: " + errorMessage,
                        ProblemHighlightType.WARNING
                )
        );
    }

    private static boolean isHttpMethodInSpec(@NotNull PathItem pathItem, @NotNull String httpMethod) {
        switch (httpMethod.toLowerCase()) {
            case "get":
                return pathItem.getGet() != null;
            case "post":
                return pathItem.getPost() != null;
            case "put":
                return pathItem.getPut() != null;
            case "delete":
                return pathItem.getDelete() != null;
            case "patch":
                return pathItem.getPatch() != null;
            default:
                return false;
        }
    }
}
