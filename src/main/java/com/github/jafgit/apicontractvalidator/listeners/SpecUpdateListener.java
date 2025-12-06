package com.github.jafgit.apicontractvalidator.listeners;

import com.intellij.util.messages.Topic;
import io.swagger.v3.oas.models.OpenAPI;
import org.jetbrains.annotations.Nullable;

public interface SpecUpdateListener {
    /**
     * Defines the topic for spec updates.
     */
    Topic<SpecUpdateListener> SPEC_UPDATE_TOPIC = Topic.create("OpenAPI Spec Update", SpecUpdateListener.class);

    /**
     * Called when the OpenAPI specification has been updated.
     *
     * @param openApi The newly loaded OpenAPI object, or null if loading failed.
     */
    void onSpecUpdate(@Nullable OpenAPI openApi);
}