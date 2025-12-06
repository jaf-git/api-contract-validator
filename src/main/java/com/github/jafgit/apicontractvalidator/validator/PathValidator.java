package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import io.swagger.v3.oas.models.OpenAPI;
import org.jetbrains.annotations.NotNull;

public class PathValidator {

    private static final Logger LOG = Logger.getInstance(PathValidator.class);

    public static void validate(@NotNull OpenAPI openApi, @NotNull String path, @NotNull PsiElement elementToHighlight, @NotNull ProblemsHolder holder) {
        LOG.warn("[PathValidator] validate() called for path: '" + path + "'");
        if (openApi.getPaths() == null || !openApi.getPaths().containsKey(path)) {
            LOG.warn("[PathValidator] DRIFT DETECTED: Path '" + path + "' is missing from the contract.");
            holder.registerProblem(
                    elementToHighlight,
                    "API Drift Detected: Path '" + path + "' is missing from openapi.yaml contract.",
                    ProblemHighlightType.WARNING
            );
        } else {
            LOG.warn("[PathValidator] SUCCESS: Path '" + path + "' is valid.");
        }
    }
}
