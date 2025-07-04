package io.github.kannann1.microservicebootstrapoperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AppConfigSpec {
    /**
     * Application name
     */
    @JsonProperty("appName")
    private String appName;

    /**
     * GitHub repository URL containing configuration templates
     */
    @JsonProperty("githubRepo")
    private String githubRepo;

    /**
     * Branch or tag to use from the GitHub repository
     */
    @JsonProperty("githubRef")
    private String githubRef;

    /**
     * Path within the repository where config templates are stored
     */
    @JsonProperty("configPath")
    private String configPath;

    /**
     * List of sidecars to inject (legacy field)
     */
    @JsonProperty("sidecars")
    private List<SidecarConfig> sidecars;
    
    /**
     * Sidecar injection configuration
     */
    @JsonProperty("sidecarInjection")
    private SidecarInjectionConfig sidecarInjection;

    /**
     * Secret rotation configuration
     */
    @JsonProperty("secretRotation")
    private SecretRotationConfig secretRotation;

    /**
     * RBAC configuration
     */
    @JsonProperty("rbac")
    private RBACConfig rbac;

    /**
     * Network policy configuration
     */
    @JsonProperty("networkPolicy")
    private NetworkPolicyConfig networkPolicy;
}
