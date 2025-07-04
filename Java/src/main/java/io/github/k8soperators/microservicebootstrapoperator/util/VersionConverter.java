package io.github.kannann1.microservicebootstrapoperator.util;

import io.github.kannann1.microservicebootstrapoperator.model.AppConfig;
import io.github.kannann1.microservicebootstrapoperator.model.NetworkPolicyConfig;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Utility class for handling version conversions of AppConfig CRs
 * This implements the "Hub and Spoke" conversion pattern similar to controller-runtime
 */
@Slf4j
public class VersionConverter {

    private static final String VERSION_LABEL = "microservice.example.com/version";
    private static final String CURRENT_VERSION = "v1";

    /**
     * Checks if the AppConfig needs conversion and performs the conversion if needed
     * 
     * @param appConfig The AppConfig to check and potentially convert
     * @return true if conversion was performed, false otherwise
     */
    public static boolean checkAndConvertIfNeeded(AppConfig appConfig) {
        String version = getVersion(appConfig);
        
        if (version == null) {
            // No version label, assume it's a new resource
            setVersion(appConfig, CURRENT_VERSION);
            return false;
        }
        
        if (version.equals(CURRENT_VERSION)) {
            // Already at current version
            return false;
        }
        
        // Perform conversion based on version
        switch (version) {
            case "v1alpha1":
                convertFromV1Alpha1(appConfig);
                break;
            case "v1beta1":
                convertFromV1Beta1(appConfig);
                break;
            default:
                log.warn("Unknown version: {}, attempting best-effort conversion", version);
                performBestEffortConversion(appConfig);
        }
        
        // Update version to current
        setVersion(appConfig, CURRENT_VERSION);
        return true;
    }
    
    private static String getVersion(HasMetadata resource) {
        Map<String, String> labels = resource.getMetadata().getLabels();
        if (labels == null) {
            return null;
        }
        return labels.get(VERSION_LABEL);
    }
    
    private static void setVersion(HasMetadata resource, String version) {
        Map<String, String> labels = resource.getMetadata().getLabels();
        if (labels == null) {
            resource.getMetadata().setLabels(Map.of(VERSION_LABEL, version));
        } else {
            labels.put(VERSION_LABEL, version);
        }
    }
    
    /**
     * Convert from v1alpha1 to current version
     */
    private static void convertFromV1Alpha1(AppConfig appConfig) {
        log.info("Converting AppConfig from v1alpha1 to {}: {}/{}", 
                CURRENT_VERSION, appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
        
        // Example conversion logic:
        // 1. In v1alpha1, networkPolicy might have been a boolean instead of an object
        // Note: In a real implementation, we would need to add a Map<String, Object> additionalProperties
        // field to our model classes and annotate it with @JsonAnyGetter/@JsonAnySetter
        // For now, we'll just use a simplified approach for demonstration
        
        // This is a simplified example - in a real implementation, we would handle this differently
        // by properly implementing additionalProperties in our model classes
        Map<String, String> annotations = appConfig.getMetadata().getAnnotations();
        if (appConfig.getSpec().getNetworkPolicy() == null && 
                annotations != null && annotations.containsKey("enableNetworkPolicy")) {
            String enableNetworkPolicyStr = annotations.get("enableNetworkPolicy");
            boolean enableNetworkPolicy = Boolean.parseBoolean(enableNetworkPolicyStr);
            if (enableNetworkPolicy) {
                // Create a new NetworkPolicyConfig object
                appConfig.getSpec().setNetworkPolicy(new NetworkPolicyConfig());
                appConfig.getSpec().getNetworkPolicy().setEnabled(true);
            }
            // Remove the old annotation
            annotations.remove("enableNetworkPolicy");
        }
        
        // Add more conversion logic as needed
    }
    
    /**
     * Convert from v1beta1 to current version
     */
    private static void convertFromV1Beta1(AppConfig appConfig) {
        log.info("Converting AppConfig from v1beta1 to {}: {}/{}", 
                CURRENT_VERSION, appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
        
        // Example conversion logic:
        // In v1beta1, secretRotation.sources might have been a string instead of an array
        // Note: In a real implementation, we would need to add a Map<String, Object> additionalProperties
        // field to our model classes and annotate it with @JsonAnyGetter/@JsonAnySetter
        // For now, we'll just use a simplified approach for demonstration
        
        Map<String, String> annotations = appConfig.getMetadata().getAnnotations();
        if (appConfig.getSpec().getSecretRotation() != null && 
                annotations != null && annotations.containsKey("secretRotationSource")) {
            String source = annotations.get("secretRotationSource");
            if (source != null && !source.isEmpty()) {
                appConfig.getSpec().getSecretRotation().setSources(java.util.Collections.singletonList(source));
            }
            // Remove the old annotation
            annotations.remove("secretRotationSource");
        }
        
        // Add more conversion logic as needed
    }
    
    /**
     * Perform best-effort conversion for unknown versions
     */
    private static void performBestEffortConversion(AppConfig appConfig) {
        log.info("Performing best-effort conversion for AppConfig: {}/{}", 
                appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
        
        // Try both known conversion methods
        convertFromV1Alpha1(appConfig);
        convertFromV1Beta1(appConfig);
    }
}
