package com.github.jafgit.apicontractvalidator.inspection;

import com.github.jafgit.apicontractvalidator.services.OpenApiSpecService;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class ApiDriftInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Logger LOG = Logger.getInstance(ApiDriftInspection.class);

    private static final Set<String> SPRING_REQUEST_ANNOTATIONS = Set.of(
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    );

    public ApiDriftInspection() {
        LOG.error("--- ApiDriftInspection INSTANCE CREATED ---");
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        OpenApiSpecService specService = holder.getProject().getService(OpenApiSpecService.class);
        OpenAPI openApi = specService.getSpec();

        if (openApi == null) {
            LOG.error("ApiDriftInspection: No OpenAPI spec found. Inspection will not run.");
            // Create a notification with a reload action
            Notification notification = new Notification(
                "ApiContractValidator",
                "OpenAPI Spec Not Found",
                "The OpenAPI specification file (openapi.yaml) was not found in the project.", // content
                NotificationType.WARNING
            );

            notification.addAction(new NotificationAction("Reload Spec") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    Project project = e.getProject();
                    if (project != null) {
                        // Get the service and try reloading the spec
                        project.getService(OpenApiSpecService.class).reloadSpec();
                        // Restart analysis to re-trigger the inspection
                        DaemonCodeAnalyzer.getInstance(project).restart();
                        notification.expire();
                    }
                }
            });

            Notifications.Bus.notify(notification, holder.getProject());
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        LOG.warn("--- Building ApiDriftInspection visitor for file: " + holder.getFile().getName() + " ---");

        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                LOG.info("Visiting method: " + method.getName());

                Optional<PsiAnnotation> annotationOpt = Arrays.stream(method.getModifierList().getAnnotations())
                    .filter(a -> SPRING_REQUEST_ANNOTATIONS.contains(a.getQualifiedName()))
                    .findFirst();

                if (annotationOpt.isEmpty()) {
                    LOG.info("No relevant Spring annotation found on method: " + method.getName());
                    return;
                }

                PsiAnnotation annotation = annotationOpt.get();
                LOG.warn("Found Spring annotation '" + annotation.getQualifiedName() + "' on method: " + method.getName());

                PathValue pathValue = getPathFromAnnotation(annotation);
                if (pathValue == null) {
                    LOG.warn("Could not extract path from annotation on method: " + method.getName());
                    return;
                }
                LOG.warn("Extracted path '" + pathValue.path + "' from annotation.");

                if (openApi.getPaths() == null || !openApi.getPaths().containsKey(pathValue.path)) {
                    LOG.error("DRIFT DETECTED: Path '" + pathValue.path + "' is MISSING from the contract.");
                    holder.registerProblem(
                        pathValue.elementToHighlight,
                        "API Drift Detected: Path '" + pathValue.path + "' is missing from openapi.yaml contract."
                    );
                } else {
                    LOG.info("Path '" + pathValue.path + "' is valid and exists in the contract.");
                }
            }
        };
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
        return null;
    }
}
