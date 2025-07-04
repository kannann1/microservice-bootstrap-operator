# Microservice Operator (Java Implementation)

A Kubernetes operator for automating microservice bootstrapping and secret rotation, implemented in Java using the Java Operator SDK.

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

### Secret Rotation

The operator provides automated secret rotation capabilities with multiple strategies to handle different types of secrets. This helps maintain security best practices by regularly rotating credentials without manual intervention.

Key features of the secret rotation:

- **Multiple rotation strategies**:
  - **Default**: Simple password rotation with configurable length and complexity
  - **Database**: Generates database credentials with username and password
  - **API Key**: Creates API keys and secrets for external service authentication
  - **TLS**: Generates self-signed TLS certificates for development/testing

- **Configurable rotation intervals**: Set how frequently secrets should be rotated (in hours)
- **Strategy-specific configuration**: Each strategy supports additional parameters via `strategyConfig`
- **Automatic tracking**: Rotation timestamps and IDs are stored in annotations and secret data
- **Kubernetes integration**: Uses owner references to tie secret lifecycle to AppConfig resources
- **Resilient operation**: Implements retry logic with exponential backoff for API failures

#### Secret Rotation Strategies

##### Default Strategy

Generates a simple random password and stores it in the secret.

```yaml
secretRotation:
  enabled: true
  intervalHours: 24  # Rotate every 24 hours
  strategy: default   # Use default strategy
  sources:
    - app-credentials
  strategyConfig:
    passwordLength: "16"  # Optional: Configure password length
```

##### Database Strategy

Generates database credentials including username and password.

```yaml
secretRotation:
  enabled: true
  intervalHours: 48  # Rotate every 48 hours
  strategy: database  # Use database strategy
  sources:
    - db-credentials
  strategyConfig:
    dbType: postgresql  # Database type (postgresql, mysql, etc.)
    usernamePrefix: "app"  # Optional: Prefix for generated usernames
    passwordLength: "20"   # Optional: Configure password length
```

##### API Key Strategy

Generates API keys and secrets for external service authentication.

```yaml
secretRotation:
  enabled: true
  intervalHours: 168  # Rotate weekly
  strategy: api-key   # Use API key strategy
  sources:
    - external-api-credentials
  strategyConfig:
    keyLength: "32"    # Length of the API key
    secretLength: "64" # Length of the API secret
```

##### TLS Strategy

Generates self-signed TLS certificates for development/testing.

```yaml
secretRotation:
  enabled: true
  intervalHours: 720  # Rotate monthly
  strategy: tls       # Use TLS strategy
  sources:
    - app-tls-cert
  strategyConfig:
    commonName: "api.example.com"  # Certificate common name
    organization: "Example Corp"   # Organization name
    validityDays: "90"            # Certificate validity period
```

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
      - db-credentials
      - api-keys
    strategy: database
    strategyConfig:
      dbType: postgresql
      dbHost: postgres.default.svc.cluster.local
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
