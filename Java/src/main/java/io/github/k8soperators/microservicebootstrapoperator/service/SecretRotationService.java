package io.github.kannann1.microservicebootstrapoperator.service;

import io.github.kannann1.microservicebootstrapoperator.model.AppConfig;
import io.github.kannann1.microservicebootstrapoperator.util.RetryUtil;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing and rotating Kubernetes Secrets
 * Provides functionality to automatically rotate secrets based on configured intervals
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecretRotationService {

    private final KubernetesClient kubernetesClient;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_PASSWORD_LENGTH = 16;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 10000;
    
    /**
     * Rotates secrets for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     */
    public void rotateSecrets(AppConfig appConfig) {
        log.info("Rotating secrets for: {}/{}", appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
        
        if (appConfig.getSpec().getSecretRotation() == null || !appConfig.getSpec().getSecretRotation().isEnabled()) {
            log.info("Secret rotation not enabled, skipping");
            return;
        }

        String secretName = String.format("%s-secrets", appConfig.getSpec().getAppName());
        
        try {
            // Determine rotation strategy based on configuration
            String rotationStrategy = "default";
            if (appConfig.getSpec().getSecretRotation().getStrategy() != null) {
                rotationStrategy = appConfig.getSpec().getSecretRotation().getStrategy();
            }
            
            // Generate secret data based on strategy
            Map<String, String> secretData = generateSecretData(appConfig, rotationStrategy);
            
            // Add metadata about rotation
            secretData.put("rotated-at", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));
            secretData.put("rotation-id", UUID.randomUUID().toString());
            
            // Create or update the secret with retry
            RetryUtil.executeWithRetry(() -> {
                createOrUpdateSecret(appConfig, secretName, secretData);
            }, MAX_RETRIES, INITIAL_BACKOFF_MS, MAX_BACKOFF_MS);
            
            // Track created resource
            String resourceName = String.format("Secret/%s/%s", appConfig.getMetadata().getNamespace(), secretName);
            if (appConfig.getStatus() != null) {
                appConfig.getStatus().addCreatedResource(resourceName);
            }
            
            log.info("Successfully rotated secrets for: {}/{}", 
                    appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
        } catch (Exception e) {
            log.error("Failed to rotate secrets for: {}/{}: {}", 
                    appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName(), e.getMessage(), e);
            throw new RuntimeException("Secret rotation failed", e);
        }
    }
    
    /**
     * Generates secret data based on the specified rotation strategy
     *
     * @param appConfig the AppConfig resource
     * @param strategy the rotation strategy to use
     * @return Map of secret data
     */
    private Map<String, String> generateSecretData(AppConfig appConfig, String strategy) {
        Map<String, String> secretData = new HashMap<>();
        
        switch (strategy.toLowerCase()) {
            case "database":
                // Generate database credentials
                secretData.put("db-username", generateUsername(appConfig.getSpec().getAppName()));
                secretData.put("db-password", generateSecurePassword(DEFAULT_PASSWORD_LENGTH));
                secretData.put("db-url", generateDatabaseUrl(appConfig));
                break;
                
            case "api-key":
                // Generate API keys
                secretData.put("api-key", generateApiKey());
                secretData.put("api-secret", generateApiSecret());
                break;
                
            case "tls":
                // In a real implementation, this would generate or obtain TLS certificates
                // For demonstration, we'll just add placeholder values
                secretData.put("tls.crt", "placeholder-certificate-data");
                secretData.put("tls.key", "placeholder-key-data");
                break;
                
            case "default":
            default:
                // Generate basic credentials
                secretData.put("username", generateUsername(appConfig.getSpec().getAppName()));
                secretData.put("password", generateSecurePassword(DEFAULT_PASSWORD_LENGTH));
                break;
        }
        
        return secretData;
    }
    
    /**
     * Creates or updates a Kubernetes Secret
     *
     * @param appConfig the AppConfig resource
     * @param secretName the name of the secret
     * @param stringData the secret data (unencoded)
     */
    private void createOrUpdateSecret(AppConfig appConfig, String secretName, Map<String, String> stringData) {
        Resource<Secret> secretResource = kubernetesClient.secrets()
                .inNamespace(appConfig.getMetadata().getNamespace())
                .withName(secretName);
        
        Secret existingSecret = secretResource.get();
        
        if (existingSecret == null) {
            // Create new secret
            Secret secret = new SecretBuilder()
                    .withNewMetadata()
                        .withName(secretName)
                        .withNamespace(appConfig.getMetadata().getNamespace())
                        .withOwnerReferences(createOwnerReference(appConfig))
                        .addToLabels("app", appConfig.getSpec().getAppName())
                        .addToLabels("managed-by", "microservice-bootstrap-operator")
                        .addToAnnotations("rotation-timestamp", ZonedDateTime.now().toString())
                    .endMetadata()
                    .withStringData(stringData)
                    .build();
            
            kubernetesClient.secrets()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(secret)
                    .create();
            
            log.info("Created Secret: {}/{}", appConfig.getMetadata().getNamespace(), secretName);
        } else {
            // Update existing secret
            Map<String, String> existingData = existingSecret.getData();
            if (existingData == null) {
                existingData = new HashMap<>();
            }
            
            // Convert string data to base64-encoded data
            Map<String, String> encodedData = new HashMap<>(existingData);
            for (Map.Entry<String, String> entry : stringData.entrySet()) {
                encodedData.put(entry.getKey(), Base64.getEncoder().encodeToString(
                        entry.getValue().getBytes(StandardCharsets.UTF_8)));
            }
            
            // Update the secret
            Secret updatedSecret = new SecretBuilder(existingSecret)
                    .editMetadata()
                        .addToAnnotations("rotation-timestamp", ZonedDateTime.now().toString())
                    .endMetadata()
                    .withData(encodedData)
                    .build();
            
            kubernetesClient.secrets()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(updatedSecret)
                    .replace();
            
            log.info("Updated Secret: {}/{}", appConfig.getMetadata().getNamespace(), secretName);
        }
    }
    
    /**
     * Generates a secure random password
     *
     * @param length the length of the password
     * @return a secure random password
     */
    private String generateSecurePassword(int length) {
        // Character set for password generation
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,.<>?";
        StringBuilder password = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(chars.length());
            password.append(chars.charAt(index));
        }
        
        return password.toString();
    }
    
    /**
     * Generates a username based on the application name
     *
     * @param appName the application name
     * @return a username
     */
    private String generateUsername(String appName) {
        return appName.toLowerCase().replaceAll("[^a-z0-9]", "") + 
               "_" + 
               Integer.toHexString(SECURE_RANDOM.nextInt(0x1000));
    }
    
    /**
     * Generates a database URL
     *
     * @param appConfig the AppConfig resource
     * @return a database URL
     */
    private String generateDatabaseUrl(AppConfig appConfig) {
        String dbName = appConfig.getSpec().getAppName().toLowerCase().replaceAll("[^a-z0-9]", "");
        return String.format("jdbc:postgresql://db.%s.svc.cluster.local:5432/%s", 
                appConfig.getMetadata().getNamespace(), dbName);
    }
    
    /**
     * Generates an API key
     *
     * @return an API key
     */
    private String generateApiKey() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Generates an API secret
     *
     * @return an API secret
     */
    private String generateApiSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Creates an owner reference for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     * @return the owner reference
     */
    private OwnerReference createOwnerReference(AppConfig appConfig) {
        return new OwnerReferenceBuilder()
                .withApiVersion(appConfig.getApiVersion())
                .withKind(appConfig.getKind())
                .withName(appConfig.getMetadata().getName())
                .withUid(appConfig.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .build();
    }
}
