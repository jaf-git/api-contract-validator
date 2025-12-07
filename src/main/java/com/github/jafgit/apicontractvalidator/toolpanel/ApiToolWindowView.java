package com.github.jafgit.apicontractvalidator.toolpanel;

import javax.swing.tree.DefaultTreeModel;

/**
 * Defines the contract for the View in the MVP pattern for the API Tool Window.
 * The View is responsible for displaying data and forwarding user events to the Presenter.
 */
public interface ApiToolWindowView {

    /**
     * Sets or updates the tree model displayed in the view.
     *
     * @param model The tree model to be rendered.
     */
    void setTreeModel(DefaultTreeModel model);
}
