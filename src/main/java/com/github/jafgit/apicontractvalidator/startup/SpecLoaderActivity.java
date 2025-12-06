package com.github.jafgit.apicontractvalidator.startup;

import com.github.jafgit.apicontractvalidator.services.OpenApiSpecService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java version of the background task that loads the OpenAPI spec.
 */
public class SpecLoaderActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(SpecLoaderActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super kotlin.Unit> continuation) {
        LOG.warn("--- SpecLoaderActivity execute() called for project: " + project.getName() + " ---");

        new Task.Backgroundable(project, "Loading OpenAPI Contract") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                LOG.warn("--- Background task run() started ---");
                indicator.setText("Indexing OpenAPI specification...");
                // Use getService() in Java
                project.getService(OpenApiSpecService.class).reloadSpec();
                LOG.warn("--- Background task run() finished ---");
            }
        }.queue();

        LOG.warn("--- Background task has been queued ---");
        return null;
    }
}
