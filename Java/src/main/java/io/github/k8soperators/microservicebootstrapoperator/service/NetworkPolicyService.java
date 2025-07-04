package io.github.kannann1.microservicebootstrapoperator.service;

import io.github.kannann1.microservicebootstrapoperator.model.AppConfig;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkPolicyService {

    private final KubernetesClient kubernetesClient;

    /**
     * Sets up NetworkPolicy for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     */
    public void setupNetworkPolicy(AppConfig appConfig) {
        log.info("Setting up NetworkPolicy for: {}/{}", appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
        
        if (appConfig.getSpec().getNetworkPolicy() == null || !appConfig.getSpec().getNetworkPolicy().isEnabled()) {
            log.info("NetworkPolicy not enabled, skipping");
            return;
        }

        String networkPolicyName = String.format("%s-network-policy", appConfig.getSpec().getAppName());
        
        // Create a basic NetworkPolicy
        Map<String, String> matchLabels = new HashMap<>();
        matchLabels.put("app", appConfig.getSpec().getAppName());
        
        LabelSelector podSelector = new LabelSelectorBuilder()
                .withMatchLabels(matchLabels)
                .build();
        
        NetworkPolicy networkPolicy = new NetworkPolicyBuilder()
                .withNewMetadata()
                    .withName(networkPolicyName)
                    .withNamespace(appConfig.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReference(appConfig))
                .endMetadata()
                .withNewSpec()
                    .withPodSelector(podSelector)
                    // In a real implementation, ingress and egress rules would be configured
                    // based on appConfig.getSpec().getNetworkPolicy().getIngress() and appConfig.getSpec().getNetworkPolicy().getEgress()
                .endSpec()
                .build();

        Resource<NetworkPolicy> networkPolicyResource = kubernetesClient.network().networkPolicies()
                .inNamespace(appConfig.getMetadata().getNamespace())
                .withName(networkPolicyName);

        if (networkPolicyResource.get() == null) {
            kubernetesClient.network().networkPolicies()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(networkPolicy)
                    .create();
            log.info("Created NetworkPolicy: {}/{}", appConfig.getMetadata().getNamespace(), networkPolicyName);
        } else {
            kubernetesClient.network().networkPolicies()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(networkPolicy)
                    .serverSideApply();
            log.info("Updated NetworkPolicy: {}/{}", appConfig.getMetadata().getNamespace(), networkPolicyName);
        }

        // Track created resource
        String resourceName = String.format("NetworkPolicy/%s", networkPolicyName);
        if (appConfig.getStatus() != null) {
            appConfig.getStatus().addCreatedResource(resourceName);
        }
    }

    /**
     * Creates an owner reference for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     * @return the owner reference
     */
    private io.fabric8.kubernetes.api.model.OwnerReference createOwnerReference(AppConfig appConfig) {
        return new io.fabric8.kubernetes.api.model.OwnerReferenceBuilder()
                .withApiVersion(appConfig.getApiVersion())
                .withKind(appConfig.getKind())
                .withName(appConfig.getMetadata().getName())
                .withUid(appConfig.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .build();
    }
}
