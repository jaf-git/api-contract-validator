//package com.github.jafgit.apicontractvalidator.toolpanel.scanner;
//
//import com.intellij.openapi.project.Project;
//import com.intellij.psi.*;
//import com.intellij.psi.search.GlobalSearchScope;
//import com.intellij.psi.search.searches.AnnotationTargetsSearch;
//
//import java.util.*;
//
//public class AnnotationScanner {
//
//    private final Project project;
//    private final JavaPsiFacade javaPsi;
//    private final PsiConstantEvaluationHelper constEval;
//
//    private static final Map<String, String> COMPOSED_MAPPINGS = Map.of(
//            "org.springframework.web.bind.annotation.GetMapping", "GET",
//            "org.springframework.web.bind.annotation.PostMapping", "POST",
//            "org.springframework.web.bind.annotation.PutMapping", "PUT",
//            "org.springframework.web.bind.annotation.DeleteMapping", "DELETE",
//            "org.springframework.web.bind.annotation.PatchMapping", "PATCH"
//    );
//    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
//
//    public AnnotationScanner(Project project) {
//        this.project = project;
//        this.javaPsi = JavaPsiFacade.getInstance(project);
//        this.constEval = javaPsi.getConstantEvaluationHelper();
//    }
//
//    public List<ImplementedEndpoint> scanProject() {
//        // Use allScope to include project source, libraries, and dependencies. This is crucial.
//        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
//        List<ImplementedEndpoint> result = new ArrayList<>();
//
//        // Handle composed annotations like @GetMapping
//        for (String annotationFqn : COMPOSED_MAPPINGS.keySet()) {
//            PsiClass ann = javaPsi.findClass(annotationFqn, scope);
//            if (ann == null) continue;
//            //noinspection deprecation
//            for (PsiModifierListOwner owner : AnnotationTargetsSearch.search(ann, scope).findAll()) {
//                if (owner instanceof PsiMethod) {
//                    collectFromComposedMapping((PsiMethod) owner, annotationFqn, COMPOSED_MAPPINGS.get(annotationFqn), result);
//                }
//            }
//        }
//
//        // Handle generic @RequestMapping on methods
//        PsiClass reqAnn = javaPsi.findClass(REQUEST_MAPPING, scope);
//        if (reqAnn != null) {
//            //noinspection deprecation
//            for (PsiModifierListOwner owner : AnnotationTargetsSearch.search(reqAnn, scope).findAll()) {
//                if (owner instanceof PsiMethod) {
//                    collectFromRequestMapping((PsiMethod) owner, result);
//                }
//            }
//        }
//
//        return result;
//    }
//
//    private void collectFromComposedMapping(PsiMethod method, String annotationFqn, String httpMethod, List<ImplementedEndpoint> out) {
//        PsiAnnotation ann = method.getAnnotation(annotationFqn);
//        if (ann == null) return;
//
//        String[] methodPaths = extractPaths(ann);
//        if (methodPaths.length == 0) methodPaths = new String[]{""};
//
//        String[] classPaths = getClassLevelPaths(method.getContainingClass());
//
//        for (String classPath : classPaths) {
//            for (String methodPath : methodPaths) {
//                String combined = combinePaths(classPath, methodPath);
//                out.add(new ImplementedEndpoint(combined, httpMethod, method));
//            }
//        }
//    }
//
//    private void collectFromRequestMapping(PsiMethod method, List<ImplementedEndpoint> out) {
//        PsiAnnotation ann = method.getAnnotation(REQUEST_MAPPING);
//        if (ann == null) return;
//
//        String[] methodPaths = extractPaths(ann);
//        if (methodPaths.length == 0) methodPaths = new String[]{""};
//
//        Set<String> httpMethods = extractHttpMethods(ann);
//        if (httpMethods.isEmpty()) {
//            // If no method is specified on a method-level @RequestMapping, it defaults to all.
//            httpMethods.addAll(COMPOSED_MAPPINGS.values());
//        }
//
//        String[] classPaths = getClassLevelPaths(method.getContainingClass());
//
//        for (String classPath : classPaths) {
//            for (String methodPath : methodPaths) {
//                String combined = combinePaths(classPath, methodPath);
//                for (String http : httpMethods) {
//                    out.add(new ImplementedEndpoint(combined, http, method));
//                }
//            }
//        }
//    }
//
//    private String[] getClassLevelPaths(PsiClass psiClass) {
//        if (psiClass == null) return new String[]{""};
//        PsiAnnotation classAnn = psiClass.getAnnotation(REQUEST_MAPPING);
//        if (classAnn == null) return new String[]{""};
//        String[] classPaths = extractPaths(classAnn);
//        return (classPaths.length == 0) ? new String[]{""} : classPaths;
//    }
//
//    private String[] extractPaths(PsiAnnotation annotation) {
//        PsiAnnotationMemberValue val = annotation.findAttributeValue("value");
//        if (val == null) val = annotation.findAttributeValue("path");
//        if (val == null) return new String[0];
//
//        if (val instanceof PsiArrayInitializerMemberValue) {
//            return Arrays.stream(((PsiArrayInitializerMemberValue) val).getInitializers())
//                    .map(this::resolveString)
//                    .filter(Objects::nonNull)
//                    .toArray(String[]::new);
//        } else {
//            String path = resolveString(val);
//            return path == null ? new String[0] : new String[]{path};
//        }
//    }
//
//    private String resolveString(PsiAnnotationMemberValue value) {
//        Object constValue = constEval.computeConstantExpression(value);
//        if (constValue instanceof String) {
//            return (String) constValue;
//        }
//        return null;
//    }
//
//    private Set<String> extractHttpMethods(PsiAnnotation annotation) {
//        PsiAnnotationMemberValue methodAttr = annotation.findAttributeValue("method");
//        if (methodAttr == null) return Collections.emptySet();
//
//        List<PsiAnnotationMemberValue> values = methodAttr instanceof PsiArrayInitializerMemberValue
//                ? Arrays.asList(((PsiArrayInitializerMemberValue) methodAttr).getInitializers())
//                : Collections.singletonList(methodAttr);
//
//        Set<String> result = new HashSet<>();
//        for (PsiAnnotationMemberValue val : values) {
//            String text = val.getText(); // e.g., "RequestMethod.GET"
//            if (text.contains("GET")) result.add("GET");
//            else if (text.contains("POST")) result.add("POST");
//            else if (text.contains("PUT")) result.add("PUT");
//            else if (text.contains("DELETE")) result.add("DELETE");
//            else if (text.contains("PATCH")) result.add("PATCH");
//        }
//        return result;
//    }
//
//    private String combinePaths(String c, String m) {
//        String cp = (c == null) ? "" : c.trim();
//        String mp = (m == null) ? "" : m.trim();
//        if (!cp.startsWith("/")) cp = "/" + cp;
//        if (!mp.startsWith("/")) mp = "/" + mp;
//        if (cp.endsWith("/") && cp.length() > 1) cp = cp.substring(0, cp.length() - 1);
//        if (mp.endsWith("/") && mp.length() > 1) mp = mp.substring(0, mp.length() - 1);
//        if ("/".equals(cp)) cp = "";
//        String full = (cp + mp).replaceAll("/+", "/");
//        return full.isEmpty() ? "/" : full;
//    }
//}
