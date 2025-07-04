# Microservice Bootstrap Operator Architecture

This document provides an overview of the Microservice Bootstrap Operator's architecture, components, and how they interact.

## High-Level Architecture

The Microservice Bootstrap Operator follows the Kubernetes Operator pattern, using the Java Operator SDK to implement custom controllers for managing AppConfig custom resources. The operator is built as a Spring Boot application, which provides dependency injection, configuration management, and health monitoring.

![Architecture Diagram](../images/architecture.png)

## Key Components

### 1. Custom Resource Definition (CRD)

The `AppConfig` CRD defines the schema for configuring microservices. It includes specifications for:
- Sidecar injection
- RBAC setup
- Network policies
- Config synchronization
- Secret rotation

### 2. Controller

The `AppConfigController` is the main reconciliation loop that processes AppConfig resources. It:
- Watches for AppConfig create/update/delete events
- Delegates to specialized services for specific functionality
- Updates the AppConfig status with reconciliation results

### 3. Services

#### SidecarInjectionService
- Watches for pod creation events across namespaces
- Maintains a cache of AppConfig resources that require sidecar injection
- Injects sidecar containers into pods that match label selectors
- Uses server-side apply for pod updates

#### ConfigMapService
- Synchronizes configuration from GitHub repositories
- Creates and updates ConfigMaps with proper owner references

#### RBACService
- Creates ServiceAccounts, Roles, and RoleBindings
- Sets up proper permissions for microservices

#### NetworkPolicyService
- Creates and updates NetworkPolicy resources
- Configures ingress and egress rules based on AppConfig

#### SecretRotationService
- Rotates secrets according to schedule
- Updates secret data while maintaining references

### 4. Utilities

#### RetryUtil
- Provides retry logic with exponential backoff
- Handles transient errors in Kubernetes API calls

#### VersionConverter
- Implements the "Hub and Spoke" conversion pattern for AppConfig versions
- Ensures backward compatibility as the CRD evolves

## Data Flow

1. The Kubernetes API server notifies the operator of AppConfig changes
2. The AppConfigController reconciles the AppConfig resource
3. The controller delegates to specialized services based on the AppConfig spec
4. Each service creates, updates, or deletes Kubernetes resources as needed
5. The controller updates the AppConfig status with the results

## Sidecar Injection Flow

The sidecar injection feature follows a specific flow:

1. When an AppConfig with sidecar injection is reconciled, it's registered with the SidecarInjectionService
2. The SidecarInjectionService watches for pod creation events across namespaces
3. When a new pod is created, the service checks if it matches any registered AppConfig's label selectors
4. If a match is found, the service injects the sidecar container into the pod
5. The pod is updated using server-side apply to avoid conflicts

## Reconciliation Loop

The reconciliation loop follows these steps:

1. Fetch the latest AppConfig resource
2. Process each feature based on the AppConfig spec:
   - Set up RBAC resources
   - Configure network policies
   - Synchronize configuration
   - Rotate secrets if needed
   - Register/unregister for sidecar injection
3. Update the AppConfig status with:
   - Conditions (Ready, Error, etc.)
   - Last sync time
   - List of created resources
4. Return an UpdateControl to schedule the next reconciliation

## Error Handling

The operator uses a combination of:
- Retry with exponential backoff for transient errors
- Status conditions to report persistent errors
- Logging for debugging and audit purposes
- Health indicators for monitoring

## Monitoring and Health

The operator exposes health endpoints via Spring Boot Actuator:
- `/actuator/health` - Overall health status
- `/actuator/info` - Operator information
- `/actuator/metrics` - Operational metrics

## Deployment

The operator is deployed as a Kubernetes Deployment with:
- A single replica (leader election for HA is supported)
- RBAC permissions to watch and modify resources
- Resource limits and requests
- Liveness and readiness probes

## Security Considerations

- The operator uses least-privilege RBAC permissions
- Secrets are handled securely with proper Kubernetes APIs
- All resources created have proper owner references for garbage collection
- Network policies restrict communication to only necessary services
