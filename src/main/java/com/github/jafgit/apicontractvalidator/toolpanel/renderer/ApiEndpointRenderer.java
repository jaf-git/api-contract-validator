package com.github.jafgit.apicontractvalidator.toolpanel.renderer;

import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointStatus;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D; // Still needed for RoundedCornerBorder if we keep it for other elements, but not for methodLabel
import java.util.HashMap;
import java.util.Map;

public class ApiEndpointRenderer extends JPanel implements TreeCellRenderer {

    private static final Logger LOG = Logger.getInstance(ApiEndpointRenderer.class);

    // --- Styling Constants ---
    private static final int PANEL_HORIZONTAL_GAP = 12;

    private static final float METHOD_FONT_SIZE_MULTIPLIER = 1.0f;
    // METHOD_BORDER_RADIUS is no longer directly used for methodLabel, but kept for potential future use or other elements
    private static final int METHOD_BORDER_RADIUS = 8;
    private static final int METHOD_PADDING_TOP_BOTTOM = 6;
    private static final int METHOD_PADDING_LEFT_RIGHT = 10;

    private static final float PATH_FONT_SIZE_MULTIPLIER = 1.0f;

    private static final float STATUS_FONT_SIZE_MULTIPLIER = 1.0f;
    private static final int STATUS_PADDING_TOP_BOTTOM = 2;
    private static final int STATUS_PADDING_LEFT_RIGHT = 6;

    private static final float TAG_FONT_SIZE_MULTIPLIER = 1.0f;
    // --- End Styling Constants ---

    private final JPanel panel;
    private final JBLabel methodLabel; // Reverted to standard JBLabel
    private final JBLabel pathLabel;
    private final JBLabel statusLabel;

    // Define colors for HTTP methods (similar to Swagger UI)
    private static final Map<String, Color> METHOD_COLORS = new HashMap<>();
    static {
        METHOD_COLORS.put("GET", new JBColor(new Color(27, 153, 139), new Color(27, 153, 139))); // Teal
        METHOD_COLORS.put("POST", new JBColor(new Color(73, 120, 201), new Color(73, 120, 201))); // Blue
        METHOD_COLORS.put("PUT", new JBColor(new Color(252, 161, 48), new Color(252, 161, 48))); // Orange
        METHOD_COLORS.put("DELETE", new JBColor(new Color(212, 53, 53), new Color(212, 53, 53))); // Red
        METHOD_COLORS.put("PATCH", new JBColor(new Color(153, 102, 204), new Color(153, 102, 204))); // Purple
    }

    // Define colors for status
    private static final JBColor STATUS_IMPLEMENTED_COLOR = new JBColor(new Color(40, 167, 69), new Color(40, 167, 69)); // Green
    private static final JBColor STATUS_NOT_IMPLEMENTED_COLOR = new JBColor(new Color(220, 53, 69), new Color(220, 53, 69)); // Red

    // The custom border for rounded corners is no longer needed for methodLabel,
    // so the class can be removed if not used elsewhere. For now, I'll remove it.

    public ApiEndpointRenderer() {
        LOG.warn("[ApiEndpointRenderer] INSTANCE CREATED.");
        panel = new JPanel(new BorderLayout(JBUI.scale(PANEL_HORIZONTAL_GAP), 0));
        panel.setOpaque(true);

        methodLabel = new JBLabel(); // Reverted to standard JBLabel
        methodLabel.setBorder(JBUI.Borders.empty(
                JBUI.scale(METHOD_PADDING_TOP_BOTTOM),
                JBUI.scale(METHOD_PADDING_LEFT_RIGHT),
                JBUI.scale(METHOD_PADDING_TOP_BOTTOM),
                JBUI.scale(METHOD_PADDING_LEFT_RIGHT)
        ));
        methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD, methodLabel.getFont().getSize() * METHOD_FONT_SIZE_MULTIPLIER));
        // Foreground will be set dynamically based on method color

        pathLabel = new JBLabel();
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, pathLabel.getFont().getSize() * PATH_FONT_SIZE_MULTIPLIER));

        statusLabel = new JBLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, statusLabel.getFont().getSize() * STATUS_FONT_SIZE_MULTIPLIER));
        statusLabel.setBorder(JBUI.Borders.empty(
                JBUI.scale(STATUS_PADDING_TOP_BOTTOM),
                JBUI.scale(STATUS_PADDING_LEFT_RIGHT),
                JBUI.scale(STATUS_PADDING_TOP_BOTTOM),
                JBUI.scale(STATUS_PADDING_LEFT_RIGHT)
        ));

        panel.add(methodLabel, BorderLayout.WEST);
        panel.add(pathLabel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.EAST);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        LOG.warn("[ApiEndpointRenderer] getTreeCellRendererComponent() called for value: " + value.toString());

        // --- Reset component state for reuse ---
        methodLabel.setText("");
        methodLabel.setBackground(null); // No background for methodLabel
        methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD, methodLabel.getFont().getSize() * METHOD_FONT_SIZE_MULTIPLIER));
        methodLabel.setForeground(UIManager.getColor("Label.foreground")); // Reset to default for method

        pathLabel.setText("");
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, pathLabel.getFont().getSize() * PATH_FONT_SIZE_MULTIPLIER));
        pathLabel.setForeground(UIManager.getColor("Label.foreground"));

        statusLabel.setText("");
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, statusLabel.getFont().getSize() * STATUS_FONT_SIZE_MULTIPLIER));

        // --- Apply selection background to the main panel ---
        if (selected) {
            panel.setBackground(UIManager.getColor("Tree.selectionBackground"));
        } else {
            panel.setBackground(UIManager.getColor("Tree.background"));
        }

        if (value instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();

            if (userObject instanceof EndpointInfo) {
                EndpointInfo endpoint = (EndpointInfo) userObject;
                LOG.warn("[ApiEndpointRenderer] Rendering EndpointInfo: " + endpoint.getMethod() + " " + endpoint.getPath() + " with status: " + endpoint.getStatus().name());

                // Method Label Styling - now just text color
                methodLabel.setText(endpoint.getMethod()); // No extra spaces, padding handles it
                methodLabel.setForeground(METHOD_COLORS.getOrDefault(endpoint.getMethod().toUpperCase(), JBColor.GRAY));

                // Path Label Styling
                pathLabel.setText(endpoint.getPath());

                // Status Label Styling
                if (endpoint.getStatus() == EndpointStatus.IMPLEMENTED) {
                    statusLabel.setText("Implemented");
                    statusLabel.setForeground(STATUS_IMPLEMENTED_COLOR);
                } else {
                    statusLabel.setText("Not Implemented");
                    statusLabel.setForeground(STATUS_NOT_IMPLEMENTED_COLOR);
                }

            } else {
                // This is a tag node (e.g., "General", "Users", "Products")
                String tag = userObject.toString();
                pathLabel.setText(tag);
                pathLabel.setFont(pathLabel.getFont().deriveFont(Font.BOLD, pathLabel.getFont().getSize() * TAG_FONT_SIZE_MULTIPLIER));
                LOG.warn("[ApiEndpointRenderer] Rendering Tag Node: " + tag);
            }
        } else {
            // Fallback for unexpected node types (e.g., the root node "API Endpoints")
            pathLabel.setText(value.toString());
            LOG.warn("[ApiEndpointRenderer] Fallback: Rendering non-DefaultMutableTreeNode node: " + value.toString());
        }

        return panel;
    }
}
