package com.github.jafgit.apicontractvalidator.validator;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * A data class to hold a validation error message and the specific PSI element
 * that should be highlighted for the error.
 */
public class ValidationError {
    private final String message;
    private final PsiElement element;

    public ValidationError(@NotNull String message, @NotNull PsiElement element) {
        this.message = message;
        this.element = element;
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    @NotNull
    public PsiElement getElement() {
        return element;
    }
}
