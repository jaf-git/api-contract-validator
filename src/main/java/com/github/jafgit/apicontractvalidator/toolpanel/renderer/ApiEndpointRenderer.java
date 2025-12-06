package com.github.jafgit.apicontractvalidator.toolpanel.renderer;

import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public class ApiEndpointRenderer implements TreeCellRenderer {

    private static final Logger LOG = Logger.getInstance(ApiEndpointRenderer.class);

    private final JPanel panel;
    private final JBLabel methodLabel;
    private final JBLabel pathLabel;
    private final JBLabel statusLabel;

    public ApiEndpointRenderer() {
        LOG.warn("[ApiEndpointRenderer] INSTANCE CREATED.");
        panel = new JPanel(new BorderLayout());
        methodLabel = new JBLabel();
        pathLabel = new JBLabel();
        statusLabel = new JBLabel();

        panel.add(methodLabel, BorderLayout.WEST);
        panel.add(pathLabel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.EAST);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        LOG.warn("[ApiEndpointRenderer] getTreeCellRendererComponent() called for value: " + value.toString());
        if (value instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof EndpointInfo) {
                EndpointInfo endpoint = (EndpointInfo) userObject;
                LOG.warn("[ApiEndpointRenderer] Rendering EndpointInfo: " + endpoint.getMethod() + " " + endpoint.getPath() + " with status: " + endpoint.getStatus().name());

                methodLabel.setText(endpoint.getMethod());
                pathLabel.setText(" " + endpoint.getPath());
                statusLabel.setText(" [" + endpoint.getStatus().name() + "] ");

                if (selected) {
                    panel.setBackground(UIManager.getColor("Tree.selectionBackground"));
                } else {
                    panel.setBackground(UIManager.getColor("Tree.background"));
                }
                return panel;
            }
        }
        LOG.warn("[ApiEndpointRenderer] Fallback: Rendering non-EndpointInfo node.");
        return new JBLabel(value.toString());
    }
}
