package com.github.jafgit.apicontractvalidator.toolpanel;

import com.github.jafgit.apicontractvalidator.listeners.SpecUpdateListener;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.services.ApiStatusService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import io.swagger.v3.oas.models.OpenAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Presenter in the MVP pattern for the API Tool Window.
 * It contains all the application logic, fetches data, and tells the View what to display.
 */
public class ApiToolWindowPresenter implements Disposable {

    private static final Logger LOG = Logger.getInstance(ApiToolWindowPresenter.class);

    private final ApiToolWindowView view;
    private final Project project;
    private final ApiStatusService statusService;

    public ApiToolWindowPresenter(ApiToolWindowView view, Project project) {
        this.view = view;
        this.project = project;
        this.statusService = new ApiStatusService(project);

        LOG.warn("[ApiToolWindowPresenter] INSTANCE CREATED for project: " + project.getName());

        // Subscribe to events to trigger updates
        project.getMessageBus().connect(this).subscribe(SpecUpdateListener.SPEC_UPDATE_TOPIC, this::onSpecUpdate);

        // VFS_CHANGES requires a full listener implementation, not a method reference.
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                LOG.warn("[ApiToolWindowPresenter] VFS_CHANGES event received.");
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file != null && "java".equals(file.getExtension())) {
                        LOG.warn("[ApiToolWindowPresenter] Java file changed: " + file.getName() + ". Triggering tree update.");
                        loadEndpoints();
                        return; // Exit after the first relevant event
                    }
                }
            }
        });
    }

    /**
     * Kicks off the initial loading of endpoints.
     */
    public void initialize() {
        LOG.warn("[ApiToolWindowPresenter] initialize() called.");
        loadEndpoints();
    }

    /**
     * Loads endpoint data in a background task and updates the view.
     * This method is dumb-aware and will only run when indexes are ready.
     */
    private void loadEndpoints() {
        LOG.warn("[ApiToolWindowPresenter] loadEndpoints() called.");
        DumbService.getInstance(project).runWhenSmart(() -> {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning API Endpoints...") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    LOG.warn("[ApiToolWindowPresenter] Background task run() started.");
                    List<EndpointInfo> endpoints = statusService.getEndpointInfos();
                    LOG.warn("[ApiToolWindowPresenter] Background task finished. Found " + endpoints.size() + " endpoints.");

                    DefaultTreeModel model = buildTreeModel(endpoints);

                    SwingUtilities.invokeLater(() -> view.setTreeModel(model));
                }
            });
        });
    }

    /**
     * Transforms the flat list of endpoints into a hierarchical tree model grouped by tags.
     * This method is pure logic and highly testable.
     *
     * @param endpoints The list of endpoints to display.
     * @return A DefaultTreeModel ready to be displayed by the view.
     */
    private DefaultTreeModel buildTreeModel(List<EndpointInfo> endpoints) {
        LOG.warn("[ApiToolWindowPresenter] buildTreeModel() called.");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("API Endpoints");

        if (endpoints.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No 'openapi.yaml' found or it is empty."));
        } else {
            Map<String, DefaultMutableTreeNode> tagNodes = new TreeMap<>(); // Use TreeMap for sorted tags

            for (EndpointInfo endpoint : endpoints) {
                String tag = endpoint.getTag();
                if (tag == null || tag.trim().isEmpty()) {
                    tag = "General"; // Default tag for untagged endpoints
                }

                DefaultMutableTreeNode tagNode = tagNodes.computeIfAbsent(tag, k -> {
                    DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(k);
                    root.add(newNode);
                    return newNode;
                });
                tagNode.add(new DefaultMutableTreeNode(endpoint));
            }
        }
        return new DefaultTreeModel(root);
    }

    /**
     * Handles the spec update event from the message bus.
     *
     * @param openApi The updated OpenAPI spec (can be null, is ignored).
     */
    private void onSpecUpdate(@Nullable OpenAPI openApi) {
        LOG.warn("[ApiToolWindowPresenter] onSpecUpdate event received.");
        loadEndpoints();
    }

    @Override
    public void dispose() {
        LOG.warn("[ApiToolWindowPresenter] dispose() called.");
        // The message bus connection is automatically disposed when this disposable is disposed.
    }
}
