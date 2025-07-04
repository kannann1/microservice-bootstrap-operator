#!/bin/bash
echo "Deploying the Microservice Bootstrap Operator (Java) to Kubernetes..."

REGISTRY="localhost:5000"
IMAGE_NAME="microservice-bootstrap-operator-java"
TAG="latest"
FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}:${TAG}"

echo "Building Docker image: ${FULL_IMAGE_NAME}"
docker build -t ${FULL_IMAGE_NAME} .

if [ $? -ne 0 ]; then
    echo "Docker build failed!"
    exit 1
fi

echo "Pushing Docker image to registry..."
docker push ${FULL_IMAGE_NAME}

if [ $? -ne 0 ]; then
    echo "Docker push failed!"
    exit 1
fi

echo "Applying CRD..."
kubectl apply -f k8s/crd/microservice.github.io_appconfigs.yaml

echo "Deploying operator..."
kubectl apply -f k8s/deploy/operator.yaml

echo "Deployment complete! You can check the status with:"
echo "kubectl get pods -n microservice-bootstrap-operator-system"
