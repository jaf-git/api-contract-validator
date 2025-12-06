package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PathValidator {

    /**
     * Validates if a path exists in the OpenAPI spec.
     * @return The PathItem if valid, otherwise null.
     */
    @Nullable
    public static PathItem validate(@NotNull OpenAPI openApi, @NotNull String path, @NotNull PsiElement elementToHighlight, @NotNull ProblemsHolder holder) {
        PathItem pathItem = openApi.getPaths() != null ? openApi.getPaths().get(path) : null;

        if (pathItem == null) {
            holder.registerProblem(
                    elementToHighlight,
                    "API Drift Detected: Path '" + path + "' is missing from openapi.yaml contract.",
                    ProblemHighlightType.WARNING
            );
            return null;
        }
        return pathItem;
    }
}
