package io.github.kannann1.microservicebootstrapoperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Configuration for secret rotation
 */
@Data
public class SecretRotationConfig {
    /**
     * Enable secret rotation
     */
    @JsonProperty("enabled")
    private boolean enabled;

    /**
     * Rotation interval in hours
     */
    @JsonProperty("intervalHours")
    private int intervalHours = 24; // Default to 24 hours if not specified

    /**
     * Secret sources to rotate
     */
    @JsonProperty("sources")
    private List<String> sources;
    
    /**
     * Rotation strategy to use (default, database, api-key, tls)
     */
    @JsonProperty("strategy")
    private String strategy = "default";
    
    /**
     * Additional configuration parameters for the rotation strategy
     */
    @JsonProperty("strategyConfig")
    private Map<String, String> strategyConfig;
}
