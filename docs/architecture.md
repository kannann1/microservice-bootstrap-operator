# Microservice Bootstrap Operator Architecture

This document provides an overview of the Microservice Bootstrap Operator architecture, including its components, interactions, and workflows.

## Architecture Overview

The Microservice Bootstrap Operator follows the Kubernetes Operator pattern, using the Java Operator SDK to watch for AppConfig custom resources and reconcile the desired state with the actual state in the cluster.

```mermaid
graph TD
    subgraph "Kubernetes Cluster"
        API[Kubernetes API Server]
        
        subgraph "Microservice Bootstrap Operator"
            App[MicroserviceBootstrapOperatorApplication]
            Operator[Java Operator SDK]
            Controller[AppConfigController]
            
            subgraph "Services"
                ConfigMap[ConfigMapService]
                RBAC[RBACService]
                Network[NetworkPolicyService]
                Secret[SecretRotationService]
                Sidecar[SidecarInjectionService]
            end
            
            subgraph "Models"
                AppConfigCR[AppConfig CR]
                AppConfigSpec[AppConfigSpec]
                AppConfigStatus[AppConfigStatus]
            end
            
            subgraph "Utilities"
                Retry[RetryUtil]
                Version[VersionConverter]
            end
        end
        
        subgraph "Kubernetes Resources"
            ConfigMaps[ConfigMaps]
            Secrets[Secrets]
            Roles[Roles/RoleBindings]
            NetworkPolicies[NetworkPolicies]
            Pods[Pods with Sidecars]
        end
        
        subgraph "External Systems"
            GitHub[GitHub Repositories]
        end
    end
    
    %% Connections
    API <--> Operator
    App --> Operator
    Operator --> Controller
    
    Controller --> ConfigMap
    Controller --> RBAC
    Controller --> Network
    Controller --> Secret
    Controller --> Sidecar
    
    Controller --> Retry
    Controller --> Version
    
    ConfigMap --> GitHub
    ConfigMap --> ConfigMaps
    RBAC --> Roles
    Network --> NetworkPolicies
    Secret --> Secrets
    Sidecar --> Pods
    
    Controller --> AppConfigCR
    AppConfigCR --> AppConfigSpec
    AppConfigCR --> AppConfigStatus
    
    %% External connections
    ConfigMap -.-> API
    RBAC -.-> API
    Network -.-> API
    Secret -.-> API
    Sidecar -.-> API
```

## Component Descriptions

### Core Components

- **MicroserviceBootstrapOperatorApplication**: The Spring Boot application entry point that initializes and starts the operator.
- **Java Operator SDK**: Provides the framework for watching Kubernetes resources and triggering reconciliation.
- **AppConfigController**: The main controller that implements the reconciliation logic for AppConfig resources.

### Services

- **ConfigMapService**: Syncs configuration from GitHub repositories and creates ConfigMaps in the cluster.
- **RBACService**: Creates and manages RBAC resources (Roles, RoleBindings) based on AppConfig specifications.
- **NetworkPolicyService**: Creates and manages NetworkPolicy resources to control pod communication.
- **SecretRotationService**: Handles automatic rotation of secrets based on configured strategies and schedules.
- **SidecarInjectionService**: Manages sidecar injection into pods based on label selectors.

### Models

- **AppConfig**: The custom resource definition that users create to define their microservice configuration.
- **AppConfigSpec**: Contains the specification for the AppConfig resource.
- **AppConfigStatus**: Tracks the status and conditions of the AppConfig resource.

### Utilities

- **RetryUtil**: Provides retry mechanisms for operations that might fail temporarily.
- **VersionConverter**: Handles version conversion for AppConfig resources.

## Workflow

1. The operator watches for AppConfig custom resources in the cluster.
2. When an AppConfig is created, updated, or deleted, the AppConfigController's reconcile method is called.
3. The controller checks if the resource is being deleted and handles cleanup if necessary.
4. For new or updated resources, the controller:
   - Adds a finalizer to prevent premature deletion
   - Checks and converts the resource version if needed
   - Syncs configuration from GitHub repositories
   - Sets up RBAC resources
   - Creates NetworkPolicy resources
   - Registers the AppConfig for sidecar injection if enabled
   - Handles secret rotation based on the configured strategy
5. The controller updates the AppConfig status with the reconciliation results.

## Reconciliation Loop

```mermaid
sequenceDiagram
    participant K8s as Kubernetes API
    participant Op as Operator
    participant Ctrl as AppConfigController
    participant Svc as Services
    participant Res as K8s Resources
    
    K8s->>Op: AppConfig Event (Create/Update/Delete)
    Op->>Ctrl: Trigger Reconciliation
    
    alt Resource is being deleted
        Ctrl->>Svc: Clean up resources
        Svc->>K8s: Remove finalizer
    else Resource is being created/updated
        Ctrl->>K8s: Add finalizer if missing
        Ctrl->>Svc: Sync config from GitHub
        Svc->>Res: Create/Update ConfigMaps
        
        Ctrl->>Svc: Setup RBAC
        Svc->>Res: Create/Update Roles/RoleBindings
        
        Ctrl->>Svc: Setup NetworkPolicy
        Svc->>Res: Create/Update NetworkPolicies
        
        alt Sidecar Injection Enabled
            Ctrl->>Svc: Register for sidecar injection
            Svc->>Res: Update Pod templates
        end
        
        alt Secret Rotation Enabled
            Ctrl->>Svc: Rotate secrets if needed
            Svc->>Res: Update Secrets
        end
        
        Ctrl->>K8s: Update AppConfig status
    end
```

## Deployment Architecture

```mermaid
flowchart TD
    subgraph "Kubernetes Cluster"
        subgraph "Operator Namespace"
            Deployment[Operator Deployment]
            ServiceAccount[Service Account]
            ClusterRole[Cluster Role]
            ClusterRoleBinding[Cluster Role Binding]
        end
        
        subgraph "Application Namespaces"
            AppConfig[AppConfig CR]
            ConfigMaps[ConfigMaps]
            Secrets[Secrets]
            Roles[Roles]
            RoleBindings[RoleBindings]
            NetworkPolicies[NetworkPolicies]
            Pods[Application Pods]
        end
    end
    
    Deployment --> ServiceAccount
    ServiceAccount --> ClusterRoleBinding
    ClusterRoleBinding --> ClusterRole
    
    Deployment -- watches --> AppConfig
    Deployment -- creates/updates --> ConfigMaps
    Deployment -- creates/updates --> Secrets
    Deployment -- creates/updates --> Roles
    Deployment -- creates/updates --> RoleBindings
    Deployment -- creates/updates --> NetworkPolicies
    NetworkPolicies -- applied to --> Pods
    ConfigMaps -- mounted by --> Pods
    Secrets -- mounted by --> Pods
```

This architecture provides a comprehensive view of how the Microservice Bootstrap Operator works, from the high-level components to the detailed reconciliation workflow.
