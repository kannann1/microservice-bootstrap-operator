#!/bin/bash
# Script to deploy the Microservice Bootstrap Operator to a Kubernetes cluster

set -e

# Default values
NAMESPACE="microservice-bootstrap-operator-system"
RELEASE_NAME="microservice-bootstrap-operator"
CHART_PATH="../helm/microservice-bootstrap-operator"
IMAGE_TAG="latest"
ENVIRONMENT="dev"
HELM_REPO="kannann1"
HELM_REPO_URL="https://kannann1.github.io/charts"
USE_LOCAL_CHART=true

# Display help
function show_help {
  echo "Usage: $0 [options]"
  echo ""
  echo "Options:"
  echo "  -n, --namespace <namespace>    Kubernetes namespace (default: $NAMESPACE)"
  echo "  -r, --release <name>           Helm release name (default: $RELEASE_NAME)"
  echo "  -c, --chart <path>             Path to Helm chart (default: $CHART_PATH)"
  echo "  -t, --tag <tag>                Image tag to deploy (default: $IMAGE_TAG)"
  echo "  -e, --env <environment>        Environment (dev, staging, prod) (default: $ENVIRONMENT)"
  echo "  -l, --local                    Use local chart (default: $USE_LOCAL_CHART)"
  echo "  -h, --help                     Show this help message"
  exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    -n|--namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    -r|--release)
      RELEASE_NAME="$2"
      shift 2
      ;;
    -c|--chart)
      CHART_PATH="$2"
      shift 2
      ;;
    -t|--tag)
      IMAGE_TAG="$2"
      shift 2
      ;;
    -e|--env)
      ENVIRONMENT="$2"
      shift 2
      ;;
    -l|--local)
      USE_LOCAL_CHART=true
      shift
      ;;
    -h|--help)
      show_help
      ;;
    *)
      echo "Unknown option: $1"
      show_help
      ;;
  esac
done

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
  echo "Error: Environment must be one of: dev, staging, prod"
  exit 1
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
  echo "Error: kubectl is not installed"
  exit 1
fi

# Check if helm is installed
if ! command -v helm &> /dev/null; then
  echo "Error: helm is not installed"
  exit 1
fi

# Check current Kubernetes context
CURRENT_CONTEXT=$(kubectl config current-context)
echo "Current Kubernetes context: $CURRENT_CONTEXT"
read -p "Continue with this context? (y/n): " CONFIRM
if [[ "$CONFIRM" != "y" ]]; then
  echo "Deployment aborted"
  exit 0
fi

# Create namespace if it doesn't exist
echo "Creating namespace $NAMESPACE if it doesn't exist..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Apply CRD
echo "Applying AppConfig CRD..."
kubectl apply -f ../Java/k8s/appconfig-crd.yaml

# Add Helm repo if using remote chart
if [[ "$USE_LOCAL_CHART" == "false" ]]; then
  echo "Adding Helm repository..."
  helm repo add $HELM_REPO $HELM_REPO_URL
  helm repo update
  CHART_PATH="$HELM_REPO/$RELEASE_NAME"
fi

# Deploy using Helm
echo "Deploying $RELEASE_NAME to $NAMESPACE with image tag $IMAGE_TAG..."
helm upgrade --install $RELEASE_NAME $CHART_PATH \
  --namespace $NAMESPACE \
  --set image.tag=$IMAGE_TAG \
  --set environment=$ENVIRONMENT \
  --wait --timeout 120s

# Verify deployment
echo "Verifying deployment..."
kubectl rollout status deployment/$RELEASE_NAME -n $NAMESPACE --timeout=120s

echo "Deployment complete!"
echo "To check the operator logs, run:"
echo "kubectl logs -f deployment/$RELEASE_NAME -n $NAMESPACE"

# Show available AppConfigs
echo "Available AppConfigs in namespace $NAMESPACE:"
kubectl get appconfigs -n $NAMESPACE
