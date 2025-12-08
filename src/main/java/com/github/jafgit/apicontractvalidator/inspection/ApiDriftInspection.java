package com.github.jafgit.apicontractvalidator.inspection;

import com.github.jafgit.apicontractvalidator.core.OpenApiSpecService;
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
import java.util.Objects;
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
    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

    public ApiDriftInspection() {
        LOG.warn("[ApiDriftInspection] INSTANCE CREATED.");
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        LOG.warn("[ApiDriftInspection] buildVisitor() called for file: " + holder.getFile().getName());
        Project project = holder.getProject();
        OpenApiSpecService specService = project.getService(OpenApiSpecService.class);
        OpenAPI openApi = specService.getSpec();
        PsiConstantEvaluationHelper constEval = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();


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

                PsiAnnotation methodAnnotation = annotationOpt.get();
                String qualifiedName = methodAnnotation.getQualifiedName();
                LOG.warn("[ApiDriftInspection] Found Spring annotation '" + qualifiedName + "' on method: " + method.getName());

                // Get class-level paths
                String[] classPaths = getClassLevelPaths(method.getContainingClass(), constEval);
                // Get method-level paths
                String[] methodPaths = extractPaths(methodAnnotation, constEval);
                if (methodPaths.length == 0) methodPaths = new String[]{""}; // Default to empty string for root path

                // Determine the element to highlight for the method annotation
                PsiElement elementToHighlight = getElementToHighlight(methodAnnotation);

                for (String classPath : classPaths) {
                    for (String methodPath : methodPaths) {
                        String fullPath = combinePaths(classPath, methodPath);
                        LOG.warn("[ApiDriftInspection] Combined path for method '" + method.getName() + "': '" + fullPath + "'");

                        // Validate Path
                        LOG.warn("[ApiDriftInspection] Calling PathValidator for path: " + fullPath);
                        PathValidator.validate(openApi, fullPath, elementToHighlight, holder);

                        // Validate Method
                        String httpMethod = ANNOTATION_TO_HTTP_METHOD.get(qualifiedName);
                        if (httpMethod != null) {
                            LOG.warn("[ApiDriftInspection] Calling MethodValidator for HTTP method: '" + httpMethod + "' on path: " + fullPath);
                            MethodValidator.validate(openApi, fullPath, httpMethod, elementToHighlight, holder);

                            // Validate Parameters
                            PathItem pathItem = openApi.getPaths() != null ? openApi.getPaths().get(fullPath) : null;
                            if (pathItem != null) {
                                Operation operation = getOperation(pathItem, httpMethod);
                                if (operation != null) {
                                    LOG.warn("[ApiDriftInspection] Calling ParameterValidator for method: " + method.getName() + " on path: " + fullPath);
                                    ParameterValidator.validate(openApi, operation, method, holder);
                                }
                            }
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

    // --- Helper methods for path extraction and combination (adapted from ApiStatusService) ---

    private String[] getClassLevelPaths(PsiClass psiClass, PsiConstantEvaluationHelper constEval) {
        if (psiClass == null) return new String[]{""};
        PsiAnnotation classAnn = psiClass.getAnnotation(REQUEST_MAPPING);
        if (classAnn == null) return new String[]{""};
        String[] classPaths = extractPaths(classAnn, constEval);
        LOG.warn("[ApiDriftInspection] getClassLevelPaths(): Class level paths for " + psiClass.getName() + ": " + Arrays.toString(classPaths));
        return (classPaths.length == 0) ? new String[]{""} : classPaths;
    }

    private String[] extractPaths(PsiAnnotation annotation, PsiConstantEvaluationHelper constEval) {
        PsiAnnotationMemberValue val = annotation.findAttributeValue("value");
        if (val == null) val = annotation.findAttributeValue("path");
        if (val == null) {
            LOG.warn("[ApiDriftInspection] extractPaths(): No 'value' or 'path' attribute found for annotation: " + annotation.getQualifiedName());
            return new String[0];
        }
        if (val instanceof PsiArrayInitializerMemberValue) {
            return Arrays.stream(((PsiArrayInitializerMemberValue) val).getInitializers())
                    .map(v -> resolveString(v, constEval))
                    .filter(Objects::nonNull)
                    .toArray(String[]::new);
        } else {
            String path = resolveString(val, constEval);
            return path == null ? new String[0] : new String[]{path};
        }
    }

    private String resolveString(PsiAnnotationMemberValue value, PsiConstantEvaluationHelper constEval) {
        Object constValue = constEval.computeConstantExpression(value);
        return constValue instanceof String ? (String) constValue : null;
    }

    private String combinePaths(String c, String m) {
        String classPath = (c == null) ? "" : c.trim();
        String methodPath = (m == null) ? "" : m.trim();

        // Remove leading/trailing slashes from both parts for consistent joining
        if (classPath.startsWith("/")) classPath = classPath.substring(1);
        if (classPath.endsWith("/")) classPath = classPath.substring(0, classPath.length() - 1);

        if (methodPath.startsWith("/")) methodPath = methodPath.substring(1);
        if (methodPath.endsWith("/")) methodPath = methodPath.substring(0, methodPath.length() - 1);

        String combinedPath;
        if (classPath.isEmpty() && methodPath.isEmpty()) {
            combinedPath = "/";
        } else if (classPath.isEmpty()) {
            combinedPath = "/" + methodPath;
        } else if (methodPath.isEmpty()) {
            combinedPath = "/" + classPath;
        } else {
            combinedPath = "/" + classPath + "/" + methodPath;
        }

        return combinedPath;
    }
}
