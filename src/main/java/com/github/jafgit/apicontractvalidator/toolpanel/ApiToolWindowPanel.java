package com.github.jafgit.apicontractvalidator.toolpanel;

import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.renderer.ApiEndpointRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The View in the MVP pattern for the API Tool Window.
 * This class is a "dumb" component responsible only for building and displaying the UI.
 * It delegates all logic to the ApiToolWindowPresenter.
 */
public class ApiToolWindowPanel extends JPanel implements ApiToolWindowView, Disposable {

    private final Tree apiTree;
    private final ApiToolWindowPresenter presenter;

    public ApiToolWindowPanel(Project project) {
        super(new BorderLayout());

        this.presenter = new ApiToolWindowPresenter(this, project);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Initializing...");
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        apiTree = new Tree(treeModel);
        apiTree.setRootVisible(false);
        apiTree.setCellRenderer(new ApiEndpointRenderer());

        // Add mouse listener for navigation
        apiTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(apiTree);
        add(scrollPane, BorderLayout.CENTER);

        presenter.initialize();
    }

    private void handleDoubleClick() {
        TreePath path = apiTree.getSelectionPath();
        if (path == null) return;

        Object lastPathComponent = path.getLastPathComponent();
        if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return;

        Object userObject = ((DefaultMutableTreeNode) lastPathComponent).getUserObject();
        if (!(userObject instanceof EndpointInfo)) return;

        EndpointInfo endpointInfo = (EndpointInfo) userObject;
        SmartPsiElementPointer<?> pointer = endpointInfo.getMethodPointer();

        if (pointer != null) {
            PsiElement element = pointer.getElement();
            if (element instanceof com.intellij.pom.Navigatable) {
                ((com.intellij.pom.Navigatable) element).navigate(true);
            }
        }
    }

    @Override
    public void setTreeModel(DefaultTreeModel newModel) {
        apiTree.setModel(newModel);
    }

    @Override
    public void dispose() {
        Disposer.dispose(presenter);
    }
}
