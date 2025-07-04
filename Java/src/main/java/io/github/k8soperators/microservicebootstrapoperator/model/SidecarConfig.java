package io.github.k8soperators.microservicebootstrapoperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class SidecarConfig {
    /**
     * Name of the sidecar
     */
    @JsonProperty("name")
    private String name;

    /**
     * Image to use for the sidecar
     */
    @JsonProperty("image")
    private String image;

    /**
     * Type of sidecar (e.g., "istio", "vault-agent")
     */
    @JsonProperty("type")
    private String type;

    /**
     * Additional configuration for the sidecar
     */
    @JsonProperty("config")
    private Map<String, String> config;
}
