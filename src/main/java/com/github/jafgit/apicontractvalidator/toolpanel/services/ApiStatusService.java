package com.github.jafgit.apicontractvalidator.toolpanel.services;

import com.github.jafgit.apicontractvalidator.services.OpenApiSpecService;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointStatus;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        LOG.warn("[ApiStatusService] INSTANCE CREATED for project: " + project.getName());
    }

    public List<EndpointInfo> getEndpointInfos() {
        LOG.warn("[ApiStatusService] getEndpointInfos() called.");
        OpenApiSpecService specService = project.getService(OpenApiSpecService.class);
        OpenAPI openApi = specService.getSpec();

        if (openApi == null || openApi.getPaths() == null) {
            LOG.warn("[ApiStatusService] OpenAPI spec is null or has no paths. Returning empty list.");
            return new ArrayList<>();
        }

        // Step 1: Find all implemented endpoints first.
        Map<String, Set<String>> implementedEndpoints = findImplementedEndpoints();
        LOG.warn("[ApiStatusService] AnnotationScanner found " + implementedEndpoints.size() + " unique implemented paths.");

        // Step 2: Build the final list from the spec, setting status on creation.
        List<EndpointInfo> finalEndpoints = new ArrayList<>();
        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            addEndpointInfoWithStatus(finalEndpoints, path, "GET", pathItem.getGet(), implementedEndpoints);
            addEndpointInfoWithStatus(finalEndpoints, path, "POST", pathItem.getPost(), implementedEndpoints);
            addEndpointInfoWithStatus(finalEndpoints, path, "PUT", pathItem.getPut(), implementedEndpoints);
            addEndpointInfoWithStatus(finalEndpoints, path, "DELETE", pathItem.getDelete(), implementedEndpoints);
            addEndpointInfoWithStatus(finalEndpoints, path, "PATCH", pathItem.getPatch(), implementedEndpoints);
        }

        LOG.warn("[ApiStatusService] getEndpointInfos() finished. Returning " + finalEndpoints.size() + " endpoints.");
        return finalEndpoints;
    }

    private void addEndpointInfoWithStatus(List<EndpointInfo> endpointInfos, String path, String method, Operation operation, Map<String, Set<String>> implementedEndpoints) {
        if (operation == null) return;
        Set<String> implementedMethods = implementedEndpoints.get(path);
        boolean isImplemented = implementedMethods != null && implementedMethods.contains(method);
        EndpointStatus status = isImplemented ? EndpointStatus.IMPLEMENTED : EndpointStatus.NOT_IMPLEMENTED;
        endpointInfos.add(new EndpointInfo(path, method, status));
    }

    private Map<String, Set<String>> findImplementedEndpoints() {
        return ApplicationManager.getApplication().runReadAction((Computable<Map<String, Set<String>>>) () -> {
            Map<String, Set<String>> implementedMap = new HashMap<>();
            // Use allScope to include project source, libraries, and dependencies.
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);

            // Handle composed annotations like @GetMapping
            for (String annotationFqn : COMPOSED_MAPPINGS.keySet()) {
                PsiClass ann = JavaPsiFacade.getInstance(project).findClass(annotationFqn, scope);
                if (ann == null) continue;
                //noinspection deprecation
                for (PsiModifierListOwner owner : AnnotationTargetsSearch.search(ann, scope).findAll()) {
                    if (owner instanceof PsiMethod) {
                        collectFromComposedMapping((PsiMethod) owner, annotationFqn, COMPOSED_MAPPINGS.get(annotationFqn), implementedMap);
                    }
                }
            }

            // Handle generic @RequestMapping on methods
            PsiClass reqAnn = JavaPsiFacade.getInstance(project).findClass(REQUEST_MAPPING, scope);
            if (reqAnn != null) {
                //noinspection deprecation
                for (PsiModifierListOwner owner : AnnotationTargetsSearch.search(reqAnn, scope).findAll()) {
                    if (owner instanceof PsiMethod) {
                        collectFromRequestMapping((PsiMethod) owner, implementedMap);
                    }
                }
            }
            return implementedMap;
        });
    }

    private void collectFromComposedMapping(PsiMethod method, String annotationFqn, String httpMethod, Map<String, Set<String>> out) {
        PsiAnnotation ann = method.getAnnotation(annotationFqn);
        if (ann == null) return;
        String[] methodPaths = extractPaths(ann);
        if (methodPaths.length == 0) methodPaths = new String[]{""};
        String[] classPaths = getClassLevelPaths(method.getContainingClass());
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String combined = combinePaths(classPath, methodPath);
                out.computeIfAbsent(combined, k -> new HashSet<>()).add(httpMethod);
            }
        }
    }

    private void collectFromRequestMapping(PsiMethod method, Map<String, Set<String>> out) {
        PsiAnnotation ann = method.getAnnotation(REQUEST_MAPPING);
        if (ann == null) return;
        String[] methodPaths = extractPaths(ann);
        if (methodPaths.length == 0) methodPaths = new String[]{""};
        Set<String> httpMethods = extractHttpMethods(ann);
        if (httpMethods.isEmpty()) httpMethods.addAll(COMPOSED_MAPPINGS.values());
        String[] classPaths = getClassLevelPaths(method.getContainingClass());
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String combined = combinePaths(classPath, methodPath);
                for (String http : httpMethods) {
                    out.computeIfAbsent(combined, k -> new HashSet<>()).add(http);
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
        String cp = (c == null) ? "" : c.trim();
        String mp = (m == null) ? "" : m.trim();
        if (!cp.startsWith("/")) cp = "/" + cp;
        if (!mp.startsWith("/")) mp = "/" + mp;
        if (cp.endsWith("/") && cp.length() > 1) cp = cp.substring(0, cp.length() - 1);
        if (mp.endsWith("/") && mp.length() > 1) mp = mp.substring(0, mp.length() - 1);
        if ("/".equals(cp)) cp = "";
        String full = (cp + mp).replaceAll("/+", "/");
        return full.isEmpty() ? "/" : full;
    }
}
