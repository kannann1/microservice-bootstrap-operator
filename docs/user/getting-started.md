# Getting Started with Microservice Bootstrap Operator

This guide will help you install and start using the Microservice Bootstrap Operator in your Kubernetes cluster.

## Prerequisites

- Kubernetes cluster (v1.19+)
- kubectl configured to communicate with your cluster
- Helm (optional, for Helm-based installation)

## Installation

### Using kubectl

1. Apply the CRD:

```bash
kubectl apply -f https://raw.githubusercontent.com/kannann1/microservice-bootstrap-operator/main/k8s/crd/microservice.github.io_appconfigs.yaml
```

2. Deploy the operator:

```bash
kubectl apply -f https://raw.githubusercontent.com/kannann1/microservice-bootstrap-operator/main/k8s/deploy/operator.yaml
```

### Using Helm

```bash
helm repo add kannann1 https://kannann1.github.io/charts
helm repo update
helm install microservice-bootstrap-operator kannann1/microservice-bootstrap-operator
```

## Verify Installation

Check that the operator pod is running:

```bash
kubectl get pods -n microservice-bootstrap-operator-system
```

You should see output similar to:

```
NAME                                                    READY   STATUS    RESTARTS   AGE
microservice-bootstrap-operator-controller-manager-0    1/1     Running   0          1m
```

## Creating Your First AppConfig

Create a file named `my-appconfig.yaml` with the following content:

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
    volumeMounts:
      config-volume: /etc/config
  rbac:
    serviceAccountName: my-app-sa
    roles:
      - my-app-role
    roleBindings:
      - my-app-role-binding
  networkPolicy:
    enabled: true
    ingressRules:
      - from:
          - podSelector:
              matchLabels:
                app: frontend
        ports:
          - protocol: TCP
            port: 8080
```

Apply the AppConfig:

```bash
kubectl apply -f my-appconfig.yaml
```

## Deploy Your Application

Create a deployment that will receive the sidecar injection:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
      - name: main
        image: busybox
        command: ['sh', '-c', 'echo The app is running! && sleep 3600']
```

Apply the deployment:

```bash
kubectl apply -f my-app-deployment.yaml
```

## Verify Sidecar Injection

Check that the pod has the sidecar container injected:

```bash
kubectl get pod -l app=my-app -o jsonpath='{.items[0].spec.containers[*].name}'
```

You should see output similar to:

```
main my-app-sidecar
```

## Next Steps

- Learn more about the [AppConfig CRD](./appconfig-reference.md)
- Explore [example configurations](./examples.md)
- Check out [troubleshooting tips](./troubleshooting.md) if you encounter issues
