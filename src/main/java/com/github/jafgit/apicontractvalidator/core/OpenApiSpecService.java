package com.github.jafgit.apicontractvalidator.core;

import com.github.jafgit.apicontractvalidator.listeners.SpecUpdateListener;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class OpenApiSpecService implements Disposable {

    private static final Logger LOG = Logger.getInstance(OpenApiSpecService.class);
    private static final String SPEC_FILE_NAME = "openapi.yaml";

    private final Project project;
    private final AtomicReference<OpenAPI> openApi = new AtomicReference<>(null);

    public OpenApiSpecService(@NotNull Project project) {
        this.project = project;
        LOG.warn("[OpenApiSpecService] INSTANCE CREATED for project: " + project.getName());

        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                LOG.warn("[OpenApiSpecService] VFS_CHANGES event received.");
                for (VFileEvent event : events) {
                    if (event.getFile() != null && event.getFile().getName().equals(SPEC_FILE_NAME)) {
                        LOG.warn("[OpenApiSpecService] Detected change in '" + SPEC_FILE_NAME + "'. Reloading spec and re-analyzing project.");
                        reloadSpec();
                        DaemonCodeAnalyzer.getInstance(project).restart();
                        break;
                    }
                }
            }
        });
    }

    @Nullable
    public OpenAPI getSpec() {
        LOG.warn("[OpenApiSpecService] getSpec() called.");
        return openApi.get();
    }

    public void reloadSpec() {
        LOG.warn("[OpenApiSpecService] reloadSpec() called.");
        VirtualFile specFile = findSpecFile();
        OpenAPI parsedApi = null;

        if (specFile == null) {
            LOG.warn("[OpenApiSpecService] reloadSpec(): No '" + SPEC_FILE_NAME + "' file found in project.");
            notifyOfReloadStatus(false, "Could not find '" + SPEC_FILE_NAME + "' in the project.", NotificationType.WARNING);
        } else {
            LOG.warn("[OpenApiSpecService] reloadSpec(): Found spec file at: " + specFile.getPath());
            try {
                parsedApi = new OpenAPIV3Parser().read(specFile.getPath());
                if (parsedApi == null) {
                    LOG.error("[OpenApiSpecService] reloadSpec(): Failed to parse the OpenAPI spec. The file might be invalid.");
                    notifyOfReloadStatus(false, "Failed to parse '" + SPEC_FILE_NAME + "'. The file may be invalid.", NotificationType.ERROR);
                } else {
                    int pathCount = parsedApi.getPaths() != null ? parsedApi.getPaths().size() : 0;
                    LOG.warn("[OpenApiSpecService] reloadSpec(): Successfully parsed and cached the OpenAPI spec. Found " + pathCount + " paths.");
                    notifyOfReloadStatus(true, "Successfully reloaded '" + SPEC_FILE_NAME + "' with " + pathCount + " paths.", NotificationType.INFORMATION);
                }
            } catch (Exception e) {
                LOG.error("[OpenApiSpecService] reloadSpec(): An exception occurred during parsing.", e);
                notifyOfReloadStatus(false, "An error occurred while parsing '" + SPEC_FILE_NAME + "'.", NotificationType.ERROR);
            }
        }

        LOG.warn("[OpenApiSpecService] Setting openApi reference.");
        openApi.set(parsedApi);
        LOG.warn("[OpenApiSpecService] Publishing update to SPEC_UPDATE_TOPIC.");
        project.getMessageBus().syncPublisher(SpecUpdateListener.SPEC_UPDATE_TOPIC).onSpecUpdate(parsedApi);
        LOG.warn("[OpenApiSpecService] Finished publishing update.");
    }

    private void notifyOfReloadStatus(boolean success, String content, NotificationType type) {
        LOG.warn("[OpenApiSpecService] notifyOfReloadStatus() called with success=" + success);
        String title = success ? "OpenAPI Spec Reloaded" : "OpenAPI Spec Reload Failed";
        Notification notification = new Notification("ApiContractValidator", title, content, type);
        Notifications.Bus.notify(notification, project);
    }

    @Nullable
    private VirtualFile findSpecFile() {
        LOG.warn("[OpenApiSpecService] findSpecFile() called.");
        return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<VirtualFile>) () -> {
            LOG.warn("[OpenApiSpecService] findSpecFile(): Entering read-action.");
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                SPEC_FILE_NAME,
                GlobalSearchScope.projectScope(project)
            );
            LOG.warn("[OpenApiSpecService] findSpecFile(): FilenameIndex returned " + files.size() + " file(s).");
            LOG.warn("[OpenApiSpecService] findSpecFile(): Exiting read-action.");
            return files.isEmpty() ? null : files.iterator().next();
        });
    }

    @Override
    public void dispose() {
        LOG.warn("[OpenApiSpecService] dispose() called for project: " + project.getName());
    }
}
