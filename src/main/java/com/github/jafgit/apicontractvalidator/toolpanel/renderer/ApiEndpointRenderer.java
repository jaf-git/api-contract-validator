package com.github.jafgit.apicontractvalidator.toolpanel.renderer;

import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointStatus;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ApiEndpointRenderer extends JPanel implements TreeCellRenderer {

    private static final Logger LOG = Logger.getInstance(ApiEndpointRenderer.class);

    // --- Styling Constants ---
    private static final int PANEL_HORIZONTAL_GAP = 12;
    private static final float METHOD_FONT_SIZE_MULTIPLIER = 1.0f;
    private static final int METHOD_PADDING_TOP_BOTTOM = 6;
    private static final int METHOD_PADDING_LEFT_RIGHT = 10;
    private static final float PATH_FONT_SIZE_MULTIPLIER = 1.0f;
    private static final float STATUS_FONT_SIZE_MULTIPLIER = 1.0f;
    private static final int STATUS_PADDING_TOP_BOTTOM = 2;
    private static final int STATUS_PADDING_LEFT_RIGHT = 6;
    private static final float TAG_FONT_SIZE_MULTIPLIER = 1.0f;

    private final JPanel panel;
    private final JBLabel methodLabel;
    private final JBLabel pathLabel;
    private final JBLabel statusLabel;

    private static final Map<String, Color> METHOD_COLORS = new HashMap<>();
    static {
        METHOD_COLORS.put("GET", new JBColor(new Color(27, 153, 139), new Color(27, 153, 139)));
        METHOD_COLORS.put("POST", new JBColor(new Color(73, 120, 201), new Color(73, 120, 201)));
        METHOD_COLORS.put("PUT", new JBColor(new Color(252, 161, 48), new Color(252, 161, 48)));
        METHOD_COLORS.put("DELETE", new JBColor(new Color(212, 53, 53), new Color(212, 53, 53)));
        METHOD_COLORS.put("PATCH", new JBColor(new Color(153, 102, 204), new Color(153, 102, 204)));
    }

    private static final JBColor STATUS_IMPLEMENTED_COLOR = new JBColor(new Color(40, 167, 69), new Color(40, 167, 69)); // Green
    private static final JBColor STATUS_NOT_IMPLEMENTED_COLOR = new JBColor(new Color(220, 53, 69), new Color(220, 53, 69)); // Red
    private static final JBColor STATUS_HAS_ISSUES_COLOR = new JBColor(new Color(255, 193, 7), new Color(255, 193, 7)); // Orange/Yellow

    public ApiEndpointRenderer() {
        panel = new JPanel(new BorderLayout(JBUI.scale(PANEL_HORIZONTAL_GAP), 0));
        panel.setOpaque(true);

        methodLabel = new JBLabel();
        methodLabel.setBorder(JBUI.Borders.empty(JBUI.scale(METHOD_PADDING_TOP_BOTTOM), JBUI.scale(METHOD_PADDING_LEFT_RIGHT)));
        methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD, methodLabel.getFont().getSize() * METHOD_FONT_SIZE_MULTIPLIER));

        pathLabel = new JBLabel();
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, pathLabel.getFont().getSize() * PATH_FONT_SIZE_MULTIPLIER));

        statusLabel = new JBLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, statusLabel.getFont().getSize() * STATUS_FONT_SIZE_MULTIPLIER));
        statusLabel.setBorder(JBUI.Borders.empty(JBUI.scale(STATUS_PADDING_TOP_BOTTOM), JBUI.scale(STATUS_PADDING_LEFT_RIGHT)));

        panel.add(methodLabel, BorderLayout.WEST);
        panel.add(pathLabel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.EAST);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        // --- Reset component state ---
        methodLabel.setText("");
        methodLabel.setForeground(UIManager.getColor("Label.foreground"));
        pathLabel.setText("");
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, pathLabel.getFont().getSize() * PATH_FONT_SIZE_MULTIPLIER));
        statusLabel.setText("");
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));

        if (selected) {
            panel.setBackground(UIManager.getColor("Tree.selectionBackground"));
        } else {
            panel.setBackground(UIManager.getColor("Tree.background"));
        }

        if (value instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();

            if (userObject instanceof EndpointInfo) {
                EndpointInfo endpoint = (EndpointInfo) userObject;

                methodLabel.setText(endpoint.getMethod());
                methodLabel.setForeground(METHOD_COLORS.getOrDefault(endpoint.getMethod().toUpperCase(), JBColor.GRAY));

                pathLabel.setText(endpoint.getPath());

                switch (endpoint.getStatus()) {
                    case IMPLEMENTED:
                        statusLabel.setText("Implemented");
                        statusLabel.setForeground(STATUS_IMPLEMENTED_COLOR);
                        break;
                    case NOT_IMPLEMENTED:
                        statusLabel.setText("Not Implemented");
                        statusLabel.setForeground(STATUS_NOT_IMPLEMENTED_COLOR);
                        break;
                    case HAS_ISSUES:
                        statusLabel.setText("Has Issues");
                        statusLabel.setForeground(STATUS_HAS_ISSUES_COLOR);
                        break;
                }

            } else {
                // This is a tag node
                String tag = userObject.toString();
                pathLabel.setText(tag);
                pathLabel.setFont(pathLabel.getFont().deriveFont(Font.BOLD, pathLabel.getFont().getSize() * TAG_FONT_SIZE_MULTIPLIER));
            }
        } else {
            // Fallback for root node
            pathLabel.setText(value.toString());
        }

        return panel;
    }
}
