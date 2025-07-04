# Microservice Bootstrap Operator Helm Chart

This Helm chart deploys the Microservice Bootstrap Operator, which automates the setup and configuration of microservices in a Kubernetes cluster.

## Features

- Sidecar injection
- RBAC setup
- Network policy management
- Config synchronization
- Secret rotation

## Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+

## Installing the Chart

Add the K8s Operators Helm repository:

```bash
helm repo add k8soperators https://k8soperators.github.io/charts
helm repo update
```

Install the chart with the release name `microservice-bootstrap-operator`:

```bash
helm install microservice-bootstrap-operator k8soperators/microservice-bootstrap-operator
```

The command deploys the Microservice Bootstrap Operator on the Kubernetes cluster with default configuration. The [Parameters](#parameters) section lists the parameters that can be configured during installation.

## Uninstalling the Chart

To uninstall/delete the `microservice-bootstrap-operator` deployment:

```bash
helm delete microservice-bootstrap-operator
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Parameters

### Global parameters

| Name                      | Description                                     | Value |
| ------------------------- | ----------------------------------------------- | ----- |
| `replicaCount`            | Number of replicas for the operator             | `1`   |
| `nameOverride`            | String to partially override the release name   | `""`  |
| `fullnameOverride`        | String to fully override the release name       | `""`  |

### Image parameters

| Name                | Description                                        | Value                                    |
| ------------------- | -------------------------------------------------- | ---------------------------------------- |
| `image.repository`  | Operator image repository                          | `k8soperators/microservice-bootstrap-operator` |
| `image.tag`         | Operator image tag (defaults to Chart appVersion)  | `""`                                     |
| `image.pullPolicy`  | Operator image pull policy                         | `IfNotPresent`                           |
| `imagePullSecrets`  | Specify image pull secrets                         | `[]`                                     |

### Operator parameters

| Name                                | Description                                                | Value     |
| ----------------------------------- | ---------------------------------------------------------- | --------- |
| `operator.logLevel`                 | Log level for the operator                                 | `info`    |
| `operator.watchNamespace`           | Namespace to watch for AppConfig resources (empty for all) | `""`      |
| `operator.leaderElection.enabled`   | Enable leader election for HA deployments                  | `false`   |
| `operator.metrics.enabled`          | Enable metrics endpoint                                    | `true`    |
| `operator.metrics.service.type`     | Metrics service type                                       | `ClusterIP` |
| `operator.metrics.service.port`     | Metrics service port                                       | `8080`    |
| `operator.metrics.serviceMonitor.enabled` | Enable ServiceMonitor for Prometheus Operator        | `false`   |

### CRD parameters

| Name                | Description                                        | Value   |
| ------------------- | -------------------------------------------------- | ------- |
| `crds.create`       | Create CRD resources                               | `true`  |
| `crds.keep`         | Keep CRDs on chart uninstall                       | `true`  |

### RBAC parameters

| Name                         | Description                                               | Value  |
| ---------------------------- | --------------------------------------------------------- | ------ |
| `serviceAccount.create`      | Create a service account for the operator                 | `true` |
| `serviceAccount.annotations` | Annotations to add to the service account                 | `{}`   |
| `serviceAccount.name`        | The name of the service account to use                    | `""`   |

### Resource management parameters

| Name                | Description                                        | Value   |
| ------------------- | -------------------------------------------------- | ------- |
| `resources.limits.cpu`      | CPU limit for the operator                 | `500m`  |
| `resources.limits.memory`   | Memory limit for the operator              | `512Mi` |
| `resources.requests.cpu`    | CPU request for the operator               | `100m`  |
| `resources.requests.memory` | Memory request for the operator            | `128Mi` |
| `nodeSelector`              | Node labels for pod assignment             | `{}`    |
| `tolerations`               | Tolerations for pod assignment             | `[]`    |
| `affinity`                  | Affinity for pod assignment                | `{}`    |

## Configuration Examples

### Watching specific namespace

```yaml
operator:
  watchNamespace: "my-namespace"
```

### Enabling high availability with leader election

```yaml
replicaCount: 3
operator:
  leaderElection:
    enabled: true
    leaseDuration: 15
    renewDeadline: 10
    retryPeriod: 2
```

### Configuring resource limits

```yaml
resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 200m
    memory: 256Mi
```

## Using the Operator

Once the operator is installed, you can create AppConfig resources to configure your microservices:

```yaml
apiVersion: microservice.github.io/v1
kind: AppConfig
metadata:
  name: my-app
  namespace: default
spec:
  appName: my-app
  sidecarInjection:
    enabled: true
    image: nginx:latest
    selectorLabels:
      app: my-app
    env:
      LOG_LEVEL: info
  rbac:
    serviceAccountName: my-app-sa
  networkPolicy:
    enabled: true
```

For more information on using the operator, see the [documentation](https://github.com/k8soperators/microservice-bootstrap-operator/tree/main/docs).
