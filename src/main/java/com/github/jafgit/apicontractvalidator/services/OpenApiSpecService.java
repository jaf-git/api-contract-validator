package com.github.jafgit.apicontractvalidator.services;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java version of the project-level service for managing the OpenAPI specification.
 */
public class OpenApiSpecService {

    private static final Logger LOG = Logger.getInstance(OpenApiSpecService.class);

    private final Project project;
    private final AtomicReference<OpenAPI> openApi = new AtomicReference<>(null);

    public OpenApiSpecService(@NotNull Project project) {
        this.project = project;
        LOG.warn("--- OpenApiSpecService INSTANCE CREATED for project: " + project.getName() + " ---");
    }

    @Nullable
    public OpenAPI getSpec() {
        return openApi.get();
    }

    public void reloadSpec() {
        LOG.warn("reloadSpec(): Starting spec reload.");
        VirtualFile specFile = findSpecFile();

        if (specFile == null) {
            LOG.error("reloadSpec(): No 'openapi.yaml' file found in project.");
            openApi.set(null);
            Notification notification = new Notification(
                "ApiContractValidator",
                "OpenAPI Spec Not Found",
                "Could not find 'openapi.yaml' in the project.",
                NotificationType.WARNING
            );
            Notifications.Bus.notify(notification, project);
            return;
        }

        LOG.warn("reloadSpec(): Found spec file at: " + specFile.getPath());
        try {
            OpenAPI parsedApi = new OpenAPIV3Parser().read(specFile.getPath());
            openApi.set(parsedApi); // Set it right away

            if (parsedApi == null) {
                LOG.error("reloadSpec(): Failed to parse the OpenAPI spec. The file might be invalid.");
                Notification notification = new Notification(
                    "ApiContractValidator",
                    "Failed to Parse OpenAPI Spec",
                    "Could not parse 'openapi.yaml'. The file may be invalid or empty.",
                    NotificationType.ERROR
                );
                Notifications.Bus.notify(notification, project);
            } else {
                int pathCount = parsedApi.getPaths() != null ? parsedApi.getPaths().size() : 0;
                LOG.warn("reloadSpec(): Successfully parsed and cached the OpenAPI spec. Found " + pathCount + " paths.");
                Notification notification = new Notification(
                    "ApiContractValidator",
                    "OpenAPI Spec Loaded",
                    "Successfully loaded 'openapi.yaml' with " + pathCount + " paths.",
                    NotificationType.INFORMATION
                );
                Notifications.Bus.notify(notification, project);
            }
        } catch (Exception e) {
            LOG.error("reloadSpec(): An exception occurred during parsing.", e);
            openApi.set(null);
            Notification notification = new Notification(
                "ApiContractValidator",
                "Error Loading OpenAPI Spec",
                "An exception occurred while parsing 'openapi.yaml'.",
                NotificationType.ERROR
            );
            Notifications.Bus.notify(notification, project);
        }
    }

    @Nullable
    private VirtualFile findSpecFile() {
        LOG.warn("findSpecFile(): Entering read-action to search index.");
        // Use a Computable lambda for runReadAction in Java
        VirtualFile file = ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<VirtualFile>) () -> {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                "openapi.yaml",
                GlobalSearchScope.projectScope(project)
            );
            LOG.warn("findSpecFile(): FilenameIndex returned " + files.size() + " file(s).");
            return files.isEmpty() ? null : files.iterator().next();
        });
        LOG.warn("findSpecFile(): Exiting read-action.");
        return file;
    }
}
