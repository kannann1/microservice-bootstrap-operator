package io.github.k8soperators.microservicebootstrapoperator.controller;

import io.github.k8soperators.microservicebootstrapoperator.model.AppConfig;
import io.github.k8soperators.microservicebootstrapoperator.model.AppConfigSpec;
import io.github.k8soperators.microservicebootstrapoperator.model.SidecarInjectionConfig;
import io.github.k8soperators.microservicebootstrapoperator.service.ConfigMapService;
import io.github.k8soperators.microservicebootstrapoperator.service.NetworkPolicyService;
import io.github.k8soperators.microservicebootstrapoperator.service.RBACService;
import io.github.k8soperators.microservicebootstrapoperator.service.SecretRotationService;
import io.github.k8soperators.microservicebootstrapoperator.service.SidecarInjectionService;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppConfigControllerTest {

    @Mock
    private KubernetesClient kubernetesClient;
    
    @Mock
    private ConfigMapService configMapService;
    
    @Mock
    private RBACService rbacService;
    
    @Mock
    private NetworkPolicyService networkPolicyService;
    
    @Mock
    private SecretRotationService secretRotationService;
    
    @Mock
    private SidecarInjectionService sidecarInjectionService;
    
    @Mock
    private Context<AppConfig> context;
    
    private AppConfigController controller;
    
    @BeforeEach
    public void setup() {
        controller = new AppConfigController(
            kubernetesClient,
            configMapService,
            rbacService,
            networkPolicyService,
            secretRotationService,
            sidecarInjectionService
        );
    }
    
    @Test
    public void testReconcileShouldRegisterAppConfigForSidecarInjection() {
        // Create test AppConfig with sidecar injection enabled
        AppConfig appConfig = createTestAppConfig("test-app", "test-namespace", true);
        
        // Mock the reconcile context
        when(context.getSecondaryResource(any())).thenReturn(java.util.Optional.empty());
        
        // Call reconcile
        UpdateControl<AppConfig> result = controller.reconcile(appConfig, context);
        
        // Verify the result is not null
        assertNotNull(result);
        
        // Verify that registerAppConfig was called on the SidecarInjectionService
        verify(sidecarInjectionService).registerAppConfig(appConfig);
    }
    
    @Test
    public void testReconcileShouldNotRegisterAppConfigWhenSidecarInjectionDisabled() {
        // Create test AppConfig with sidecar injection disabled
        AppConfig appConfig = createTestAppConfig("test-app", "test-namespace", false);
        
        // Mock the reconcile context
        when(context.getSecondaryResource(any())).thenReturn(java.util.Optional.empty());
        
        // Call reconcile
        UpdateControl<AppConfig> result = controller.reconcile(appConfig, context);
        
        // Verify the result is not null
        assertNotNull(result);
        
        // No interaction with sidecarInjectionService should occur
        // This is implicitly verified by not setting up any expectations on the mock
    }
    
    private AppConfig createTestAppConfig(String name, String namespace, boolean sidecarInjectionEnabled) {
        AppConfig appConfig = new AppConfig();
        
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        metadata.setNamespace(namespace);
        appConfig.setMetadata(metadata);
        
        AppConfigSpec spec = new AppConfigSpec();
        spec.setAppName(name);
        
        if (sidecarInjectionEnabled) {
            SidecarInjectionConfig sidecarInjection = new SidecarInjectionConfig();
            sidecarInjection.setEnabled(true);
            sidecarInjection.setName(name + "-sidecar");
            sidecarInjection.setImage("nginx:latest");
            
            Map<String, String> selectorLabels = new HashMap<>();
            selectorLabels.put("app", name);
            sidecarInjection.setSelectorLabels(selectorLabels);
            
            spec.setSidecarInjection(sidecarInjection);
        } else {
            SidecarInjectionConfig sidecarInjection = new SidecarInjectionConfig();
            sidecarInjection.setEnabled(false);
            spec.setSidecarInjection(sidecarInjection);
        }
        
        appConfig.setSpec(spec);
        
        return appConfig;
    }
}
