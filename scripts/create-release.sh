#!/bin/bash
# Script to create a new release of the Microservice Bootstrap Operator

set -e

# Default values
VERSION=""
SKIP_TESTS=false
PUSH_IMAGES=true
DRY_RUN=false

# Display help
function show_help {
  echo "Usage: $0 [options] <version>"
  echo ""
  echo "Arguments:"
  echo "  version                       Version to release (e.g., 1.0.0)"
  echo ""
  echo "Options:"
  echo "  -s, --skip-tests              Skip running tests"
  echo "  -n, --no-push                 Don't push Docker images"
  echo "  -d, --dry-run                 Perform a dry run (no git tags or pushes)"
  echo "  -h, --help                    Show this help message"
  exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    -s|--skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    -n|--no-push)
      PUSH_IMAGES=false
      shift
      ;;
    -d|--dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      show_help
      ;;
    *)
      if [[ -z "$VERSION" ]]; then
        VERSION="$1"
        shift
      else
        echo "Unknown option: $1"
        show_help
      fi
      ;;
  esac
done

# Validate version
if [[ -z "$VERSION" ]]; then
  echo "Error: Version is required"
  show_help
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Error: Version must be in format x.y.z (e.g., 1.0.0)"
  exit 1
fi

# Check if git is installed
if ! command -v git &> /dev/null; then
  echo "Error: git is not installed"
  exit 1
fi

# Check if we're in a git repository
if ! git rev-parse --is-inside-work-tree &> /dev/null; then
  echo "Error: Not in a git repository"
  exit 1
fi

# Check for uncommitted changes
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: There are uncommitted changes in the repository"
  echo "Please commit or stash your changes before creating a release"
  exit 1
fi

# Check if the tag already exists
if git rev-parse "v$VERSION" &> /dev/null; then
  echo "Error: Tag v$VERSION already exists"
  exit 1
fi

echo "Creating release v$VERSION..."
echo "Dry run: $DRY_RUN"
echo "Skip tests: $SKIP_TESTS"
echo "Push images: $PUSH_IMAGES"
echo ""

# Update version in pom.xml
echo "Updating version in pom.xml..."
cd ../Java
mvn versions:set -DnewVersion=$VERSION -DgenerateBackupPoms=false

# Update version in Chart.yaml
echo "Updating version in Chart.yaml..."
cd ../helm/microservice-bootstrap-operator
sed -i "s/^version: .*/version: $VERSION/" Chart.yaml
sed -i "s/^appVersion: .*/appVersion: \"$VERSION\"/" Chart.yaml
cd ../../scripts

# Run tests if not skipped
if [[ "$SKIP_TESTS" == "false" ]]; then
  echo "Running tests..."
  cd ../Java
  mvn clean test
  cd ../scripts
fi

# Build the project
echo "Building the project..."
cd ../Java
mvn clean package -DskipTests
cd ../scripts

# Commit version changes
echo "Committing version changes..."
if [[ "$DRY_RUN" == "false" ]]; then
  git add ../Java/pom.xml ../helm/microservice-bootstrap-operator/Chart.yaml
  git commit -m "chore: prepare release v$VERSION"
fi

# Create tag
echo "Creating tag v$VERSION..."
if [[ "$DRY_RUN" == "false" ]]; then
  git tag -a "v$VERSION" -m "Release v$VERSION"
fi

# Build and push Docker image if requested
if [[ "$PUSH_IMAGES" == "true" ]]; then
  echo "Building Docker image..."
  cd ../Java
  docker build -t kannann1/microservice-bootstrap-operator:$VERSION .
  docker tag kannann1/microservice-bootstrap-operator:$VERSION kannann1/microservice-bootstrap-operator:latest
  
  if [[ "$DRY_RUN" == "false" ]]; then
    echo "Pushing Docker image..."
    docker push kannann1/microservice-bootstrap-operator:$VERSION
    docker push kannann1/microservice-bootstrap-operator:latest
  fi
  cd ../scripts
fi

# Push changes and tag if not dry run
if [[ "$DRY_RUN" == "false" ]]; then
  echo "Pushing changes and tag..."
  git push origin main
  git push origin "v$VERSION"
  
  echo "Release v$VERSION created and pushed successfully!"
  echo ""
  echo "The CI/CD pipeline will now:"
  echo "1. Build and test the project"
  echo "2. Build and push the Docker image"
  echo "3. Package and publish the Helm chart"
  echo "4. Create a GitHub release"
else
  echo "Dry run completed. No changes were pushed."
fi

echo ""
echo "To deploy this release to a Kubernetes cluster, run:"
echo "./deploy-operator.sh --tag $VERSION --env prod"
