package com.github.jafgit.apicontractvalidator.toolpanel.services;

import com.github.jafgit.apicontractvalidator.core.OpenApiSpecService;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointStatus;
import com.github.jafgit.apicontractvalidator.validator.MethodValidator;
import com.github.jafgit.apicontractvalidator.validator.ParameterValidator;
import com.github.jafgit.apicontractvalidator.validator.PathValidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotationTargetsSearch;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.*;

public class ApiStatusService {

    private static final Logger LOG = Logger.getInstance(ApiStatusService.class);

    private final Project project;
    private final PsiConstantEvaluationHelper constEval;

    private static final Map<String, String> COMPOSED_MAPPINGS = Map.of(
            "org.springframework.web.bind.annotation.GetMapping", "GET",
            "org.springframework.web.bind.annotation.PostMapping", "POST",
            "org.springframework.web.bind.annotation.PutMapping", "PUT",
            "org.springframework.web.bind.annotation.DeleteMapping", "DELETE",
            "org.springframework.web.bind.annotation.PatchMapping", "PATCH"
    );
    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

    public ApiStatusService(Project project) {
        this.project = project;
        this.constEval = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
    }

    public List<EndpointInfo> getEndpointInfos() {
        OpenApiSpecService specService = project.getService(OpenApiSpecService.class);
        OpenAPI openApi = specService.getSpec();

        if (openApi == null || openApi.getPaths() == null) {
            return new ArrayList<>();
        }

        Map<String, Map<String, PsiMethod>> implementedEndpoints = findImplementedEndpoints();

        List<EndpointInfo> finalEndpoints = new ArrayList<>();
        ApplicationManager.getApplication().runReadAction(() -> {
            for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();
                addEndpointInfoWithStatus(finalEndpoints, openApi, path, "GET", pathItem.getGet(), implementedEndpoints);
                addEndpointInfoWithStatus(finalEndpoints, openApi, path, "POST", pathItem.getPost(), implementedEndpoints);
                addEndpointInfoWithStatus(finalEndpoints, openApi, path, "PUT", pathItem.getPut(), implementedEndpoints);
                addEndpointInfoWithStatus(finalEndpoints, openApi, path, "DELETE", pathItem.getDelete(), implementedEndpoints);
                addEndpointInfoWithStatus(finalEndpoints, openApi, path, "PATCH", pathItem.getPatch(), implementedEndpoints);
            }
        });

