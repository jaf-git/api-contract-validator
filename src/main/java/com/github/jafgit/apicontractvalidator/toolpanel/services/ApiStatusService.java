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
        LOG.warn("[ApiStatusService] Implemented Endpoints Map: " + implementedEndpoints);


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
        
        String tag = null;
        if (operation.getTags() != null && !operation.getTags().isEmpty()) {
            tag = operation.getTags().get(0); // Use the first tag for grouping
        }
        
        endpointInfos.add(new EndpointInfo(path, method, status, tag));
        LOG.warn("[ApiStatusService] Added EndpointInfo: Path=" + path + ", Method=" + method + ", Status=" + status + ", Tag=" + tag);
    }

    private Map<String, Set<String>> findImplementedEndpoints() {
        return ApplicationManager.getApplication().runReadAction((Computable<Map<String, Set<String>>>) () -> {
            Map<String, Set<String>> implementedMap = new HashMap<>();
            // Use allScope to include project source, libraries, and dependencies.
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            LOG.warn("[ApiStatusService] findImplementedEndpoints(): Starting annotation search.");

            // Handle composed annotations like @GetMapping
            for (String annotationFqn : COMPOSED_MAPPINGS.keySet()) {
                PsiClass ann = JavaPsiFacade.getInstance(project).findClass(annotationFqn, scope);
                if (ann == null) {
                    LOG.warn("[ApiStatusService] findImplementedEndpoints(): Annotation class not found: " + annotationFqn);
                    continue;
                }
                Collection<PsiModifierListOwner> owners = AnnotationTargetsSearch.search(ann, scope).findAll();
                LOG.warn("[ApiStatusService] findImplementedEndpoints(): Found " + owners.size() + " targets for " + annotationFqn);
                for (PsiModifierListOwner owner : owners) {
                    if (owner instanceof PsiMethod) {
                        collectFromComposedMapping((PsiMethod) owner, annotationFqn, COMPOSED_MAPPINGS.get(annotationFqn), implementedMap);
                    }
                }
            }

            // Handle generic @RequestMapping on methods
            PsiClass reqAnn = JavaPsiFacade.getInstance(project).findClass(REQUEST_MAPPING, scope);
            if (reqAnn != null) {
                Collection<PsiModifierListOwner> owners = AnnotationTargetsSearch.search(reqAnn, scope).findAll();
                LOG.warn("[ApiStatusService] findImplementedEndpoints(): Found " + owners.size() + " targets for " + REQUEST_MAPPING);
                for (PsiModifierListOwner owner : owners) {
                    if (owner instanceof PsiMethod) {
                        collectFromRequestMapping((PsiMethod) owner, implementedMap);
                    }
                }
            } else {
                LOG.warn("[ApiStatusService] findImplementedEndpoints(): Annotation class not found: " + REQUEST_MAPPING);
            }
            LOG.warn("[ApiStatusService] findImplementedEndpoints(): Finished annotation search.");
            return implementedMap;
        });
    }

    private void collectFromComposedMapping(PsiMethod method, String annotationFqn, String httpMethod, Map<String, Set<String>> out) {
        LOG.warn("[ApiStatusService] collectFromComposedMapping(): Processing method: " + method.getName() + " with annotation: " + annotationFqn);
        PsiAnnotation ann = method.getAnnotation(annotationFqn);
        if (ann == null) return;
        String[] methodPaths = extractPaths(ann);
        if (methodPaths.length == 0) methodPaths = new String[]{""};
        String[] classPaths = getClassLevelPaths(method.getContainingClass());
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String combined = combinePaths(classPath, methodPath);
                out.computeIfAbsent(combined, k -> new HashSet<>()).add(httpMethod);
                LOG.warn("[ApiStatusService] collectFromComposedMapping(): Added to map: Path='" + combined + "', Method='" + httpMethod + "'");
            }
        }
    }

    private void collectFromRequestMapping(PsiMethod method, Map<String, Set<String>> out) {
        LOG.warn("[ApiStatusService] collectFromRequestMapping(): Processing method: " + method.getName());
        PsiAnnotation ann = method.getAnnotation(REQUEST_MAPPING);
        if (ann == null) return;
        String[] methodPaths = extractPaths(ann);
        if (methodPaths.length == 0) methodPaths = new String[]{""};
        Set<String> httpMethods = extractHttpMethods(ann);
        if (httpMethods.isEmpty()) {
            LOG.warn("[ApiStatusService] collectFromRequestMapping(): No HTTP methods found for " + method.getName() + ", assuming all COMPOSED_MAPPINGS methods.");
            httpMethods.addAll(COMPOSED_MAPPINGS.values());
        }
        String[] classPaths = getClassLevelPaths(method.getContainingClass());
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String combined = combinePaths(classPath, methodPath);
                for (String http : httpMethods) {
                    out.computeIfAbsent(combined, k -> new HashSet<>()).add(http);
                    LOG.warn("[ApiStatusService] collectFromRequestMapping(): Added to map: Path='" + combined + "', Method='" + http + "'");
                }
            }
        }
    }

    private String[] getClassLevelPaths(PsiClass psiClass) {
        if (psiClass == null) return new String[]{""};
        PsiAnnotation classAnn = psiClass.getAnnotation(REQUEST_MAPPING);
        if (classAnn == null) return new String[]{""};
        String[] classPaths = extractPaths(classAnn);
        LOG.warn("[ApiStatusService] getClassLevelPaths(): Class level paths for " + psiClass.getName() + ": " + Arrays.toString(classPaths));
        return (classPaths.length == 0) ? new String[]{""} : classPaths;
    }

    private String[] extractPaths(PsiAnnotation annotation) {
        PsiAnnotationMemberValue val = annotation.findAttributeValue("value");
        if (val == null) val = annotation.findAttributeValue("path");
        if (val == null) {
            LOG.warn("[ApiStatusService] extractPaths(): No 'value' or 'path' attribute found for annotation: " + annotation.getQualifiedName());
            return new String[0];
        }
        if (val instanceof PsiArrayInitializerMemberValue) {
            String[] paths = Arrays.stream(((PsiArrayInitializerMemberValue) val).getInitializers())
                    .map(this::resolveString)
                    .filter(Objects::nonNull)
                    .toArray(String[]::new);
            LOG.warn("[ApiStatusService] extractPaths(): Extracted array paths: " + Arrays.toString(paths));
            return paths;
        } else {
            String path = resolveString(val);
            LOG.warn("[ApiStatusService] extractPaths(): Extracted single path: " + path);
            return path == null ? new String[0] : new String[]{path};
        }
    }

    private String resolveString(PsiAnnotationMemberValue value) {
        Object constValue = constEval.computeConstantExpression(value);
        LOG.warn("[ApiStatusService] resolveString(): Resolving value: '" + value.getText() + "' to '" + constValue + "'");
        return constValue instanceof String ? (String) constValue : null;
    }

    private Set<String> extractHttpMethods(PsiAnnotation annotation) {
        PsiAnnotationMemberValue methodAttr = annotation.findAttributeValue("method");
        if (methodAttr == null) {
            LOG.warn("[ApiStatusService] extractHttpMethods(): No 'method' attribute found for annotation: " + annotation.getQualifiedName());
            return Collections.emptySet();
        }
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
        LOG.warn("[ApiStatusService] extractHttpMethods(): Extracted HTTP methods: " + result);
        return result;
    }

    private String combinePaths(String c, String m) {
        String cp = (c == null) ? "" : c.trim();
        String mp = (m == null) ? "" : m.trim();
        LOG.warn("[ApiStatusService] combinePaths(): Combining class path '" + c + "' and method path '" + m + "'");
        if (!cp.startsWith("/")) cp = "/" + cp;
        if (!mp.startsWith("/")) mp = "/" + mp;
        if (cp.endsWith("/") && cp.length() > 1) cp = cp.substring(0, cp.length() - 1);
        if (mp.endsWith("/") && mp.length() > 1) mp = mp.substring(0, mp.length() - 1);
        if ("/".equals(cp)) cp = "";
        String full = (cp + mp).replaceAll("/+", "/");
        full = full.isEmpty() ? "/" : full;
        LOG.warn("[ApiStatusService] combinePaths(): Result: '" + full + "'");
        return full;
    }
}
