package io.github.k8soperators.microservicebootstrapoperator.service;

import io.github.k8soperators.microservicebootstrapoperator.model.AppConfig;
import io.github.k8soperators.microservicebootstrapoperator.model.AppConfigSpec;
import io.github.k8soperators.microservicebootstrapoperator.model.SidecarInjectionConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SidecarInjectionService
 * Updated for Java 22 compatibility
 */
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
        // Setup basic mocks that are needed for all tests
        lenient().when(kubernetesClient.pods()).thenReturn(podOperation);
        lenient().when(podOperation.inAnyNamespace()).thenReturn(nonNamespaceOperation);
        lenient().when(nonNamespaceOperation.inform()).thenReturn(podInformer);
        
        // Fix for NullPointerException
        lenient().when(podOperation.inNamespace(anyString())).thenReturn(nonNamespaceOperation);
        lenient().when(nonNamespaceOperation.withName(anyString())).thenReturn(podResource);
        lenient().when(nonNamespaceOperation.resource(any(Pod.class))).thenReturn(podResource);
        
        // Make all stubs lenient to avoid unnecessary stubbing exceptions
        lenient().when(podResource.get()).thenReturn(null);
        lenient().when(podResource.serverSideApply()).thenReturn(null);
        
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
        
        // Setup specific mocks for this test case - use lenient() to avoid unnecessary stubbing errors
        lenient().when(kubernetesClient.pods().inNamespace("test-namespace").withName("test-pod")).thenReturn(podResource);
        lenient().when(podResource.get()).thenReturn(pod);
        
        // Simulate pod creation event
        sidecarInjectionService.handlePodCreation(pod);
        
        // Verify expected interactions occurred
        verify(podResource).serverSideApply();
        
        // Unregister AppConfig
        sidecarInjectionService.unregisterAppConfig(appConfig);
        
        // Create a new pod and verify it's not injected
        Pod newPod = createTestPod("test-pod-2", "test-namespace", Map.of("app", "test-app"));
        
        // Reset the mock to clear previous interactions
        reset(podResource);
        
        // Simulate pod creation event after unregistering
        sidecarInjectionService.handlePodCreation(newPod);
        
        // Verify no interactions with the pod resource after reset
        verifyNoInteractions(podResource);
    }
    
    @Test
    public void testShouldInjectSidecar() {
        // Create test AppConfig
        AppConfig appConfig = createTestAppConfig("test-app", "test-namespace");
        
        // Create test pods with different scenarios
        Pod matchingPod = createTestPod("matching-pod", "test-namespace", Map.of("app", "test-app"));
        Pod nonMatchingPod = createTestPod("non-matching-pod", "test-namespace", Map.of("app", "other-app"));
        Pod differentNamespacePod = createTestPod("different-namespace-pod", "other-namespace", Map.of("app", "test-app"));
        
        // Create test pod with matching labels but already has sidecar
        Pod podWithSidecar = createTestPod("pod-with-sidecar", "test-namespace", Map.of("app", "test-app"));
        Container sidecarContainer = new Container();
        sidecarContainer.setName("test-app-sidecar");
        podWithSidecar.getSpec().getContainers().add(sidecarContainer);
        
        // Test shouldInjectSidecar method directly without registering the AppConfig
        assertTrue(sidecarInjectionService.shouldInjectSidecar(matchingPod, appConfig), 
                "Should inject sidecar for pod with matching labels in the same namespace");
        assertFalse(sidecarInjectionService.shouldInjectSidecar(nonMatchingPod, appConfig),
                "Should not inject sidecar for pod with non-matching labels");
        assertFalse(sidecarInjectionService.shouldInjectSidecar(differentNamespacePod, appConfig),
                "Should not inject sidecar for pod in different namespace");
        assertFalse(sidecarInjectionService.shouldInjectSidecar(podWithSidecar, appConfig),
                "Should not inject sidecar for pod that already has the sidecar");
    }
    
    @Test
    public void testInjectSidecar() {
        // Create test AppConfig
        AppConfig appConfig = createTestAppConfig("test-app", "test-namespace");
        
        // Create test pod
        Pod pod = createTestPod("test-pod", "test-namespace", Map.of("app", "test-app"));
        
        // Capture the pod that is passed to resource() method
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(nonNamespaceOperation.resource(podCaptor.capture())).thenReturn(podResource);
        
        // Inject sidecar
        sidecarInjectionService.injectSidecar(pod, appConfig);
        
        // Verify serverSideApply was called
        verify(podResource).serverSideApply();
        
        // Verify the pod has the sidecar container
        Pod updatedPod = podCaptor.getValue();
        List<Container> containers = updatedPod.getSpec().getContainers();
        
        assertEquals(2, containers.size(), "Pod should have 2 containers after injection");
        assertEquals("test-app-sidecar", containers.get(1).getName(), "Second container should be the sidecar");
        assertEquals("nginx:latest", containers.get(1).getImage(), "Sidecar should have the correct image");
        
        // Verify environment variables
        assertEquals(2, containers.get(1).getEnv().size(), "Sidecar should have 2 environment variables");
        assertEquals("APP_NAME", containers.get(1).getEnv().get(0).getName(), "First env var should be APP_NAME");
        assertEquals("test-app", containers.get(1).getEnv().get(0).getValue(), "APP_NAME should have correct value");
        assertEquals("DEBUG", containers.get(1).getEnv().get(1).getName(), "Second env var should be DEBUG");
        assertEquals("true", containers.get(1).getEnv().get(1).getValue(), "DEBUG should have correct value");
        
        // Verify volume mounts
        assertEquals(1, containers.get(1).getVolumeMounts().size(), "Sidecar should have 1 volume mount");
        assertEquals("config-volume", containers.get(1).getVolumeMounts().get(0).getName(), "Volume mount should have correct name");
        assertEquals("/etc/config", containers.get(1).getVolumeMounts().get(0).getMountPath(), "Volume mount should have correct path");
    }
    
    /**
     * Helper method to create a test AppConfig
     */
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
        
        // Use Map.of for immutable maps in Java 22
        sidecarInjection.setSelectorLabels(new HashMap<>(Map.of("app", name)));
        sidecarInjection.setEnv(new HashMap<>(Map.of("APP_NAME", name, "DEBUG", "true")));
        sidecarInjection.setVolumeMounts(new HashMap<>(Map.of("config-volume", "/etc/config")));
        
        spec.setSidecarInjection(sidecarInjection);
        appConfig.setSpec(spec);
        
        return appConfig;
    }
    
    /**
     * Helper method to create a test Pod
     */
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
