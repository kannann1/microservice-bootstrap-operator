# Microservice Operator (Java Implementation)

A Kubernetes operator for automating microservice bootstrapping and secret rotation, implemented in Java using the Java Operator SDK. Complete documentation is available at [microservice-bootstrap-operator-java](docs/README.md).

## Overview

This operator automates the following tasks for microservices:

- Generates ConfigMaps/Secrets from GitHub repositories
- Injects sidecars into pods based on label selectors
- Sets up RBAC and NetworkPolicy
- Handles secret rotation
- Uses finalizers to clean up dependent resources

## Architecture

The operator uses the Kubernetes Operator pattern with the Java Operator SDK to watch for AppConfig custom resources and reconcile the desired state with the actual state in the cluster.

### Custom Resource: AppConfig

The AppConfig custom resource defines:

- GitHub repository containing configuration templates
- Sidecar injection configuration (container image, env vars, volume mounts, label selectors)
- Secret rotation settings
- RBAC configuration
- Network policy configuration

## Installation

### Prerequisites

- Kubernetes cluster 1.19+
- kubectl 1.19+
- Java 22+
- Maven 3.6+

### Building the Operator

```bash
# Build the project
mvn clean package

# Build the Docker image
docker build -t your-registry/microservice-bootstrap-operator-java:v0.1.0 .
```

### Installing the CRD

```bash
kubectl apply -f k8s/crd/microservice.github.io_appconfigs.yaml
```

### Deploying the Operator

1. Push the image to your registry:

```bash
docker push your-registry/microservice-operator-java:v0.1.0
```

2. Deploy the operator:

```bash
# Create namespace
kubectl create namespace microservice-operator-system

# Deploy the operator
# (In a production environment, you would use proper RBAC and deployment manifests)
kubectl create deployment microservice-bootstrap-operator-java \
  --image=your-registry/microservice-bootstrap-operator-java:v0.1.0 \
  -n microservice-bootstrap-operator-system
```

## Features

### Sidecar Injection

The operator can automatically inject sidecar containers into pods based on namespace and label selectors. This is useful for adding logging, monitoring, security, or other capabilities to your applications without modifying their deployment manifests.

Key features of the sidecar injection:

- **Label-based selection**: Only inject sidecars into pods matching specific labels
- **Namespace scoping**: Sidecars are only injected into pods in the same namespace as the AppConfig
- **Dynamic configuration**: Environment variables, volumes, and volume mounts can be configured
- **Idempotent operation**: Pods that already have the sidecar container are skipped

The sidecar injection works by watching for pod creation events across the cluster and patching matching pods with the sidecar container configuration.

## Usage

Create an AppConfig resource to define your microservice configuration:

```yaml
apiVersion: microservice.github.io/v1
kind: AppConfig
metadata:
  name: sample-app
  namespace: default
spec:
  appName: sample-microservice
  githubRepo: https://github.com/example/sample-config
  githubRef: main
  configPath: /configs
  sidecarInjection:
    enabled: true
    name: logging-sidecar
    image: fluent/fluent-bit:latest
    selectorLabels:
      app: sample-app
      component: web
    env:
      LOG_LEVEL: info
      FLUSH_INTERVAL: "5"
    volumes:
      - config-volume
    volumeMounts:
      config-volume: /fluent-bit/etc
  secretRotation:
    enabled: true
    intervalHours: 24
    sources:
      - vault
  rbac:
    serviceAccountName: sample-app-sa
    roles:
      - configReader
    roleBindings:
      - configReaderBinding
  networkPolicy:
    enabled: true
    ingress:
      - "allow-from-same-namespace"
    egress:
      - "allow-dns"
```

Apply the AppConfig:

```bash
kubectl apply -f k8s/samples/sample-appconfig.yaml
```

## Development

### Running Locally

```bash
mvn spring-boot:run
```

### Running Tests

```bash
mvn test
```

## Implementation Details

### Key Components

1. **Custom Resource Definition (CRD)**: 
   - `AppConfig` - Defines the desired state for microservice configuration

2. **Controller**:
   - `AppConfigController` - Implements the reconciliation logic for AppConfig resources

3. **Services**:
   - `ConfigMapService` - Handles syncing configuration from GitHub
   - `RBACService` - Sets up RBAC resources
   - `NetworkPolicyService` - Configures network policies
   - `SecretRotationService` - Manages secret rotation

4. **Finalizers**:
   - Used to ensure proper cleanup of dependent resources when an AppConfig is deleted

## License

MIT
