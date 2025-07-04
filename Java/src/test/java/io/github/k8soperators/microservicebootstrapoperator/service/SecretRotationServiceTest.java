package io.github.k8soperators.microservicebootstrapoperator.service;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.github.k8soperators.microservicebootstrapoperator.model.AppConfig;
import io.github.k8soperators.microservicebootstrapoperator.model.AppConfigSpec;
import io.github.k8soperators.microservicebootstrapoperator.model.SecretRotationConfig;
import io.github.k8soperators.microservicebootstrapoperator.util.RetryUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecretRotationServiceTest {

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private MixedOperation<Secret, io.fabric8.kubernetes.api.model.SecretList, Resource<Secret>> secretClient;

    @Mock
    private NonNamespaceOperation<Secret, io.fabric8.kubernetes.api.model.SecretList, Resource<Secret>> namespaceSecretClient;

    @Mock
    private Resource<Secret> secretResource;

    private SecretRotationService secretRotationService;

    @BeforeEach
    void setUp() {
        secretRotationService = new SecretRotationService(kubernetesClient);
        
        // Mock the Kubernetes client chain
        when(kubernetesClient.secrets()).thenReturn(secretClient);
        when(secretClient.inNamespace(anyString())).thenReturn(namespaceSecretClient);
        when(namespaceSecretClient.withName(anyString())).thenReturn(secretResource);
    }

    @Test
    void testRotateSecretsWithDefaultStrategy() {
        // Setup
        AppConfig appConfig = createAppConfig("default", null);
        String namespace = "test-namespace";
        List<String> secretSources = Arrays.asList("test-secret");
        
        // Mock existing secret
        Secret existingSecret = new SecretBuilder()
                .withNewMetadata()
                .withName("test-secret")
                .withNamespace(namespace)
                .endMetadata()
                .withData(new HashMap<>())
                .build();
        
        when(secretResource.get()).thenReturn(existingSecret);
        when(secretResource.createOrReplace(any(Secret.class))).thenReturn(existingSecret);
        
        // Execute
        secretRotationService.rotateSecrets(appConfig, namespace, secretSources);
        
        // Verify
        ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);
        verify(secretResource).createOrReplace(secretCaptor.capture());
        
        Secret capturedSecret = secretCaptor.getValue();
        assertNotNull(capturedSecret.getData());
        assertTrue(capturedSecret.getData().containsKey("password"));
        assertTrue(capturedSecret.getData().containsKey("rotationTimestamp"));
        assertTrue(capturedSecret.getData().containsKey("rotationId"));
        
        // Verify metadata
        assertNotNull(capturedSecret.getMetadata().getAnnotations());
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/last-rotated"));
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/rotation-id"));
        assertEquals("default", capturedSecret.getMetadata().getAnnotations().get("secretRotation.microservice.github.io/strategy"));
    }

    @Test
    void testRotateSecretsWithDatabaseStrategy() {
        // Setup
        Map<String, String> strategyConfig = new HashMap<>();
        strategyConfig.put("dbType", "postgresql");
        AppConfig appConfig = createAppConfig("database", strategyConfig);
        String namespace = "test-namespace";
        List<String> secretSources = Arrays.asList("db-secret");
        
        // Mock existing secret
        Secret existingSecret = new SecretBuilder()
                .withNewMetadata()
                .withName("db-secret")
                .withNamespace(namespace)
                .endMetadata()
                .withData(new HashMap<>())
                .build();
        
        when(secretResource.get()).thenReturn(existingSecret);
        when(secretResource.createOrReplace(any(Secret.class))).thenReturn(existingSecret);
        
        // Execute
        secretRotationService.rotateSecrets(appConfig, namespace, secretSources);
        
        // Verify
        ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);
        verify(secretResource).createOrReplace(secretCaptor.capture());
        
        Secret capturedSecret = secretCaptor.getValue();
        assertNotNull(capturedSecret.getData());
        assertTrue(capturedSecret.getData().containsKey("username"));
        assertTrue(capturedSecret.getData().containsKey("password"));
        assertTrue(capturedSecret.getData().containsKey("rotationTimestamp"));
        assertTrue(capturedSecret.getData().containsKey("rotationId"));
        
        // Verify metadata
        assertNotNull(capturedSecret.getMetadata().getAnnotations());
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/last-rotated"));
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/rotation-id"));
        assertEquals("database", capturedSecret.getMetadata().getAnnotations().get("secretRotation.microservice.github.io/strategy"));
    }

    @Test
    void testRotateSecretsWithApiKeyStrategy() {
        // Setup
        Map<String, String> strategyConfig = new HashMap<>();
        strategyConfig.put("keyLength", "32");
        AppConfig appConfig = createAppConfig("api-key", strategyConfig);
        String namespace = "test-namespace";
        List<String> secretSources = Arrays.asList("api-secret");
        
        // Mock existing secret
        Secret existingSecret = new SecretBuilder()
                .withNewMetadata()
                .withName("api-secret")
                .withNamespace(namespace)
                .endMetadata()
                .withData(new HashMap<>())
                .build();
        
        when(secretResource.get()).thenReturn(existingSecret);
        when(secretResource.createOrReplace(any(Secret.class))).thenReturn(existingSecret);
        
        // Execute
        secretRotationService.rotateSecrets(appConfig, namespace, secretSources);
        
        // Verify
        ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);
        verify(secretResource).createOrReplace(secretCaptor.capture());
        
        Secret capturedSecret = secretCaptor.getValue();
        assertNotNull(capturedSecret.getData());
        assertTrue(capturedSecret.getData().containsKey("apiKey"));
        assertTrue(capturedSecret.getData().containsKey("apiSecret"));
        assertTrue(capturedSecret.getData().containsKey("rotationTimestamp"));
        assertTrue(capturedSecret.getData().containsKey("rotationId"));
        
        // Verify metadata
        assertNotNull(capturedSecret.getMetadata().getAnnotations());
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/last-rotated"));
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/rotation-id"));
        assertEquals("api-key", capturedSecret.getMetadata().getAnnotations().get("secretRotation.microservice.github.io/strategy"));
    }

    @Test
    void testRotateSecretsWithTlsStrategy() {
        // Setup
        Map<String, String> strategyConfig = new HashMap<>();
        strategyConfig.put("commonName", "example.com");
        AppConfig appConfig = createAppConfig("tls", strategyConfig);
        String namespace = "test-namespace";
        List<String> secretSources = Arrays.asList("tls-secret");
        
        // Mock existing secret
        Secret existingSecret = new SecretBuilder()
                .withNewMetadata()
                .withName("tls-secret")
                .withNamespace(namespace)
                .endMetadata()
                .withData(new HashMap<>())
                .build();
        
        when(secretResource.get()).thenReturn(existingSecret);
        when(secretResource.createOrReplace(any(Secret.class))).thenReturn(existingSecret);
        
        // Execute
        secretRotationService.rotateSecrets(appConfig, namespace, secretSources);
        
        // Verify
        ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);
        verify(secretResource).createOrReplace(secretCaptor.capture());
        
        Secret capturedSecret = secretCaptor.getValue();
        assertNotNull(capturedSecret.getData());
        assertTrue(capturedSecret.getData().containsKey("tls.crt"));
        assertTrue(capturedSecret.getData().containsKey("tls.key"));
        assertTrue(capturedSecret.getData().containsKey("rotationTimestamp"));
        assertTrue(capturedSecret.getData().containsKey("rotationId"));
        
        // Verify metadata
        assertNotNull(capturedSecret.getMetadata().getAnnotations());
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/last-rotated"));
        assertTrue(capturedSecret.getMetadata().getAnnotations().containsKey("secretRotation.microservice.github.io/rotation-id"));
        assertEquals("tls", capturedSecret.getMetadata().getAnnotations().get("secretRotation.microservice.github.io/strategy"));
    }

    @Test
    void testRotateSecretsWithNonExistentSecret() {
        // Setup
        AppConfig appConfig = createAppConfig("default", null);
        String namespace = "test-namespace";
        List<String> secretSources = Arrays.asList("non-existent-secret");
        
        // Mock non-existent secret
        when(secretResource.get()).thenReturn(null);
        
        // Mock secret creation
        Secret newSecret = new SecretBuilder()
                .withNewMetadata()
                .withName("non-existent-secret")
                .withNamespace(namespace)
                .endMetadata()
                .withData(new HashMap<>())
                .build();
        when(secretResource.createOrReplace(any(Secret.class))).thenReturn(newSecret);
        
        // Execute
        secretRotationService.rotateSecrets(appConfig, namespace, secretSources);
        
        // Verify
        ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);
        verify(secretResource).createOrReplace(secretCaptor.capture());
        
        Secret capturedSecret = secretCaptor.getValue();
        assertNotNull(capturedSecret.getData());
        assertTrue(capturedSecret.getData().containsKey("password"));
        assertTrue(capturedSecret.getData().containsKey("rotationTimestamp"));
        assertTrue(capturedSecret.getData().containsKey("rotationId"));
    }

    @Test
    void testRotateSecretsWithRetryOnFailure() {
        // Setup
        AppConfig appConfig = createAppConfig("default", null);
        String namespace = "test-namespace";
        List<String> secretSources = Arrays.asList("test-secret");
        
        // Mock existing secret
        Secret existingSecret = new SecretBuilder()
                .withNewMetadata()
                .withName("test-secret")
                .withNamespace(namespace)
                .endMetadata()
                .withData(new HashMap<>())
                .build();
        
        when(secretResource.get()).thenReturn(existingSecret);
        
        // Mock failure then success
        when(secretResource.createOrReplace(any(Secret.class)))
                .thenThrow(new RuntimeException("API server error"))
                .thenReturn(existingSecret);
        
        // Execute
        secretRotationService.rotateSecrets(appConfig, namespace, secretSources);
        
        // Verify
        verify(secretResource, times(2)).createOrReplace(any(Secret.class));
    }

    // Helper method to create AppConfig with specific rotation strategy
    private AppConfig createAppConfig(String strategy, Map<String, String> strategyConfig) {
        AppConfig appConfig = new AppConfig();
        AppConfigSpec spec = new AppConfigSpec();
        SecretRotationConfig secretRotation = new SecretRotationConfig();
        
        secretRotation.setEnabled(true);
        secretRotation.setIntervalHours(24);
        secretRotation.setSources(Arrays.asList("test-source"));
        secretRotation.setStrategy(strategy);
        secretRotation.setStrategyConfig(strategyConfig);
        
        spec.setAppName("test-app");
        spec.setSecretRotation(secretRotation);
        appConfig.setSpec(spec);
        
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName("test-app-config");
        metadata.setNamespace("test-namespace");
        appConfig.setMetadata(metadata);
        
        return appConfig;
    }
}
