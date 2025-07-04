package io.github.k8soperators.microservicebootstrapoperator.service;

import io.github.k8soperators.microservicebootstrapoperator.model.AppConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for injecting sidecars into pods based on AppConfig specifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SidecarInjectionService {

    private final KubernetesClient kubernetesClient;
    private SharedIndexInformer<Pod> podInformer;
    private final Map<String, AppConfig> appConfigCache = new ConcurrentHashMap<>();

    /**
     * Initialize the pod informer to watch for pod creation events
     */
    @PostConstruct
    public void init() {
        log.info("Initializing SidecarInjectionService");
        
        // Create a pod informer that watches all namespaces
        podInformer = kubernetesClient.pods().inAnyNamespace().inform();
        
        // Add event handler for pod events
        podInformer.addEventHandler(new ResourceEventHandler<Pod>() {
            @Override
            public void onAdd(Pod pod) {
                handlePodCreation(pod);
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                // We only care about pod creation for sidecar injection
            }

            @Override
            public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                // We don't need to handle pod deletion
            }
        });
    }

    /**
     * Clean up resources when the service is destroyed
     */
    @PreDestroy
    public void cleanup() {
        if (podInformer != null) {
            podInformer.stop();
        }
    }

    /**
     * Register an AppConfig for sidecar injection
     *
     * @param appConfig the AppConfig to register
     */
    public void registerAppConfig(AppConfig appConfig) {
        if (appConfig.getSpec().getSidecarInjection() != null && appConfig.getSpec().getSidecarInjection().isEnabled()) {
            String key = appConfig.getMetadata().getNamespace() + "/" + appConfig.getMetadata().getName();
            appConfigCache.put(key, appConfig);
            log.info("Registered AppConfig for sidecar injection: {}", key);
        }
    }

    /**
     * Unregister an AppConfig for sidecar injection
     *
     * @param appConfig the AppConfig to unregister
     */
    public void unregisterAppConfig(AppConfig appConfig) {
        String key = appConfig.getMetadata().getNamespace() + "/" + appConfig.getMetadata().getName();
        appConfigCache.remove(key);
        log.info("Unregistered AppConfig for sidecar injection: {}", key);
    }

    /**
     * Handle pod creation event
     *
     * @param pod the created pod
     */
    public void handlePodCreation(Pod pod) {
        // Skip if pod is already being deleted
        if (pod.getMetadata().getDeletionTimestamp() != null) {
            return;
        }
        
        // Check if the pod matches any registered AppConfig
        for (AppConfig appConfig : appConfigCache.values()) {
            if (shouldInjectSidecar(pod, appConfig)) {
                injectSidecar(pod, appConfig);
                break;
            }
        }
    }

    /**
     * Check if a sidecar should be injected into the pod
     *
     * @param pod the pod to check
     * @param appConfig the AppConfig to check against
     * @return true if the sidecar should be injected
     */
    public boolean shouldInjectSidecar(Pod pod, AppConfig appConfig) {
        // Skip if pod is not in the same namespace as the AppConfig
        if (!pod.getMetadata().getNamespace().equals(appConfig.getMetadata().getNamespace())) {
            return false;
        }
        
        // Check if the pod has the required labels
        Map<String, String> podLabels = pod.getMetadata().getLabels();
        if (podLabels == null) {
            return false;
        }
        
        Map<String, String> selectorLabels = appConfig.getSpec().getSidecarInjection().getSelectorLabels();
        if (selectorLabels == null || selectorLabels.isEmpty()) {
            return false;
        }
        
        // Check if all selector labels match
        for (Map.Entry<String, String> entry : selectorLabels.entrySet()) {
            String labelValue = podLabels.get(entry.getKey());
            if (labelValue == null || !labelValue.equals(entry.getValue())) {
                return false;
            }
        }
        
        // Check if the pod already has the sidecar
        if (pod.getSpec().getContainers() != null) {
            for (Container container : pod.getSpec().getContainers()) {
                if (container.getName().equals(getSidecarName(appConfig))) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Inject a sidecar into the pod
     *
     * @param pod the pod to inject the sidecar into
     * @param appConfig the AppConfig containing sidecar configuration
     */
    public void injectSidecar(Pod pod, AppConfig appConfig) {
        log.info("Injecting sidecar into pod: {}/{}", pod.getMetadata().getNamespace(), pod.getMetadata().getName());
        
        try {
            // Create sidecar container
            Container sidecar = createSidecarContainer(appConfig);
            
            // Add volumes if needed
            List<Volume> volumes = new ArrayList<>();
            if (pod.getSpec().getVolumes() != null) {
                volumes.addAll(pod.getSpec().getVolumes());
            }
            
            // Add sidecar-specific volumes
            if (appConfig.getSpec().getSidecarInjection().getVolumes() != null) {
                for (String volumeName : appConfig.getSpec().getSidecarInjection().getVolumes()) {
                    volumes.add(new VolumeBuilder()
                            .withName(volumeName)
                            .withNewEmptyDir()
                            .endEmptyDir()
                            .build());
                }
            }
            
            // Update the pod with the sidecar
            List<Container> containers = new ArrayList<>();
            if (pod.getSpec().getContainers() != null) {
                containers.addAll(pod.getSpec().getContainers());
            }
            containers.add(sidecar);
            
            // Apply the changes to the pod
            pod.getSpec().setContainers(containers);
            pod.getSpec().setVolumes(volumes);
            
            // Update the pod
            kubernetesClient.pods()
                    .inNamespace(pod.getMetadata().getNamespace())
                    .resource(pod)
                    .serverSideApply();
            
            log.info("Successfully injected sidecar into pod: {}/{}", 
                    pod.getMetadata().getNamespace(), pod.getMetadata().getName());
        } catch (Exception e) {
            log.error("Failed to inject sidecar into pod: {}/{}", 
                    pod.getMetadata().getNamespace(), pod.getMetadata().getName(), e);
        }
    }

    /**
     * Create a sidecar container based on AppConfig
     *
     * @param appConfig the AppConfig containing sidecar configuration
     * @return the created sidecar container
     */
    private Container createSidecarContainer(AppConfig appConfig) {
        String sidecarName = getSidecarName(appConfig);
        String sidecarImage = appConfig.getSpec().getSidecarInjection().getImage();
        
        // Create environment variables
        List<EnvVar> envVars = new ArrayList<>();
        if (appConfig.getSpec().getSidecarInjection().getEnv() != null) {
            Map<String, String> envMap = appConfig.getSpec().getSidecarInjection().getEnv();
            for (Map.Entry<String, String> entry : envMap.entrySet()) {
                envVars.add(new EnvVarBuilder()
                        .withName(entry.getKey())
                        .withValue(entry.getValue())
                        .build());
            }
        }
        
        // Create volume mounts
        List<VolumeMount> volumeMounts = new ArrayList<>();
        if (appConfig.getSpec().getSidecarInjection().getVolumeMounts() != null) {
            Map<String, String> mountMap = appConfig.getSpec().getSidecarInjection().getVolumeMounts();
            for (Map.Entry<String, String> entry : mountMap.entrySet()) {
                volumeMounts.add(new VolumeMountBuilder()
                        .withName(entry.getKey())
                        .withMountPath(entry.getValue())
                        .build());
            }
        }
        
        // Build the container
        return new ContainerBuilder()
                .withName(sidecarName)
                .withImage(sidecarImage)
                .withEnv(envVars)
                .withVolumeMounts(volumeMounts)
                .build();
    }

    /**
     * Get the name of the sidecar container
     *
     * @param appConfig the AppConfig containing sidecar configuration
     * @return the sidecar container name
     */
    private String getSidecarName(AppConfig appConfig) {
        return appConfig.getSpec().getSidecarInjection().getName() != null
                ? appConfig.getSpec().getSidecarInjection().getName()
                : appConfig.getSpec().getAppName() + "-sidecar";
    }
}
