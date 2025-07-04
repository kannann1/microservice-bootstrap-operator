@echo off
REM Script to create a new release of the Microservice Bootstrap Operator

setlocal EnableDelayedExpansion

REM Default values
set VERSION=
set SKIP_TESTS=false
set PUSH_IMAGES=true
set DRY_RUN=false

REM Parse command line arguments
:parse_args
if "%~1"=="" goto :end_parse_args
if /i "%~1"=="-s" (
    set SKIP_TESTS=true
    shift
    goto :parse_args
)
if /i "%~1"=="--skip-tests" (
    set SKIP_TESTS=true
    shift
    goto :parse_args
)
if /i "%~1"=="-n" (
    set PUSH_IMAGES=false
    shift
    goto :parse_args
)
if /i "%~1"=="--no-push" (
    set PUSH_IMAGES=false
    shift
    goto :parse_args
)
if /i "%~1"=="-d" (
    set DRY_RUN=true
    shift
    goto :parse_args
)
if /i "%~1"=="--dry-run" (
    set DRY_RUN=true
    shift
    goto :parse_args
)
if /i "%~1"=="-h" (
    goto :show_help
)
if /i "%~1"=="--help" (
    goto :show_help
)
if "%VERSION%"=="" (
    set VERSION=%~1
    shift
    goto :parse_args
) else (
    echo Unknown option: %~1
    goto :show_help
)

:end_parse_args

REM Validate version
if "%VERSION%"=="" (
    echo Error: Version is required
    goto :show_help
)

echo %VERSION% | findstr /r "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul
if %ERRORLEVEL% neq 0 (
    echo Error: Version must be in format x.y.z ^(e.g., 1.0.0^)
    exit /b 1
)

REM Check if git is installed
git --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: git is not installed
    exit /b 1
)

REM Check if we're in a git repository
git rev-parse --is-inside-work-tree >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: Not in a git repository
    exit /b 1
)

REM Check for uncommitted changes
git status --porcelain >nul
if %ERRORLEVEL% equ 0 (
    for /f %%i in ('git status --porcelain') do (
        echo Error: There are uncommitted changes in the repository
        echo Please commit or stash your changes before creating a release
        exit /b 1
    )
)

REM Check if the tag already exists
git rev-parse "v%VERSION%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo Error: Tag v%VERSION% already exists
    exit /b 1
)

echo Creating release v%VERSION%...
echo Dry run: %DRY_RUN%
echo Skip tests: %SKIP_TESTS%
echo Push images: %PUSH_IMAGES%
echo.

REM Update version in pom.xml
echo Updating version in pom.xml...
cd ..\Java
call mvn versions:set -DnewVersion=%VERSION% -DgenerateBackupPoms=false

REM Update version in Chart.yaml
echo Updating version in Chart.yaml...
cd ..\helm\microservice-bootstrap-operator
powershell -Command "(Get-Content Chart.yaml) -replace '^version: .*', 'version: %VERSION%' | Set-Content Chart.yaml"
powershell -Command "(Get-Content Chart.yaml) -replace '^appVersion: .*', 'appVersion: \"%VERSION%\"' | Set-Content Chart.yaml"
cd ..\..\scripts

REM Run tests if not skipped
if "%SKIP_TESTS%"=="false" (
    echo Running tests...
    cd ..\Java
    call mvn clean test
    cd ..\scripts
)

REM Build the project
echo Building the project...
cd ..\Java
call mvn clean package -DskipTests
cd ..\scripts

REM Commit version changes
echo Committing version changes...
if "%DRY_RUN%"=="false" (
    git add ..\Java\pom.xml ..\helm\microservice-bootstrap-operator\Chart.yaml
    git commit -m "chore: prepare release v%VERSION%"
)

REM Create tag
echo Creating tag v%VERSION%...
if "%DRY_RUN%"=="false" (
    git tag -a "v%VERSION%" -m "Release v%VERSION%"
)

REM Build and push Docker image if requested
if "%PUSH_IMAGES%"=="true" (
    echo Building Docker image...
    cd ..\Java
    docker build -t kannann1/microservice-bootstrap-operator:%VERSION% .
    docker tag kannann1/microservice-bootstrap-operator:%VERSION% kannann1/microservice-bootstrap-operator:latest
    
    if "%DRY_RUN%"=="false" (
        echo Pushing Docker image...
        docker push kannann1/microservice-bootstrap-operator:%VERSION%
        docker push kannann1/microservice-bootstrap-operator:latest
    )
    cd ..\scripts
)

REM Push changes and tag if not dry run
if "%DRY_RUN%"=="false" (
    echo Pushing changes and tag...
    git push origin main
    git push origin "v%VERSION%"
    
    echo Release v%VERSION% created and pushed successfully!
    echo.
    echo The CI/CD pipeline will now:
    echo 1. Build and test the project
    echo 2. Build and push the Docker image
    echo 3. Package and publish the Helm chart
    echo 4. Create a GitHub release
) else (
    echo Dry run completed. No changes were pushed.
)

echo.
echo To deploy this release to a Kubernetes cluster, run:
echo deploy-operator.bat --tag %VERSION% --env prod

exit /b 0

:show_help
echo Usage: %0 [options] ^<version^>
echo.
echo Arguments:
echo   version                       Version to release (e.g., 1.0.0)
echo.
echo Options:
echo   -s, --skip-tests              Skip running tests
echo   -n, --no-push                 Don't push Docker images
echo   -d, --dry-run                 Perform a dry run (no git tags or pushes)
echo   -h, --help                    Show this help message
exit /b 0
