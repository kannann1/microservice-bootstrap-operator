package io.github.kannann1.microservicebootstrapoperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Configuration for sidecar injection
 */
@Data
public class SidecarInjectionConfig {
    /**
     * Whether sidecar injection is enabled
     */
    @JsonProperty("enabled")
    private boolean enabled;

    /**
     * Name of the sidecar container
     */
    @JsonProperty("name")
    private String name;

    /**
     * Docker image for the sidecar
     */
    @JsonProperty("image")
    private String image;

    /**
     * Labels to select pods for sidecar injection
     */
    @JsonProperty("selectorLabels")
    private Map<String, String> selectorLabels;

    /**
     * Environment variables for the sidecar
     */
    @JsonProperty("env")
    private Map<String, String> env;

    /**
     * Volumes to create for the sidecar
     */
    @JsonProperty("volumes")
    private List<String> volumes;

    /**
     * Volume mounts for the sidecar (volume name -> mount path)
     */
    @JsonProperty("volumeMounts")
    private Map<String, String> volumeMounts;
}
