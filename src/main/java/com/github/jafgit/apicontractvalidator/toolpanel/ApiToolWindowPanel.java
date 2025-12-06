package com.github.jafgit.apicontractvalidator.toolpanel;

import com.github.jafgit.apicontractvalidator.listeners.SpecUpdateListener;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.renderer.ApiEndpointRenderer;
import com.github.jafgit.apicontractvalidator.toolpanel.services.ApiStatusService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import io.swagger.v3.oas.models.OpenAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap; // Use TreeMap to keep tags sorted

public class ApiToolWindowPanel extends JPanel implements Disposable {

    private static final Logger LOG = Logger.getInstance(ApiToolWindowPanel.class);

    private final Tree apiTree;
    private final DefaultTreeModel treeModel;
    private final Project project;

    public ApiToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        LOG.warn("[ApiToolWindowPanel] INSTANCE CREATED for project: " + project.getName());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("API Endpoints");
        treeModel = new DefaultTreeModel(root);
        apiTree = new Tree(treeModel);
        apiTree.setRootVisible(false);
        apiTree.setCellRenderer(new ApiEndpointRenderer());

        JBScrollPane scrollPane = new JBScrollPane(apiTree);
        add(scrollPane, BorderLayout.CENTER);

        LOG.warn("[ApiToolWindowPanel] Subscribing to SPEC_UPDATE_TOPIC.");
        project.getMessageBus().connect(this).subscribe(SpecUpdateListener.SPEC_UPDATE_TOPIC, new SpecUpdateListener() {
            @Override
            public void onSpecUpdate(@Nullable OpenAPI openApi) {
                LOG.warn("[ApiToolWindowPanel] onSpecUpdate event received.");
                updateTreeInBackground();
            }
        });

        LOG.warn("[ApiToolWindowPanel] Subscribing to VFS_CHANGES.");
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                LOG.warn("[ApiToolWindowPanel] VFS_CHANGES event received.");
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file != null && "java".equals(file.getExtension())) {
                        LOG.warn("[ApiToolWindowPanel] Java file changed: " + file.getName() + ". Triggering tree update.");
                        updateTreeInBackground();
                        return;
                    }
                }
            }
        });
    }

    private void updateTreeInBackground() {
        LOG.warn("[ApiToolWindowPanel] updateTreeInBackground() called.");
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning API Endpoints...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                LOG.warn("[ApiToolWindowPanel] Background task run() started.");
                ApiStatusService statusService = new ApiStatusService(project);
                LOG.warn("[ApiToolWindowPanel] Creating ApiStatusService: "+ statusService);
                List<EndpointInfo> endpoints = statusService.getEndpointInfos();
                LOG.warn("[ApiToolWindowPanel] Background task finished. Found " + endpoints.size() + " endpoints.");
                SwingUtilities.invokeLater(() -> {
                    LOG.warn("[ApiToolWindowPanel] Updating UI on EDT.");
                    DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
                    LOG.warn("[ApiToolWindowPanel] root: " + root);
                    root.removeAllChildren();

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
                    treeModel.reload(root);
                    LOG.warn("[ApiToolWindowPanel] UI update complete.");
                });
            }
        });
    }

    @Override
    public void dispose() {
        LOG.warn("[ApiToolWindowPanel] dispose() called.");
    }
}
