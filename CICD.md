# CI/CD Pipeline Overview

## Documentation Publishing

The CI/CD pipeline begins with the documentation publishing workflow, which ensures that all documentation is up-to-date before any build or deployment processes run.

### Workflow: docs-publish.yml

- **Trigger**: Runs on any push to the main branch or manual trigger
- **Process**:
  1. Collects all Markdown files from the repository
  2. Builds a documentation site using MkDocs with Material theme
  3. Deploys the documentation to GitHub Pages
- **Dependencies**: Requires GitHub Pages to be enabled in repository settings (see SETUP.md)

### Integration with CI/CD Pipeline

The main CI/CD pipeline is configured to run only after the documentation publishing workflow completes successfully. This ensures that documentation is always published before any new builds or releases.

# CI/CD Pipeline for Microservice Bootstrap Operator

This document describes the CI/CD pipeline setup for the Microservice Bootstrap Operator project.

## Overview

The CI/CD pipeline automates the build, test, and deployment processes for the Microservice Bootstrap Operator. It includes:

1. **Building and testing** the Java application
2. **Code quality checks** and security scanning
3. **Docker image building** and publishing
4. **Helm chart packaging** and publishing
5. **Deployment** to Kubernetes environments
6. **Release management** for versioned releases

## GitHub Actions Workflows

### Main CI/CD Pipeline (`ci-cd.yml`)

The main workflow is triggered on:
- Push to `main` branch
- Push of version tags (`v*`)
- Pull requests to `main` branch
- Manual workflow dispatch with environment selection

The workflow consists of the following jobs:

#### 1. Build and Test
- Builds the Java application with Maven
- Runs unit tests with JaCoCo coverage
- Performs SonarQube code quality analysis
- Runs OWASP dependency check for security vulnerabilities
- Uploads build artifacts for subsequent jobs

#### 2. Docker Build
- Builds the Docker image
- Scans the image for security vulnerabilities using Trivy
- Pushes the image to Docker Hub with appropriate tags
- Verifies the image signature

#### 3. Helm Package
- Updates Helm chart version based on Git tag or commit
- Lints and validates the Helm chart
- Packages the Helm chart
- Publishes the chart to GitHub Pages Helm repository

#### 4. Deploy
- Deploys the operator to the specified Kubernetes environment
- Verifies the deployment
- Runs smoke tests
- Sends deployment notifications

#### 5. Release
- Creates a GitHub release with release notes
- Attaches artifacts (JAR and Helm chart)
- Provides installation instructions

### Docker Security Scan (`docker-security-scan.yml`)

A dedicated workflow for regular security scanning of Docker images:
- Runs weekly and on-demand
- Performs deep security scanning with Trivy
- Uploads results to GitHub Security tab
- Generates detailed security reports
- Scans for sensitive data using Gitleaks

## Configuration Files

### Repository Configuration (`.github/repository-config.yml`)

Defines GitHub repository settings:
- Repository metadata
- Branch protection rules
- Issue labels

### Docker Hub Configuration (`.github/dockerhub-config.yml`)

Defines Docker Hub repository settings:
- Repository metadata
- Build settings
- Security scanning configuration

### Helm Repository Configuration (`.github/helm-repo-config.yml`)

Defines Helm chart repository settings:
- Repository metadata
- Chart information
- Release process configuration

## Required Secrets

The following secrets need to be configured in your GitHub repository:

| Secret Name | Description |
|-------------|-------------|
| `DOCKERHUB_USERNAME` | Docker Hub username for image publishing |
| `DOCKERHUB_TOKEN` | Docker Hub access token for authentication |
| `SONAR_TOKEN` | SonarQube token for code quality analysis |
| `KUBE_CONFIG` | Kubernetes configuration for deployment |
| `SLACK_WEBHOOK` | Slack webhook URL for notifications (optional) |

## Environments

The CI/CD pipeline supports deployment to multiple environments:

- **dev**: Development environment (default for manual triggers)
- **staging**: Staging/QA environment
- **prod**: Production environment

## Versioning

The project follows semantic versioning:

- Version tags should follow the format `v1.2.3`
- The Docker image is tagged with:
  - Full version (`1.2.3`)
  - Minor version (`1.2`)
  - Branch name for non-tag builds
  - Short SHA for all builds

## Usage

### Triggering the Pipeline

- **Automatic**: Push to `main` or create a pull request
- **Release**: Push a tag in the format `v*` (e.g., `v1.0.0`)
- **Manual**: Use the "Run workflow" button in GitHub Actions, selecting the desired environment

### Monitoring

- View workflow runs in the GitHub Actions tab
- Check deployment status in the Kubernetes dashboard
- Review security reports in the GitHub Security tab

## Troubleshooting

### Common Issues

1. **Build Failures**
   - Check Maven build logs for compilation errors
   - Verify test failures in the test results artifact

2. **Docker Build Issues**
   - Ensure Dockerfile is correctly configured
   - Check for base image compatibility issues

3. **Deployment Failures**
   - Verify Kubernetes cluster access
   - Check Helm chart values for errors
   - Examine Kubernetes events for the deployment

### Getting Help

For assistance with CI/CD issues:
- Check GitHub Actions documentation
- Review workflow run logs
- Contact the DevOps team

## Future Improvements

- Add integration testing in a dedicated environment
- Implement canary deployments
- Add performance testing
- Enhance monitoring and alerting
- Implement GitOps workflow with ArgoCD or Flux
