package io.github.kannann1.microservicebootstrapoperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RBACConfig {
    /**
     * ServiceAccount name to create
     */
    @JsonProperty("serviceAccountName")
    private String serviceAccountName;

    /**
     * List of roles to create
     */
    @JsonProperty("roles")
    private List<String> roles;

    /**
     * List of role bindings to create
     */
    @JsonProperty("roleBindings")
    private List<String> roleBindings;
}
