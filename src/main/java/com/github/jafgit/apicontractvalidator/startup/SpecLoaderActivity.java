package com.github.jafgit.apicontractvalidator.startup;

import com.github.jafgit.apicontractvalidator.core.OpenApiSpecService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpecLoaderActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(SpecLoaderActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super kotlin.Unit> continuation) {
        LOG.warn("[SpecLoaderActivity] execute() called for project: " + project.getName());

        DumbService.getInstance(project).runWhenSmart(() -> {
            LOG.warn("[SpecLoaderActivity] Now in smart mode. Queuing background task.");
            new Task.Backgroundable(project, "Loading OpenAPI Contract") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    LOG.warn("[SpecLoaderActivity] Background task run() started.");
                    indicator.setText("Indexing OpenAPI specification...");
                    LOG.warn("[SpecLoaderActivity] Calling OpenApiSpecService.reloadSpec().");
                    project.getService(OpenApiSpecService.class).reloadSpec();
                    LOG.warn("[SpecLoaderActivity] Background task run() finished.");
                }
            }.queue();
        });

        LOG.warn("[SpecLoaderActivity] Task to run in smart mode has been scheduled.");
        return null;
    }
}
