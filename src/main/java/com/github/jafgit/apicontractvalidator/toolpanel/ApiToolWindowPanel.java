package com.github.jafgit.apicontractvalidator.toolpanel;

import com.github.jafgit.apicontractvalidator.listeners.SpecUpdateListener;
import com.github.jafgit.apicontractvalidator.services.OpenApiSpecService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Map;

public class ApiToolWindowPanel extends JPanel {

    private final Tree apiTree;
    private final DefaultTreeModel treeModel;

    public ApiToolWindowPanel(Project project) {
        super(new BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenAPI Paths");
        treeModel = new DefaultTreeModel(root);
        apiTree = new Tree(treeModel);
        apiTree.setRootVisible(false);

        JBScrollPane scrollPane = new JBScrollPane(apiTree);
        add(scrollPane, BorderLayout.CENTER);

        // Listen for future spec updates
        project.getMessageBus().connect().subscribe(SpecUpdateListener.SPEC_UPDATE_TOPIC, new SpecUpdateListener() {
            @Override
            public void onSpecUpdate(@Nullable OpenAPI openApi) {
                SwingUtilities.invokeLater(() -> updateTree(openApi));
            }
        });

        // Fetch the initial state immediately
        OpenApiSpecService specService = project.getService(OpenApiSpecService.class);
        if (specService != null) {
            updateTree(specService.getSpec());
        }
    }

    private void updateTree(OpenAPI openApi) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        if (openApi != null && openApi.getPaths() != null && !openApi.getPaths().isEmpty()) {
            for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
                DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(entry.getKey());
                addOperations(pathNode, entry.getValue());
                root.add(pathNode);
            }
        } else {
            DefaultMutableTreeNode emptyNode = new DefaultMutableTreeNode("No 'openapi.yaml' found or it is empty.");
            root.add(emptyNode);
        }

        treeModel.reload(root);
    }

    private void addOperations(DefaultMutableTreeNode pathNode, PathItem pathItem) {
        if (pathItem.getGet() != null) pathNode.add(new DefaultMutableTreeNode("GET"));
        if (pathItem.getPost() != null) pathNode.add(new DefaultMutableTreeNode("POST"));
        if (pathItem.getPut() != null) pathNode.add(new DefaultMutableTreeNode("PUT"));
        if (pathItem.getDelete() != null) pathNode.add(new DefaultMutableTreeNode("DELETE"));
        if (pathItem.getPatch() != null) pathNode.add(new DefaultMutableTreeNode("PATCH"));
    }
}
