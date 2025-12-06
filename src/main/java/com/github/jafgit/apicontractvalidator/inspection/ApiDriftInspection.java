package com.github.jafgit.apicontractvalidator.inspection;

import com.github.jafgit.apicontractvalidator.services.OpenApiSpecService;
import com.github.jafgit.apicontractvalidator.validator.MethodValidator;
import com.github.jafgit.apicontractvalidator.validator.ParameterValidator;
import com.github.jafgit.apicontractvalidator.validator.PathValidator;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ApiDriftInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Logger LOG = Logger.getInstance(ApiDriftInspection.class);

    private static final Map<String, String> ANNOTATION_TO_HTTP_METHOD = Map.of(
            "org.springframework.web.bind.annotation.GetMapping", "get",
            "org.springframework.web.bind.annotation.PostMapping", "post",
            "org.springframework.web.bind.annotation.PutMapping", "put",
            "org.springframework.web.bind.annotation.DeleteMapping", "delete",
            "org.springframework.web.bind.annotation.PatchMapping", "patch"
    );

    private static final Set<String> SPRING_REQUEST_ANNOTATIONS = ANNOTATION_TO_HTTP_METHOD.keySet();

    public ApiDriftInspection() {
        LOG.warn("[ApiDriftInspection] INSTANCE CREATED.");
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        LOG.warn("[ApiDriftInspection] buildVisitor() called for file: " + holder.getFile().getName());
        OpenApiSpecService specService = holder.getProject().getService(OpenApiSpecService.class);
        OpenAPI openApi = specService.getSpec();

        if (openApi == null) {
            LOG.warn("[ApiDriftInspection] OpenAPI spec is null. Handling spec not found.");
            handleSpecNotFound(holder);
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        LOG.warn("[ApiDriftInspection] Returning new JavaElementVisitor.");
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                LOG.warn("[ApiDriftInspection] visitMethod() called for method: " + method.getName());
                super.visitMethod(method);

                Optional<PsiAnnotation> annotationOpt = findSpringRequestAnnotation(method);

                if (annotationOpt.isEmpty()) {
                    LOG.warn("[ApiDriftInspection] No relevant Spring annotation found on method: " + method.getName());
                    return;
                }

                PsiAnnotation annotation = annotationOpt.get();
                String qualifiedName = annotation.getQualifiedName();
                LOG.warn("[ApiDriftInspection] Found Spring annotation '" + qualifiedName + "' on method: " + method.getName());

                PathValue pathValue = getPathFromAnnotation(annotation);
                if (pathValue == null) {
                    LOG.warn("[ApiDriftInspection] Could not extract path from annotation on method: " + method.getName());
                    return;
                }
                LOG.warn("[ApiDriftInspection] Extracted path '" + pathValue.path + "' from annotation.");

                LOG.warn("[ApiDriftInspection] Calling PathValidator.");
                PathValidator.validate(openApi, pathValue.path, pathValue.elementToHighlight, holder);

                String httpMethod = ANNOTATION_TO_HTTP_METHOD.get(qualifiedName);
                if (httpMethod != null) {
                    LOG.warn("[ApiDriftInspection] Calling MethodValidator for HTTP method: '" + httpMethod + "'");
                    PsiElement elementToHighlight = getElementToHighlight(annotation);
                    MethodValidator.validate(openApi, pathValue.path, httpMethod, elementToHighlight, holder);

                    PathItem pathItem = openApi.getPaths() != null ? openApi.getPaths().get(pathValue.path) : null;
                    if (pathItem != null) {
                        Operation operation = getOperation(pathItem, httpMethod);
                        if (operation != null) {
                            LOG.warn("[ApiDriftInspection] Calling ParameterValidator for method: " + method.getName());
                            ParameterValidator.validate(openApi, operation, method, holder);
                        }
                    }
                }
            }
        };
    }

    private void handleSpecNotFound(@NotNull ProblemsHolder holder) {
        LOG.warn("[ApiDriftInspection] handleSpecNotFound() called.");
        Notification notification = new Notification(
                "ApiContractValidator",
                "OpenAPI Spec Not Found",
                "The OpenAPI specification file (openapi.yaml) was not found in the project.",
                NotificationType.WARNING
        );

        notification.addAction(new NotificationAction("Reload Spec") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                LOG.warn("[ApiDriftInspection] 'Reload Spec' action performed.");
                Project project = e.getProject();
                if (project != null) {
                    project.getService(OpenApiSpecService.class).reloadSpec();
                    DaemonCodeAnalyzer.getInstance(project).restart();
                    notification.expire();
                }
            }
        });

        Notifications.Bus.notify(notification, holder.getProject());
    }

    private Optional<PsiAnnotation> findSpringRequestAnnotation(@NotNull PsiMethod method) {
        LOG.warn("[ApiDriftInspection] findSpringRequestAnnotation() called for method: " + method.getName());
        return Arrays.stream(method.getModifierList().getAnnotations())
                .filter(a -> SPRING_REQUEST_ANNOTATIONS.contains(a.getQualifiedName()))
                .findFirst();
    }

    @NotNull
    private PsiElement getElementToHighlight(@NotNull PsiAnnotation annotation) {
        LOG.warn("[ApiDriftInspection] getElementToHighlight() called for annotation '" + annotation.getText() + "'");
        PsiJavaCodeReferenceElement annotationNameElement = annotation.getNameReferenceElement();
        if (annotationNameElement != null) {
            PsiElement identifier = annotationNameElement.getReferenceNameElement();
            if (identifier != null) {
                LOG.warn("[ApiDriftInspection] Returning identifier: '" + identifier.getText() + "'");
                return identifier;
            }
            LOG.warn("[ApiDriftInspection] Returning annotation name element: '" + annotationNameElement.getText() + "'");
            return annotationNameElement;
        }
        LOG.warn("[ApiDriftInspection] Returning full annotation text.");
        return annotation;
    }

    @Nullable
    private Operation getOperation(PathItem pathItem, String httpMethod) {
        switch (httpMethod.toLowerCase()) {
            case "get":
                return pathItem.getGet();
            case "post":
                return pathItem.getPost();
            case "put":
                return pathItem.getPut();
            case "delete":
                return pathItem.getDelete();
            case "patch":
                return pathItem.getPatch();
            default:
                return null;
        }
    }

    private static class PathValue {
        final String path;
        final PsiElement elementToHighlight;

        PathValue(String path, PsiElement element) {
            this.path = path;
            this.elementToHighlight = element;
        }
    }

    @Nullable
    private PathValue getPathFromAnnotation(@NotNull PsiAnnotation annotation) {
        LOG.warn("[ApiDriftInspection] getPathFromAnnotation() called for annotation: " + annotation.getQualifiedName());
        PsiAnnotationMemberValue valueAttribute = annotation.findAttributeValue("value");
        if (valueAttribute instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression) valueAttribute).getValue();
            if (value instanceof String) {
                return new PathValue((String) value, valueAttribute);
            }
        }
        PsiAnnotationMemberValue pathAttribute = annotation.findAttributeValue("path");
        if (pathAttribute instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression) pathAttribute).getValue();
            if (value instanceof String) {
                return new PathValue((String) value, pathAttribute);
            }
        }
        return null;
    }
}
