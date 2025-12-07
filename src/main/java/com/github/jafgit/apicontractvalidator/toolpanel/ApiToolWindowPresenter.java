package com.github.jafgit.apicontractvalidator.toolpanel;

import com.github.jafgit.apicontractvalidator.listeners.SpecUpdateListener;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointFilter;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ApiToolWindowPresenter implements Disposable {

    private static final Logger LOG = Logger.getInstance(ApiToolWindowPresenter.class);

    private final ApiToolWindowView view;
    private final Project project;
    private final ApiStatusService statusService;
    private final EndpointFilter filter = new EndpointFilter();
    private List<EndpointInfo> masterEndpointList = new ArrayList<>();

    public ApiToolWindowPresenter(ApiToolWindowView view, Project project) {
        this.view = view;
        this.project = project;
        this.statusService = new ApiStatusService(project);

        project.getMessageBus().connect(this).subscribe(SpecUpdateListener.SPEC_UPDATE_TOPIC, this::onSpecUpdate);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file != null && "java".equals(file.getExtension())) {
                        loadEndpoints();
                        return;
                    }
                }
            }
        });
    }

    public void initialize() {
        loadEndpoints();
    }

    private void loadEndpoints() {
        DumbService.getInstance(project).runWhenSmart(() -> {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning API Endpoints...") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    masterEndpointList = statusService.getEndpointInfos();
                    updateView();
                }
            });
        });
    }

    private void updateView() {
        List<EndpointInfo> filteredEndpoints = masterEndpointList.stream()
                .filter(filter::matches)
                .collect(Collectors.toList());
        DefaultTreeModel model = buildTreeModel(filteredEndpoints);
        SwingUtilities.invokeLater(() -> view.setTreeModel(model));
    }

    private DefaultTreeModel buildTreeModel(List<EndpointInfo> endpoints) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("API Endpoints");

        if (endpoints.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No endpoints match the current filter."));
        } else {
            Map<String, DefaultMutableTreeNode> tagNodes = new TreeMap<>();
            for (EndpointInfo endpoint : endpoints) {
                String tag = endpoint.getTag() != null ? endpoint.getTag() : "General";
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

    public void setSearchText(@NotNull String searchText) {
        filter.setSearchText(searchText);
        updateView();
    }

    public void setStatusFilter(EndpointStatus status, boolean isEnabled) {
        filter.setStatusFilter(status, isEnabled);
        updateView();
    }

    public boolean isStatusEnabled(EndpointStatus status) {
        return filter.isStatusEnabled(status);
    }

    private void onSpecUpdate(@Nullable OpenAPI openApi) {
        loadEndpoints();
    }

    @Override
    public void dispose() {
    }
}
