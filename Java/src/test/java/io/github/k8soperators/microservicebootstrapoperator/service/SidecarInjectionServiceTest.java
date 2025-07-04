package io.github.kannann1.microservicebootstrapoperator.service;

import io.github.kannann1.microservicebootstrapoperator.model.AppConfig;
import io.github.kannann1.microservicebootstrapoperator.model.AppConfigSpec;
import io.github.kannann1.microservicebootstrapoperator.model.SidecarInjectionConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
// Removed unused import: io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
// Removed unused import: static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SidecarInjectionServiceTest {

    @Mock
    private KubernetesClient kubernetesClient;
    
    @Mock
    private MixedOperation<Pod, io.fabric8.kubernetes.api.model.PodList, PodResource> podOperation;
    
    @Mock
    private NonNamespaceOperation<Pod, io.fabric8.kubernetes.api.model.PodList, PodResource> nonNamespaceOperation;
    
    @Mock
    private SharedIndexInformer<Pod> podInformer;
    
    @Mock
    private PodResource podResource;
    
    private SidecarInjectionService sidecarInjectionService;
    
    @BeforeEach
    public void setup() {
        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inAnyNamespace()).thenReturn(nonNamespaceOperation);
        when(nonNamespaceOperation.inform()).thenReturn(podInformer);
        
        sidecarInjectionService = new SidecarInjectionService(kubernetesClient);
    }
    
    @Test
    public void testRegisterAndUnregisterAppConfig() {
        // Create test AppConfig
        AppConfig appConfig = createTestAppConfig("test-app", "test-namespace");
        
        // Register AppConfig
        sidecarInjectionService.registerAppConfig(appConfig);
        
        // Verify it's registered by checking if handlePodCreation works with it
        Pod pod = createTestPod("test-pod", "test-namespace", Map.of("app", "test-app"));
        
        when(kubernetesClient.pods().inNamespace("test-namespace").withName("test-pod")).thenReturn(podResource);
        when(podResource.get()).thenReturn(pod);
        
        // Simulate pod creation event
        sidecarInjectionService.handlePodCreation(pod);
        
        // Unregister AppConfig
        sidecarInjectionService.unregisterAppConfig(appConfig);
        
        // Create a new pod and verify it's not injected
        Pod newPod = createTestPod("test-pod-2", "test-namespace", Map.of("app", "test-app"));
        
        // Simulate pod creation event after unregistering
        sidecarInjectionService.handlePodCreation(newPod);
        
        // Verify no more interactions with the pod resource
        verifyNoMoreInteractions(podResource);
    }
    
    @Test
    public void testShouldInjectSidecar() {
        // Create test AppConfig
        AppConfig appConfig = createTestAppConfig("test-app", "test-namespace");
        
        // Register AppConfig
        sidecarInjectionService.registerAppConfig(appConfig);
        
        // Create test pod with matching labels
        Pod matchingPod = createTestPod("matching-pod", "test-namespace", Map.of("app", "test-app"));
        
        // Create test pod with non-matching labels
        Pod nonMatchingPod = createTestPod("non-matching-pod", "test-namespace", Map.of("app", "other-app"));
        
        // Create test pod with matching labels but in different namespace
        Pod differentNamespacePod = createTestPod("different-namespace-pod", "other-namespace", Map.of("app", "test-app"));
        
        // Create test pod with matching labels but already has sidecar
        Pod podWithSidecar = createTestPod("pod-with-sidecar", "test-namespace", Map.of("app", "test-app"));
        Container sidecarContainer = new Container();
        sidecarContainer.setName("test-app-sidecar");
        podWithSidecar.getSpec().getContainers().add(sidecarContainer);
        
        // Test shouldInjectSidecar method
        assertTrue(sidecarInjectionService.shouldInjectSidecar(matchingPod, appConfig));
        assertFalse(sidecarInjectionService.shouldInjectSidecar(nonMatchingPod, appConfig));
        assertFalse(sidecarInjectionService.shouldInjectSidecar(differentNamespacePod, appConfig));
        assertFalse(sidecarInjectionService.shouldInjectSidecar(podWithSidecar, appConfig));
    }
    
    @Test
    public void testInjectSidecar() {
        // Create test AppConfig
        AppConfig appConfig = createTestAppConfig("test-app", "test-namespace");
        
        // Create test pod
        Pod pod = createTestPod("test-pod", "test-namespace", Map.of("app", "test-app"));
        
        // Mock pod resource
        when(kubernetesClient.pods().inNamespace("test-namespace").withName("test-pod")).thenReturn(podResource);
        when(podResource.get()).thenReturn(pod);
        
        // Capture the pod that is passed to serverSideApply
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(kubernetesClient.pods().inNamespace(eq("test-namespace")).resource(podCaptor.capture())).thenReturn(podResource);
        
        // Inject sidecar
        sidecarInjectionService.injectSidecar(pod, appConfig);
        
        // Verify serverSideApply was called
        verify(podResource).serverSideApply();
        
        // Verify the pod has the sidecar container
        Pod updatedPod = podCaptor.getValue();
        List<Container> containers = updatedPod.getSpec().getContainers();
        
        assertEquals(2, containers.size());
        assertEquals("test-app-sidecar", containers.get(1).getName());
        assertEquals("nginx:latest", containers.get(1).getImage());
        
        // Verify environment variables
        assertEquals(2, containers.get(1).getEnv().size());
        assertEquals("APP_NAME", containers.get(1).getEnv().get(0).getName());
        assertEquals("test-app", containers.get(1).getEnv().get(0).getValue());
        assertEquals("DEBUG", containers.get(1).getEnv().get(1).getName());
        assertEquals("true", containers.get(1).getEnv().get(1).getValue());
        
        // Verify volume mounts
        assertEquals(1, containers.get(1).getVolumeMounts().size());
        assertEquals("config-volume", containers.get(1).getVolumeMounts().get(0).getName());
        assertEquals("/etc/config", containers.get(1).getVolumeMounts().get(0).getMountPath());
    }
    
    private AppConfig createTestAppConfig(String name, String namespace) {
        AppConfig appConfig = new AppConfig();
        
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        metadata.setNamespace(namespace);
        appConfig.setMetadata(metadata);
        
        AppConfigSpec spec = new AppConfigSpec();
        spec.setAppName(name);
        
        SidecarInjectionConfig sidecarInjection = new SidecarInjectionConfig();
        sidecarInjection.setEnabled(true);
        sidecarInjection.setImage("nginx:latest");
        
        Map<String, String> selectorLabels = new HashMap<>();
        selectorLabels.put("app", name);
        sidecarInjection.setSelectorLabels(selectorLabels);
        
        Map<String, String> env = new HashMap<>();
        env.put("APP_NAME", name);
        env.put("DEBUG", "true");
        sidecarInjection.setEnv(env);
        
        Map<String, String> volumeMounts = new HashMap<>();
        volumeMounts.put("config-volume", "/etc/config");
        sidecarInjection.setVolumeMounts(volumeMounts);
        
        spec.setSidecarInjection(sidecarInjection);
        appConfig.setSpec(spec);
        
        return appConfig;
    }
    
    private Pod createTestPod(String name, String namespace, Map<String, String> labels) {
        Pod pod = new Pod();
        
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        metadata.setNamespace(namespace);
        metadata.setLabels(labels);
        pod.setMetadata(metadata);
        
        PodSpec podSpec = new PodSpec();
        podSpec.setContainers(new ArrayList<>());
        
        Container appContainer = new Container();
        appContainer.setName("app");
        appContainer.setImage("app:latest");
        podSpec.getContainers().add(appContainer);
        
        pod.setSpec(podSpec);
        
        return pod;
    }
}
