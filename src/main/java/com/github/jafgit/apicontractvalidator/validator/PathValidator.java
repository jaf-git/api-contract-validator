package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import io.swagger.v3.oas.models.OpenAPI;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class PathValidator {

    private static final Logger LOG = Logger.getInstance(PathValidator.class);

    /**
     * Validates a path against the OpenAPI spec. This method is pure logic and decoupled from the inspection framework.
     *
     * @param openApi The OpenAPI specification.
     * @param path    The path to validate.
     * @return An Optional containing an error message if validation fails, otherwise empty.
     */
    public static Optional<String> validate(@NotNull OpenAPI openApi, @NotNull String path) {
        LOG.warn("[PathValidator] Pure validate() called for path: '" + path + "'");
        if (openApi.getPaths() == null || !openApi.getPaths().containsKey(path)) {
            String errorMessage = "Path '" + path + "' is missing from the contract.";
            LOG.warn("[PathValidator] DRIFT DETECTED: " + errorMessage);
            return Optional.of(errorMessage);
        }
        LOG.warn("[PathValidator] SUCCESS: Path '" + path + "' is valid.");
        return Optional.empty();
    }

    /**
     * Validates a path and registers a problem with the ProblemsHolder if validation fails.
     * This method is used by the inspection framework.
     *
     * @param openApi            The OpenAPI specification.
     * @param path               The path to validate.
     * @param elementToHighlight The PSI element to highlight if there's an error.
     * @param holder             The ProblemsHolder to register the problem with.
     */
    public static void validate(@NotNull OpenAPI openApi, @NotNull String path, @NotNull PsiElement elementToHighlight, @NotNull ProblemsHolder holder) {
        validate(openApi, path).ifPresent(errorMessage ->
                holder.registerProblem(
                        elementToHighlight,
                        "API Drift Detected: " + errorMessage,
                        ProblemHighlightType.WARNING
                )
        );
    }
}
