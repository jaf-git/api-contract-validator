package com.github.jafgit.apicontractvalidator.toolpanel;

import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointInfo;
import com.github.jafgit.apicontractvalidator.toolpanel.model.EndpointStatus;
import com.github.jafgit.apicontractvalidator.toolpanel.renderer.ApiEndpointRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ApiToolWindowPanel extends JPanel implements ApiToolWindowView, Disposable {

    private final Tree apiTree;
    private final ApiToolWindowPresenter presenter;

    public ApiToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.presenter = new ApiToolWindowPresenter(this, project);
        add(createTopPanel(), BorderLayout.NORTH);
        this.apiTree = createTree();
        add(new JBScrollPane(apiTree), BorderLayout.CENTER);
        presenter.initialize();
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Define colors matching the renderer
        Color implementedColor = new JBColor(new Color(40, 167, 69), new Color(40, 167, 69));
        Color notImplementedColor = new JBColor(new Color(220, 53, 69), new Color(220, 53, 69));
        Color hasIssuesColor = new JBColor(new Color(255, 193, 7), new Color(255, 193, 7));

        actionGroup.add(new FilterAction("Implemented", new ColorBoxIcon(implementedColor), EndpointStatus.IMPLEMENTED, presenter));
        actionGroup.add(new FilterAction("Not Implemented", new ColorBoxIcon(notImplementedColor), EndpointStatus.NOT_IMPLEMENTED, presenter));
        actionGroup.add(new FilterAction("Has Issues", new ColorBoxIcon(hasIssuesColor), EndpointStatus.HAS_ISSUES, presenter));

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ApiContractValidatorToolbar", actionGroup, true);
        toolbar.setTargetComponent(this);
        topPanel.add(toolbar.getComponent(), BorderLayout.WEST);

        SearchTextField searchTextField = new SearchTextField();
        searchTextField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                presenter.setSearchText(searchTextField.getText());
            }
        });
        topPanel.add(searchTextField, BorderLayout.CENTER);

        return topPanel;
    }

    private Tree createTree() {
        Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("Initializing...")));
        tree.setRootVisible(false);
        tree.setCellRenderer(new ApiEndpointRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick(e);
                }
            }
        });
        return tree;
    }

    private void handleDoubleClick(MouseEvent e) {
        TreePath path = apiTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        Object node = path.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode && ((DefaultMutableTreeNode) node).getUserObject() instanceof EndpointInfo) {
            EndpointInfo endpointInfo = (EndpointInfo) ((DefaultMutableTreeNode) node).getUserObject();
            SmartPsiElementPointer<?> pointer = endpointInfo.getMethodPointer();
            if (pointer != null) {
                PsiElement element = pointer.getElement();
                if (element instanceof com.intellij.pom.Navigatable) {
                    ((com.intellij.pom.Navigatable) element).navigate(true);
                }
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

    private static class FilterAction extends ToggleAction {
        private final EndpointStatus status;
        private final ApiToolWindowPresenter presenter;

        FilterAction(String text, Icon icon, EndpointStatus status, ApiToolWindowPresenter presenter) {
            super(text, null, icon);
            this.status = status;
            this.presenter = presenter;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return presenter.isStatusEnabled(status);
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            presenter.setStatusFilter(status, state);
        }
    }

    private static class ColorBoxIcon implements Icon {
        private final Color color;
        private static final int SIZE = 12;

        ColorBoxIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, SIZE, SIZE);
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
