@echo off
REM Script to deploy the Microservice Bootstrap Operator to a Kubernetes cluster

setlocal EnableDelayedExpansion

REM Default values
set NAMESPACE=microservice-bootstrap-operator-system
set RELEASE_NAME=microservice-bootstrap-operator
set CHART_PATH=..\helm\microservice-bootstrap-operator
set IMAGE_TAG=latest
set ENVIRONMENT=dev
set HELM_REPO=kannann1
set HELM_REPO_URL=https://kannann1.github.io/charts
set USE_LOCAL_CHART=true

REM Parse command line arguments
:parse_args
if "%~1"=="" goto :end_parse_args
if /i "%~1"=="-n" (
    set NAMESPACE=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--namespace" (
    set NAMESPACE=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="-r" (
    set RELEASE_NAME=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--release" (
    set RELEASE_NAME=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="-c" (
    set CHART_PATH=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--chart" (
    set CHART_PATH=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="-t" (
    set IMAGE_TAG=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--tag" (
    set IMAGE_TAG=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="-e" (
    set ENVIRONMENT=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--env" (
    set ENVIRONMENT=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="-l" (
    set USE_LOCAL_CHART=true
    shift
    goto :parse_args
)
if /i "%~1"=="--local" (
    set USE_LOCAL_CHART=true
    shift
    goto :parse_args
)
if /i "%~1"=="-h" (
    goto :show_help
)
if /i "%~1"=="--help" (
    goto :show_help
)
echo Unknown option: %~1
goto :show_help

:end_parse_args

REM Validate environment
if not "%ENVIRONMENT%"=="dev" if not "%ENVIRONMENT%"=="staging" if not "%ENVIRONMENT%"=="prod" (
    echo Error: Environment must be one of: dev, staging, prod
    exit /b 1
)

REM Check if kubectl is installed
kubectl version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: kubectl is not installed
    exit /b 1
)

REM Check if helm is installed
helm version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: helm is not installed
    exit /b 1
)

REM Check current Kubernetes context
for /f "tokens=*" %%i in ('kubectl config current-context') do set CURRENT_CONTEXT=%%i
echo Current Kubernetes context: %CURRENT_CONTEXT%
set /p CONFIRM=Continue with this context? (y/n): 
if /i not "%CONFIRM%"=="y" (
    echo Deployment aborted
    exit /b 0
)

REM Create namespace if it doesn't exist
echo Creating namespace %NAMESPACE% if it doesn't exist...
kubectl create namespace %NAMESPACE% --dry-run=client -o yaml | kubectl apply -f -

REM Apply CRD
echo Applying AppConfig CRD...
kubectl apply -f ..\Java\k8s\appconfig-crd.yaml

REM Add Helm repo if using remote chart
if "%USE_LOCAL_CHART%"=="false" (
    echo Adding Helm repository...
    helm repo add %HELM_REPO% %HELM_REPO_URL%
    helm repo update
    set CHART_PATH=%HELM_REPO%/%RELEASE_NAME%
)

REM Deploy using Helm
echo Deploying %RELEASE_NAME% to %NAMESPACE% with image tag %IMAGE_TAG%...
helm upgrade --install %RELEASE_NAME% %CHART_PATH% ^
  --namespace %NAMESPACE% ^
  --set image.tag=%IMAGE_TAG% ^
  --set environment=%ENVIRONMENT% ^
  --wait --timeout 120s

REM Verify deployment
echo Verifying deployment...
kubectl rollout status deployment/%RELEASE_NAME% -n %NAMESPACE% --timeout=120s

echo Deployment complete!
echo To check the operator logs, run:
echo kubectl logs -f deployment/%RELEASE_NAME% -n %NAMESPACE%

REM Show available AppConfigs
echo Available AppConfigs in namespace %NAMESPACE%:
kubectl get appconfigs -n %NAMESPACE%

exit /b 0

:show_help
echo Usage: %0 [options]
echo.
echo Options:
echo   -n, --namespace ^<namespace^>    Kubernetes namespace (default: %NAMESPACE%)
echo   -r, --release ^<name^>           Helm release name (default: %RELEASE_NAME%)
echo   -c, --chart ^<path^>             Path to Helm chart (default: %CHART_PATH%)
echo   -t, --tag ^<tag^>                Image tag to deploy (default: %IMAGE_TAG%)
echo   -e, --env ^<environment^>        Environment (dev, staging, prod) (default: %ENVIRONMENT%)
echo   -l, --local                    Use local chart (default: %USE_LOCAL_CHART%)
echo   -h, --help                     Show this help message
exit /b 0
