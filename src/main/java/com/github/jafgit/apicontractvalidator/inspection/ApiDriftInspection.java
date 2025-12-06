package com.github.jafgit.apicontractvalidator.inspection;

import com.github.jafgit.apicontractvalidator.services.OpenApiSpecService;
import com.github.jafgit.apicontractvalidator.validator.MethodValidator;
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
        LOG.warn("--- ApiDriftInspection INSTANCE CREATED ---");
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        OpenApiSpecService specService = holder.getProject().getService(OpenApiSpecService.class);
        OpenAPI openApi = specService.getSpec();

        if (openApi == null) {
            handleSpecNotFound(holder);
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        LOG.info("--- Building ApiDriftInspection visitor for file: " + holder.getFile().getName() + " ---");

        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                LOG.debug("Visiting method: " + method.getName());

                Optional<PsiAnnotation> annotationOpt = findSpringRequestAnnotation(method);

                if (annotationOpt.isEmpty()) {
                    LOG.debug("No relevant Spring annotation found on method: " + method.getName());
                    return;
                }

                PsiAnnotation annotation = annotationOpt.get();
                String qualifiedName = annotation.getQualifiedName();
                LOG.debug("Found Spring annotation '" + qualifiedName + "' on method: " + method.getName());

                PathValue pathValue = getPathFromAnnotation(annotation);
                if (pathValue == null) {
                    LOG.warn("Could not extract path from annotation on method: " + method.getName());
                    return;
                }
                LOG.warn("Path extracted from annotation: '" + pathValue.path + "'");

                PathItem pathItem = PathValidator.validate(openApi, pathValue.path, pathValue.elementToHighlight, holder);

                if (pathItem != null) {
                    // Path is valid, now validate the method
                    String httpMethod = ANNOTATION_TO_HTTP_METHOD.get(qualifiedName);
                    if (httpMethod != null) {
                        LOG.warn("Path is valid. Calling MethodValidator for HTTP method: '" + httpMethod + "'");
                        PsiElement elementToHighlight = getElementToHighlight(annotation);
                        LOG.info("Element to highlight: " + elementToHighlight.getText() + "pathItem" + pathItem);
                        MethodValidator.validate(httpMethod, pathItem, elementToHighlight, holder);
                    }
                }
            }
        };
    }

    private void handleSpecNotFound(@NotNull ProblemsHolder holder) {
        LOG.warn("ApiDriftInspection: No OpenAPI spec found. Inspection will not run.");
        Notification notification = new Notification(
                "ApiContractValidator",
                "OpenAPI Spec Not Found",
                "The OpenAPI specification file (openapi.yaml) was not found in the project.",
                NotificationType.WARNING
        );

        notification.addAction(new NotificationAction("Reload Spec") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
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
        return Arrays.stream(method.getModifierList().getAnnotations())
                .filter(a -> SPRING_REQUEST_ANNOTATIONS.contains(a.getQualifiedName()))
                .findFirst();
    }

    @NotNull
    private PsiElement getElementToHighlight(@NotNull PsiAnnotation annotation) {
        LOG.warn("getElementToHighlight: Starting for annotation '" + annotation.getText() + "'");
        PsiJavaCodeReferenceElement annotationNameElement = annotation.getNameReferenceElement();
        if (annotationNameElement != null) {
            LOG.warn("getElementToHighlight: Found annotation name element: '" + annotationNameElement.getText() + "'");
            PsiElement identifier = annotationNameElement.getReferenceNameElement();
            if (identifier != null) {
                LOG.warn("getElementToHighlight: Found identifier: '" + identifier.getText() + "'. Using this for highlighting.");
                return identifier;
            } else {
                LOG.warn("getElementToHighlight: Identifier was null. Falling back to annotation name element.");
                return annotationNameElement;
            }
        }
        LOG.warn("getElementToHighlight: Annotation name element was null. Falling back to the full annotation text.");
        return annotation;
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
        PsiAnnotationMemberValue valueAttribute = annotation.findAttributeValue("value");
        if (valueAttribute instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression) valueAttribute).getValue();
            if (value instanceof String) {
                return new PathValue((String) value, valueAttribute);
            }
        }
        // Also check for "path" attribute
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
