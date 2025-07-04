@echo off
echo Deploying the Microservice Bootstrap Operator (Java) to Kubernetes...

set REGISTRY=localhost:5000
set IMAGE_NAME=microservice-bootstrap-operator-java
set TAG=latest
set FULL_IMAGE_NAME=%REGISTRY%/%IMAGE_NAME%:%TAG%

echo Building Docker image: %FULL_IMAGE_NAME%
docker build -t %FULL_IMAGE_NAME% .

if %ERRORLEVEL% NEQ 0 (
    echo Docker build failed!
    exit /b %ERRORLEVEL%
)

echo Pushing Docker image to registry...
docker push %FULL_IMAGE_NAME%

if %ERRORLEVEL% NEQ 0 (
    echo Docker push failed!
    exit /b %ERRORLEVEL%
)

echo Applying CRD...
kubectl apply -f k8s/crd/microservice.github.io_appconfigs.yaml

echo Deploying operator...
kubectl apply -f k8s/deploy/operator.yaml

echo Deployment complete! You can check the status with:
echo kubectl get pods -n microservice-bootstrap-operator-system
