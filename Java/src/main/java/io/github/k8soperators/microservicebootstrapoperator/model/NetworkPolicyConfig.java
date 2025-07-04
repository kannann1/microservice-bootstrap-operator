package io.github.k8soperators.microservicebootstrapoperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class NetworkPolicyConfig {
    /**
     * Enable network policy
     */
    @JsonProperty("enabled")
    private boolean enabled;

    /**
     * Ingress rules
     */
    @JsonProperty("ingress")
    private List<String> ingress;

    /**
     * Egress rules
     */
    @JsonProperty("egress")
    private List<String> egress;
}
