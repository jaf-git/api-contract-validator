package com.github.jafgit.apicontractvalidator.toolpanel.model;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * A model representing the filtering criteria for the API endpoints tree.
 * This encapsulates the filtering logic, making it testable and extensible.
 */
public class EndpointFilter {

    private String searchText = "";
    private final Set<EndpointStatus> allowedStatuses = EnumSet.allOf(EndpointStatus.class);

    public void setSearchText(@NotNull String searchText) {
        this.searchText = searchText.toLowerCase();
    }

    public void setStatusFilter(EndpointStatus status, boolean isEnabled) {
        if (isEnabled) {
            allowedStatuses.add(status);
        } else {
            allowedStatuses.remove(status);
        }
    }

    public boolean isStatusEnabled(EndpointStatus status) {
        return allowedStatuses.contains(status);
    }

    /**
     * Checks if a given EndpointInfo matches the current filter criteria.
     *
     * @param endpoint The endpoint to check.
     * @return true if the endpoint matches, false otherwise.
     */
    public boolean matches(EndpointInfo endpoint) {
        boolean statusMatches = allowedStatuses.contains(endpoint.getStatus());
        if (!statusMatches) {
            return false;
        }

        boolean searchMatches = searchText.isEmpty() || endpoint.getPath().toLowerCase().contains(searchText);
        return searchMatches;
    }
}
