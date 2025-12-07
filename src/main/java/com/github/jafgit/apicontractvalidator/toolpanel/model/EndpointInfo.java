package com.github.jafgit.apicontractvalidator.toolpanel.model;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

public class EndpointInfo {
    private final String path;
    private final String method;
    private EndpointStatus status;
    private final String tag;
    private final SmartPsiElementPointer<PsiMethod> methodPointer;

    public EndpointInfo(String path, String method, EndpointStatus status, String tag, @Nullable SmartPsiElementPointer<PsiMethod> methodPointer) {
        this.path = path;
        this.method = method;
        this.status = status;
        this.tag = tag;
        this.methodPointer = methodPointer;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public String getTag() {
        return tag;
    }

    @Nullable
    public SmartPsiElementPointer<PsiMethod> getMethodPointer() {
        return methodPointer;
    }

    @Override
    public String toString() {
        return method + " " + path;
    }
}