        return finalEndpoints;
    }

    private void addEndpointInfoWithStatus(List<EndpointInfo> endpointInfos, OpenAPI openApi, String path, String method, Operation operation, Map<String, Map<String, PsiMethod>> implementedEndpoints) {
        if (operation == null) return;

        Map<String, PsiMethod> implementedMethods = implementedEndpoints.get(path);
        PsiMethod psiMethod = (implementedMethods != null) ? implementedMethods.get(method) : null;
        EndpointStatus status;
        SmartPsiElementPointer<PsiMethod> methodPointer = null;

        if (psiMethod == null) {
            status = EndpointStatus.NOT_IMPLEMENTED;
        } else {
            methodPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiMethod);
            boolean hasIssues = PathValidator.validate(openApi, path).isPresent()
                    || MethodValidator.validate(openApi, path, method).isPresent()
                    || !ParameterValidator.validate(openApi, operation, psiMethod).isEmpty();

            status = hasIssues ? EndpointStatus.HAS_ISSUES : EndpointStatus.IMPLEMENTED;
        }

        String tag = (operation.getTags() != null && !operation.getTags().isEmpty()) ? operation.getTags().get(0) : null;
        endpointInfos.add(new EndpointInfo(path, method, status, tag, methodPointer));
    }

    private Map<String, Map<String, PsiMethod>> findImplementedEndpoints() {
        return ApplicationManager.getApplication().runReadAction((Computable<Map<String, Map<String, PsiMethod>>>) () -> {
            Map<String, Map<String, PsiMethod>> implementedMap = new HashMap<>();
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);

            for (String annotationFqn : COMPOSED_MAPPINGS.keySet()) {
                PsiClass ann = JavaPsiFacade.getInstance(project).findClass(annotationFqn, scope);
                if (ann == null) continue;
                for (PsiModifierListOwner owner : AnnotationTargetsSearch.search(ann, scope).findAll()) {
                    if (owner instanceof PsiMethod) {
                        collectFromComposedMapping((PsiMethod) owner, annotationFqn, COMPOSED_MAPPINGS.get(annotationFqn), implementedMap);
                    }
                }
            }

            PsiClass reqAnn = JavaPsiFacade.getInstance(project).findClass(REQUEST_MAPPING, scope);
            if (reqAnn != null) {
                for (PsiModifierListOwner owner : AnnotationTargetsSearch.search(reqAnn, scope).findAll()) {
                    if (owner instanceof PsiMethod) {
                        collectFromRequestMapping((PsiMethod) owner, implementedMap);
                    }
                }
            }
            return implementedMap;
        });
    }

    private void collectFromComposedMapping(PsiMethod method, String annotationFqn, String httpMethod, Map<String, Map<String, PsiMethod>> out) {
        PsiAnnotation ann = method.getAnnotation(annotationFqn);
        if (ann == null) return;
        String[] methodPaths = extractPaths(ann);
        if (methodPaths.length == 0) methodPaths = new String[]{""};
        String[] classPaths = getClassLevelPaths(method.getContainingClass());
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String combined = combinePaths(classPath, methodPath);
                out.computeIfAbsent(combined, k -> new HashMap<>()).put(httpMethod, method);
            }
        }
    }

    private void collectFromRequestMapping(PsiMethod method, Map<String, Map<String, PsiMethod>> out) {
        PsiAnnotation ann = method.getAnnotation(REQUEST_MAPPING);
        if (ann == null) return;
        String[] methodPaths = extractPaths(ann);
        if (methodPaths.length == 0) methodPaths = new String[]{""};
        Set<String> httpMethods = extractHttpMethods(ann);
        if (httpMethods.isEmpty()) {
            httpMethods.addAll(COMPOSED_MAPPINGS.values());
        }
        String[] classPaths = getClassLevelPaths(method.getContainingClass());
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String combined = combinePaths(classPath, methodPath);
                for (String http : httpMethods) {
                    out.computeIfAbsent(combined, k -> new HashMap<>()).put(http, method);
                }
            }
        }
    }

    private String[] getClassLevelPaths(PsiClass psiClass) {
        if (psiClass == null) return new String[]{""};
        PsiAnnotation classAnn = psiClass.getAnnotation(REQUEST_MAPPING);
        if (classAnn == null) return new String[]{""};
        String[] classPaths = extractPaths(classAnn);
        return (classPaths.length == 0) ? new String[]{""} : classPaths;
    }

    private String[] extractPaths(PsiAnnotation annotation) {
        PsiAnnotationMemberValue val = annotation.findAttributeValue("value");
        if (val == null) val = annotation.findAttributeValue("path");
        if (val == null) return new String[0];
        if (val instanceof PsiArrayInitializerMemberValue) {
            return Arrays.stream(((PsiArrayInitializerMemberValue) val).getInitializers())
                    .map(this::resolveString)
                    .filter(Objects::nonNull)
                    .toArray(String[]::new);
        } else {
            String path = resolveString(val);
            return path == null ? new String[0] : new String[]{path};
        }
    }

    private String resolveString(PsiAnnotationMemberValue value) {
        Object constValue = constEval.computeConstantExpression(value);
        return constValue instanceof String ? (String) constValue : null;
    }

    private Set<String> extractHttpMethods(PsiAnnotation annotation) {
        PsiAnnotationMemberValue methodAttr = annotation.findAttributeValue("method");
        if (methodAttr == null) return Collections.emptySet();
        List<PsiAnnotationMemberValue> values = methodAttr instanceof PsiArrayInitializerMemberValue
                ? Arrays.asList(((PsiArrayInitializerMemberValue) methodAttr).getInitializers())
                : Collections.singletonList(methodAttr);
        Set<String> result = new HashSet<>();
        for (PsiAnnotationMemberValue val : values) {
            String text = val.getText();
            if (text.contains("GET")) result.add("GET");
            else if (text.contains("POST")) result.add("POST");
            else if (text.contains("PUT")) result.add("PUT");
            else if (text.contains("DELETE")) result.add("DELETE");
            else if (text.contains("PATCH")) result.add("PATCH");
        }
        return result;
    }

    private String combinePaths(String c, String m) {
        String classPath = (c == null) ? "" : c.trim();
        String methodPath = (m == null) ? "" : m.trim();
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
