package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.jetbrains.annotations.NotNull;

public class MethodValidator {

    private static final Logger LOG = Logger.getInstance(MethodValidator.class);

    public static void validate(@NotNull OpenAPI openApi, @NotNull String path, @NotNull String httpMethod, @NotNull PsiElement elementToHighlight, @NotNull ProblemsHolder holder) {
        LOG.warn("[MethodValidator] validate() called for path: '" + path + "', method: '" + httpMethod + "'");
        PathItem pathItem = openApi.getPaths() != null ? openApi.getPaths().get(path) : null;
        if (pathItem == null) {
            LOG.warn("[MethodValidator] Path '" + path + "' not found in spec. Skipping method validation.");
            return;
        }

        if (!isHttpMethodInSpec(pathItem, httpMethod)) {
            LOG.warn("[MethodValidator] DRIFT DETECTED: HTTP method '" + httpMethod.toUpperCase() + "' is not defined for this path.");
            holder.registerProblem(
                    elementToHighlight,
                    "API Drift Detected: HTTP method '" + httpMethod.toUpperCase() + "' is not defined for this path in the contract.",
                    ProblemHighlightType.WARNING
            );
        } else {
            LOG.warn("[MethodValidator] SUCCESS: HTTP method '" + httpMethod.toUpperCase() + "' is valid for this path.");
        }
    }

    private static boolean isHttpMethodInSpec(@NotNull PathItem pathItem, @NotNull String httpMethod) {
        LOG.warn("[MethodValidator] isHttpMethodInSpec() called with: " + httpMethod.toLowerCase());
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
