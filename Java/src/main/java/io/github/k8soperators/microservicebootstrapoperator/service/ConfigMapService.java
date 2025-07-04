package io.github.kannann1.microservicebootstrapoperator.service;

import io.github.kannann1.microservicebootstrapoperator.model.AppConfig;
import io.github.kannann1.microservicebootstrapoperator.util.RetryUtil;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service for managing ConfigMaps in Kubernetes
 * Provides functionality to sync configuration from GitHub repositories
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigMapService {

    private final KubernetesClient kubernetesClient;
    private static final String TEMP_DIR_PREFIX = "github-config-";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 10000;

    /**
     * Syncs configuration from GitHub and creates ConfigMaps
     *
     * @param appConfig the AppConfig resource
     */
    public void syncConfigFromGitHub(AppConfig appConfig) {
        if (appConfig.getSpec().getGithubRepo() == null || appConfig.getSpec().getGithubRepo().isEmpty()) {
            log.info("No GitHub repo specified for AppConfig {}, skipping config sync", 
                    appConfig.getMetadata().getName());
            return;
        }
        
        log.info("Syncing config from GitHub: repo={}, ref={}, path={}",
                appConfig.getSpec().getGithubRepo(),
                appConfig.getSpec().getGithubRef(),
                appConfig.getSpec().getConfigPath());

        File tempDir = null;
        try {
            // Create a temporary directory for cloning
            tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX).toFile();
            
            // Clone the repository
            Git git = cloneRepository(appConfig.getSpec().getGithubRepo(), tempDir);
            
            // Checkout the specified branch/tag if provided
            String ref = appConfig.getSpec().getGithubRef();
            if (ref != null && !ref.isEmpty()) {
                checkoutRef(git, ref);
            }
            
            // Get the config path or use the root if not specified
            String configPath = appConfig.getSpec().getConfigPath();
            if (configPath == null || configPath.isEmpty()) {
                configPath = "/";
            }
            
            // Process configuration files
            Path configDir = Paths.get(tempDir.getAbsolutePath(), configPath);
            createConfigMapsFromDirectory(configDir, appConfig);
            
        } catch (IOException | GitAPIException e) {
            log.error("Failed to sync config from GitHub for AppConfig {}: {}", 
                    appConfig.getMetadata().getName(), e.getMessage(), e);
        } finally {
            // Cleanup temporary directory
            if (tempDir != null && tempDir.exists()) {
                deleteDirectory(tempDir);
            }
        }
    }
    
    /**
     * Clones a Git repository
     * 
     * @param repoUrl URL of the repository
     * @param targetDir Directory to clone into
     * @return Git instance
     * @throws GitAPIException if Git operations fail
     */
    private Git cloneRepository(String repoUrl, File targetDir) throws GitAPIException {
        log.debug("Cloning repository: {} to {}", repoUrl, targetDir.getAbsolutePath());
        return Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(targetDir)
                .call();
    }
    
    /**
     * Checks out a specific branch or tag
     * 
     * @param git Git instance
     * @param ref Branch or tag name
     * @throws GitAPIException if Git operations fail
     */
    private void checkoutRef(Git git, String ref) throws GitAPIException {
        log.debug("Checking out ref: {}", ref);
        git.checkout()
           .setName(ref)
           .call();
    }
    
    /**
     * Creates ConfigMaps from files in a directory
     * 
     * @param directory Directory containing configuration files
     * @param appConfig AppConfig resource
     * @throws IOException if file operations fail
     */
    private void createConfigMapsFromDirectory(Path directory, AppConfig appConfig) throws IOException {
        if (!Files.exists(directory)) {
            log.warn("Config directory does not exist: {}", directory);
            return;
        }
        
        log.debug("Processing config directory: {}", directory);
        
        // Process files in the directory
        try (Stream<Path> paths = Files.walk(directory, 1)) {
            paths.filter(path -> !path.equals(directory))
                 .forEach(path -> {
                     try {
                         if (Files.isDirectory(path)) {
                             // Process subdirectory recursively
                             createConfigMapsFromDirectory(path, appConfig);
                         } else {
                             // Create ConfigMap for this file
                             createConfigMapFromFile(path, appConfig);
                         }
                     } catch (IOException e) {
                         log.error("Error processing path {}: {}", path, e.getMessage(), e);
                     }
                 });
        }
    }
    
    /**
     * Creates a ConfigMap from a single file
     * 
     * @param filePath Path to the configuration file
     * @param appConfig AppConfig resource
     * @throws IOException if file operations fail
     */
    private void createConfigMapFromFile(Path filePath, AppConfig appConfig) throws IOException {
        String fileName = filePath.getFileName().toString();
        String configMapName = String.format("%s-%s", appConfig.getSpec().getAppName(), fileName.replace(".", "-"));
        
        log.debug("Creating ConfigMap {} from file {}", configMapName, filePath);
        
        // Read file content
        String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        
        // Create data map
        Map<String, String> data = new HashMap<>();
        data.put(fileName, content);
        
        // Create owner reference
        OwnerReference ownerRef = new OwnerReferenceBuilder()
                .withApiVersion(appConfig.getApiVersion())
                .withKind(appConfig.getKind())
                .withName(appConfig.getMetadata().getName())
                .withUid(appConfig.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .build();
        
        // Create and apply ConfigMap
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(configMapName)
                    .withNamespace(appConfig.getMetadata().getNamespace())
                    .withOwnerReferences(ownerRef)
                    .addToLabels("app", appConfig.getSpec().getAppName())
                    .addToLabels("managed-by", "microservice-bootstrap-operator")
                .endMetadata()
                .withData(data)
                .build();
        
        // Apply the ConfigMap with retry
        try {
            RetryUtil.executeWithRetry(() -> {
                kubernetesClient.configMaps()
                        .inNamespace(appConfig.getMetadata().getNamespace())
                        .resource(configMap)
                        .createOrReplace();
                log.info("Created/updated ConfigMap {} in namespace {}", 
                        configMapName, appConfig.getMetadata().getNamespace());
            }, MAX_RETRIES, INITIAL_BACKOFF_MS, MAX_BACKOFF_MS);
        } catch (Exception e) {
            log.error("Failed to create/update ConfigMap {} in namespace {}: {}", 
                    configMapName, appConfig.getMetadata().getNamespace(), e.getMessage(), e);
        }
    }
    
    /**
     * Recursively deletes a directory
     * 
     * @param directory Directory to delete
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
