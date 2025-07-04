# Microservice Bootstrap Operator CI/CD Setup Guide

This guide provides step-by-step instructions for setting up the CI/CD pipeline for the Microservice Bootstrap Operator project.

## Prerequisites

- GitHub account with admin access to the repository
- Docker Hub account
- SonarCloud account (optional, for code quality analysis)
- Kubernetes cluster for deployment
- Slack workspace (optional, for notifications)

## 1. GitHub Repository Setup

### 1.1 Create GitHub Repository

1. Create a new GitHub repository named `microservice-bootstrap-operator`
2. Push your local code to the repository

### 1.2 Configure GitHub Repository Settings

The repository configuration is defined in `.github/repository-config.yml`. GitHub will automatically apply these settings.

Key settings include:
- Branch protection rules for `main`
- Required status checks
- Issue and PR templates
- Labels for issues and PRs

## 2. Configure GitHub Secrets

Add the following secrets to your GitHub repository:

1. Go to your repository → Settings → Secrets and variables → Actions
2. Add the following secrets:

| Secret Name | Description |
|-------------|-------------|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token (not your password) |
| `SONAR_TOKEN` | SonarCloud token for code quality analysis |
| `KUBE_CONFIG` | Base64-encoded Kubernetes configuration file |
| `SLACK_WEBHOOK` | Slack webhook URL for notifications |

### 2.1 Creating Docker Hub Token

1. Log in to [Docker Hub](https://hub.docker.com/)
2. Go to Account Settings → Security → New Access Token
3. Give it a name (e.g., "GitHub Actions") and create the token
4. Copy the token and add it as the `DOCKERHUB_TOKEN` secret

### 2.2 Creating SonarCloud Token

1. Log in to [SonarCloud](https://sonarcloud.io/)
2. Create a new organization or use an existing one
3. Create a new project for your repository
4. Go to My Account → Security → Generate Tokens
5. Create a token and add it as the `SONAR_TOKEN` secret

### 2.3 Creating Kubernetes Config

1. Get your Kubernetes configuration file (usually `~/.kube/config`)
2. Encode it to base64:
   ```bash
   cat ~/.kube/config | base64 -w 0
   ```
3. Add the output as the `KUBE_CONFIG` secret

### 2.4 Creating Slack Webhook

1. Go to your Slack workspace → Administration → Manage Apps
2. Search for "Incoming Webhooks" and add it to your workspace
3. Create a new webhook for a channel (e.g., #deployments)
4. Copy the webhook URL and add it as the `SLACK_WEBHOOK` secret

## 3. Docker Hub Setup

### 3.1 Create Docker Hub Repository

1. Log in to [Docker Hub](https://hub.docker.com/)
2. Create a new repository named `microservice-bootstrap-operator`
3. Set it to public or private as needed

The Docker Hub configuration is defined in `.github/dockerhub-config.yml`.

## 4. Helm Chart Repository Setup

### 4.1 Configure GitHub Pages for Helm Charts

1. Go to your repository → Settings → Pages
2. Set Source to "Deploy from a branch"
3. Select the `gh-pages` branch and root directory
4. Save the settings

The Helm repository configuration is defined in `.github/helm-repo-config.yml`.

## 5. Understanding the CI/CD Workflows

### 5.1 Main CI/CD Pipeline

The main workflow is defined in `.github/workflows/ci-cd.yml` and includes:

- **Build and Test**: Builds the Java application, runs tests, and performs code quality checks
- **Docker Build**: Builds and pushes the Docker image
- **Helm Package**: Packages and publishes the Helm chart
- **Deploy**: Deploys the operator to Kubernetes
- **Release**: Creates GitHub releases for tagged versions

### 5.2 Docker Security Scan

A dedicated workflow for Docker image security scanning is defined in `.github/workflows/docker-security-scan.yml`.

### 5.3 Dependency Updates

A workflow for keeping dependencies up-to-date is defined in `.github/workflows/dependency-updates.yml`.

### 5.4 Automated Testing

A comprehensive testing workflow is defined in `.github/workflows/automated-testing.yml`.

## 6. Using the CI/CD Pipeline

### 6.1 Triggering the Pipeline

The pipeline can be triggered in several ways:

- **Push to main**: Triggers build, test, Docker build, and Helm package
- **Create a tag**: Triggers full pipeline including deployment and release
- **Pull request**: Triggers build and test
- **Manual trigger**: Allows selecting the deployment environment

### 6.2 Creating a Release

To create a new release:

1. Use the provided script:
   ```bash
   # On Linux/macOS
   ./scripts/create-release.sh 1.0.0
   
   # On Windows
   scripts\create-release.bat 1.0.0
   ```

2. Or manually:
   - Update version in `pom.xml` and `Chart.yaml`
   - Commit changes
   - Create and push a tag: `git tag -a v1.0.0 -m "Release v1.0.0" && git push origin v1.0.0`

### 6.3 Deploying to Kubernetes

To deploy the operator to a Kubernetes cluster:

1. Use the provided script:
   ```bash
   # On Linux/macOS
   ./scripts/deploy-operator.sh --tag 1.0.0 --env prod
   
   # On Windows
   scripts\deploy-operator.bat --tag 1.0.0 --env prod
   ```

2. Or manually trigger the workflow in GitHub Actions:
   - Go to Actions → CI/CD Pipeline → Run workflow
   - Select the environment (dev, staging, prod)
   - Run the workflow

## 7. Monitoring and Troubleshooting

### 7.1 Monitoring Workflows

1. Go to your repository → Actions
2. Select the workflow you want to monitor
3. View the logs for each job and step

### 7.2 Troubleshooting Common Issues

#### Build Failures
- Check Maven build logs for compilation errors
- Verify test failures in the test results artifact

#### Docker Build Issues
- Ensure Dockerfile is correctly configured
- Check for base image compatibility issues

#### Deployment Failures
- Verify Kubernetes cluster access
- Check Helm chart values for errors
- Examine Kubernetes events for the deployment

## 8. Next Steps

- Set up monitoring for the operator using Prometheus and Grafana
- Implement canary deployments
- Add integration with other CI/CD tools
- Enhance security scanning with additional tools
- Implement GitOps workflow with ArgoCD or Flux
