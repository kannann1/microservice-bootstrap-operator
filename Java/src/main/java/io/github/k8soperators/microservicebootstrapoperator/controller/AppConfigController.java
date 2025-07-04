package io.github.k8soperators.microservicebootstrapoperator.controller;

import io.github.k8soperators.microservicebootstrapoperator.model.AppConfig;
import io.github.k8soperators.microservicebootstrapoperator.service.ConfigMapService;
import io.github.k8soperators.microservicebootstrapoperator.service.NetworkPolicyService;
import io.github.k8soperators.microservicebootstrapoperator.service.RBACService;
import io.github.k8soperators.microservicebootstrapoperator.service.SecretRotationService;
import io.github.k8soperators.microservicebootstrapoperator.service.SidecarInjectionService;
import io.github.k8soperators.microservicebootstrapoperator.util.RetryUtil;
import io.github.k8soperators.microservicebootstrapoperator.util.VersionConverter;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.api.reconciler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppConfigController implements Reconciler<AppConfig>, ErrorStatusHandler<AppConfig> {

    private static final String FINALIZER_NAME = "microservice.example.com/finalizer";

    private final KubernetesClient kubernetesClient;
    private final ConfigMapService configMapService;
    private final RBACService rbacService;
    private final NetworkPolicyService networkPolicyService;
    private final SecretRotationService secretRotationService;
    private final SidecarInjectionService sidecarInjectionService;

    // Event sources are configured automatically by the framework
    // No need to override prepareEventSources in newer SDK versions

    @Override
    public UpdateControl<AppConfig> reconcile(AppConfig appConfig, Context<AppConfig> context) {
        log.info("Reconciling AppConfig: {}/{}", appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());

        // Check if the resource is being deleted
        if (appConfig.getMetadata().getDeletionTimestamp() != null) {
            return handleDeletion(appConfig);
        }

        // Add finalizer if it doesn't exist
        if (!hasFinalizer(appConfig)) {
            addFinalizer(appConfig);
            return UpdateControl.updateResource(appConfig);
        }
        
        // Check if version conversion is needed
        boolean wasConverted = VersionConverter.checkAndConvertIfNeeded(appConfig);
        if (wasConverted) {
            log.info("AppConfig was converted to the latest version, updating CR");
            return UpdateControl.updateResource(appConfig);
        }

        try {
            // Use retry utility for GitHub operations which might be flaky
            RetryUtil.executeWithRetry(
                () -> configMapService.syncConfigFromGitHub(appConfig),
                3, // max retries
                1000, // initial backoff in ms
                10000 // max backoff in ms
            );

            // Setup RBAC if configured - idempotent operation
            if (appConfig.getSpec().getRbac() != null) {
                rbacService.setupRBAC(appConfig);
            }

            // Setup NetworkPolicy if configured - idempotent operation
            if (appConfig.getSpec().getNetworkPolicy() != null && appConfig.getSpec().getNetworkPolicy().isEnabled()) {
                networkPolicyService.setupNetworkPolicy(appConfig);
            }
            
            // Register for sidecar injection if configured
            if (appConfig.getSpec().getSidecarInjection() != null && appConfig.getSpec().getSidecarInjection().isEnabled()) {
                sidecarInjectionService.registerAppConfig(appConfig);
            }

            // Handle secret rotation if enabled
            if (appConfig.getSpec().getSecretRotation() != null && appConfig.getSpec().getSecretRotation().isEnabled()) {
                boolean shouldRotate = shouldRotateSecrets(appConfig);
                if (shouldRotate) {
                    // Secret rotation with retry for resilience
                    RetryUtil.executeWithRetry(
                        () -> secretRotationService.rotateSecrets(appConfig),
                        3, // max retries
                        1000, // initial backoff in ms
                        10000 // max backoff in ms
                    );
                    appConfig.getStatus().updateLastSecretRotationTime();
                }
            }

            // Update status
            appConfig.getStatus().updateLastSyncTime();
            addSuccessCondition(appConfig);

            // Determine if we need to requeue based on secret rotation interval
            if (appConfig.getSpec().getSecretRotation() != null && appConfig.getSpec().getSecretRotation().isEnabled()) {
                int intervalHours = appConfig.getSpec().getSecretRotation().getIntervalHours();
                return UpdateControl.updateStatus(appConfig)
                        .rescheduleAfter(intervalHours * 60 * 60 * 1000); // Convert hours to milliseconds
            }

            return UpdateControl.updateStatus(appConfig);
        } catch (KubernetesClientException e) {
            log.error("Kubernetes API error reconciling AppConfig: {}", e.getMessage(), e);
            addErrorCondition(appConfig, "Kubernetes API error: " + e.getMessage());
            
            // For API server errors, requeue with backoff
            int statusCode = e.getCode();
            if (statusCode >= 500) {
                // Server error, retry with longer backoff
                return UpdateControl.updateStatus(appConfig)
                        .rescheduleAfter(30, TimeUnit.SECONDS);
            } else if (statusCode == 429) {
                // Too many requests, retry with backoff
                return UpdateControl.updateStatus(appConfig)
                        .rescheduleAfter(10, TimeUnit.SECONDS);
            }
            
            return UpdateControl.updateStatus(appConfig);
        } catch (Exception e) {
            log.error("Error reconciling AppConfig", e);
            addErrorCondition(appConfig, e.getMessage());
            return UpdateControl.updateStatus(appConfig);
        }
    }

    @Override
    public ErrorStatusUpdateControl<AppConfig> updateErrorStatus(AppConfig resource, Context<AppConfig> context, Exception e) {
        log.error("Error during reconciliation", e);
        addErrorCondition(resource, e.getMessage());
        return ErrorStatusUpdateControl.updateStatus(resource);
    }

    private UpdateControl<AppConfig> handleDeletion(AppConfig appConfig) {
        if (hasFinalizer(appConfig)) {
            log.info("Finalizing AppConfig: {}/{}", appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
            
            try {
                // Unregister from sidecar injection if configured
                if (appConfig.getSpec().getSidecarInjection() != null && appConfig.getSpec().getSidecarInjection().isEnabled()) {
                    sidecarInjectionService.unregisterAppConfig(appConfig);
                }
                
                // Clean up resources created by this AppConfig
                if (appConfig.getStatus() != null && appConfig.getStatus().getCreatedResources() != null) {
                    for (String resourceName : appConfig.getStatus().getCreatedResources()) {
                        log.info("Cleaning up resource: {}", resourceName);
                        try {
                            // Use retry for deletion to ensure resources are properly cleaned up
                            RetryUtil.executeWithRetry(
                                () -> cleanupResource(resourceName, appConfig),
                                3, // max retries
                                1000, // initial backoff in ms
                                5000 // max backoff in ms
                            );
                        } catch (Exception e) {
                            // Log but continue with other resources
                            log.warn("Failed to clean up resource: {}, continuing with others", resourceName, e);
                        }
                    }
                }
                
                removeFinalizer(appConfig);
                return UpdateControl.updateResource(appConfig);
            } catch (Exception e) {
                log.error("Error during finalization", e);
                addErrorCondition(appConfig, "Finalization failed: " + e.getMessage());
                return UpdateControl.updateStatus(appConfig);
            }
        }
        
        return UpdateControl.noUpdate();
    }
    
    /**
     * Clean up a specific resource created by this AppConfig
     * 
     * @param resourceName The resource name in format "kind:namespace/name"
     * @param appConfig The parent AppConfig
     */
    private void cleanupResource(String resourceName, AppConfig appConfig) {
        // Parse the resource type and name
        String[] parts = resourceName.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid resource name format: {}", resourceName);
            return;
        }
        
        String kind = parts[0];
        String namespacedName = parts[1];
        String[] nameParts = namespacedName.split("/", 2);
        
        if (nameParts.length != 2) {
            log.warn("Invalid namespaced name format: {}", namespacedName);
            return;
        }
        
        String namespace = nameParts[0];
        String name = nameParts[1];
        
        log.info("Deleting resource of kind {} with name {}/{}", kind, namespace, name);
        
        // Delete the resource based on its kind
        switch (kind.toLowerCase()) {
            case "configmap":
                kubernetesClient.configMaps().inNamespace(namespace).withName(name).delete();
                break;
            case "secret":
                kubernetesClient.secrets().inNamespace(namespace).withName(name).delete();
                break;
            case "serviceaccount":
                kubernetesClient.serviceAccounts().inNamespace(namespace).withName(name).delete();
                break;
            case "role":
                kubernetesClient.rbac().roles().inNamespace(namespace).withName(name).delete();
                break;
            case "rolebinding":
                kubernetesClient.rbac().roleBindings().inNamespace(namespace).withName(name).delete();
                break;
            case "networkpolicy":
                kubernetesClient.network().networkPolicies().inNamespace(namespace).withName(name).delete();
                break;
            default:
                log.warn("Unknown resource kind: {}", kind);
        }
    }

    private boolean shouldRotateSecrets(AppConfig appConfig) {
        if (appConfig.getStatus().getLastSecretRotationTime() == null) {
            return true;
        }
        
        try {
            ZonedDateTime lastRotation = ZonedDateTime.parse(appConfig.getStatus().getLastSecretRotationTime());
            ZonedDateTime nextRotation = lastRotation.plusHours(appConfig.getSpec().getSecretRotation().getIntervalHours());
            return ZonedDateTime.now().isAfter(nextRotation);
        } catch (Exception e) {
            log.warn("Error parsing last rotation time, will rotate secrets", e);
            return true;
        }
    }

    private boolean hasFinalizer(AppConfig resource) {
        return Optional.ofNullable(resource.getMetadata().getFinalizers())
                .map(finalizers -> finalizers.contains(FINALIZER_NAME))
                .orElse(false);
    }

    private void addFinalizer(AppConfig resource) {
        resource.getMetadata().getFinalizers().add(FINALIZER_NAME);
    }

    private void removeFinalizer(AppConfig resource) {
        resource.getMetadata().getFinalizers().remove(FINALIZER_NAME);
    }

    private void addSuccessCondition(AppConfig resource) {
        Condition condition = new Condition();
        condition.setType("Reconciled");
        condition.setStatus("True");
        condition.setReason("ReconciliationSucceeded");
        condition.setMessage("AppConfig reconciled successfully");
        condition.setLastTransitionTime(ZonedDateTime.now().toString());
        
        if (resource.getStatus().getConditions() == null) {
            resource.getStatus().setConditions(java.util.Collections.singletonList(condition));
        } else {
            resource.getStatus().getConditions().add(condition);
        }
    }

    private void addErrorCondition(AppConfig resource, String errorMessage) {
        Condition condition = new Condition();
        condition.setType("Reconciled");
        condition.setStatus("False");
        condition.setReason("ReconciliationFailed");
        condition.setMessage(errorMessage);
        condition.setLastTransitionTime(ZonedDateTime.now().toString());
        
        if (resource.getStatus().getConditions() == null) {
            resource.getStatus().setConditions(java.util.Collections.singletonList(condition));
        } else {
            resource.getStatus().getConditions().add(condition);
        }
    }
}
