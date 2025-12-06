package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import io.swagger.v3.oas.models.PathItem;
import org.jetbrains.annotations.NotNull;

public class MethodValidator {

    private static final Logger LOG = Logger.getInstance(MethodValidator.class);

    public static void validate(@NotNull String httpMethod, @NotNull PathItem pathItem, @NotNull PsiElement elementToHighlight, @NotNull ProblemsHolder holder) {
        LOG.warn("** [METHOD VALIDATOR] ** validate() has been called.");
        if (!isHttpMethodInSpec(pathItem, httpMethod)) {
            LOG.warn("** [METHOD VALIDATOR] ** DRIFT DETECTED: HTTP method '" + httpMethod.toUpperCase() + "' is not defined for this path.");
            holder.registerProblem(
                    elementToHighlight,
                    "API Drift Detected: HTTP method '" + httpMethod.toUpperCase() + "' is not defined for this path in the contract.",
                    ProblemHighlightType.WARNING
            );
        } else {
            LOG.warn("** [METHOD VALIDATOR] ** SUCCESS: HTTP method '" + httpMethod.toUpperCase() + "' is valid for this path.");
        }
    }

    private static boolean isHttpMethodInSpec(@NotNull PathItem pathItem, @NotNull String httpMethod) {
        LOG.warn("** [METHOD VALIDATOR] ** isHttpMethodInSpec has been called() with: " + httpMethod.toLowerCase());
        switch (httpMethod.toLowerCase()) {
            case "get":
                return pathItem.getGet() != null;
            case "post":
                LOG.warn("*** pathItem: " + pathItem.getPost());
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
