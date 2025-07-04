package io.github.kannann1.microservicebootstrapoperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Condition;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AppConfigStatus {
    /**
     * Conditions represent the latest available observations of an object's state
     */
    @JsonProperty("conditions")
    private List<Condition> conditions = new ArrayList<>();

    /**
     * Last time the configuration was synchronized
     */
    @JsonProperty("lastSyncTime")
    private String lastSyncTime;

    /**
     * Last time secrets were rotated
     */
    @JsonProperty("lastSecretRotationTime")
    private String lastSecretRotationTime;

    /**
     * List of resources created by this AppConfig
     */
    @JsonProperty("createdResources")
    private List<String> createdResources = new ArrayList<>();

    /**
     * Updates the last sync time to now
     */
    public void updateLastSyncTime() {
        this.lastSyncTime = ZonedDateTime.now().toString();
    }

    /**
     * Updates the last secret rotation time to now
     */
    public void updateLastSecretRotationTime() {
        this.lastSecretRotationTime = ZonedDateTime.now().toString();
    }

    /**
     * Adds a created resource to the list if it doesn't already exist
     * 
     * @param resourceName the name of the resource to add
     */
    public void addCreatedResource(String resourceName) {
        if (!createdResources.contains(resourceName)) {
            createdResources.add(resourceName);
        }
    }
}
