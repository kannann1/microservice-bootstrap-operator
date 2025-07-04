# Development Guide

This guide provides instructions for setting up a development environment and contributing to the Microservice Bootstrap Operator.

## Prerequisites

- JDK 17 or later
- Maven 3.6 or later
- Docker
- Kubernetes cluster (minikube, kind, or a remote cluster)
- kubectl configured to communicate with your cluster
- Git

## Setting Up the Development Environment

1. Clone the repository:

```bash
git clone https://github.com/kannann1/microservice-bootstrap-operator.git
cd microservice-bootstrap-operator
```

2. Build the project:

```bash
cd Java
mvn clean install
```

3. Run the operator locally (outside the cluster):

```bash
mvn spring-boot:run
```

This will start the operator with the default configuration, connecting to the Kubernetes cluster configured in your current kubectl context.

## Project Structure

```
Java/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/
│   │   │       └── github/
│   │   │           └── kannann1/
│   │   │               └── microservicebootstrapoperator/
│   │   │                   ├── config/           # Spring Boot configuration
│   │   │                   ├── controller/       # Kubernetes controllers
│   │   │                   ├── model/            # CRD model classes
│   │   │                   ├── service/          # Business logic services
│   │   │                   └── util/             # Utility classes
│   │   └── resources/
│   │       └── application.properties            # Spring Boot configuration
│   └── test/
│       └── java/
│           └── io/
│               └── github/
│                   └── kannann1/
│                       └── microservicebootstrapoperator/
│                           ├── controller/       # Controller tests
│                           └── service/          # Service tests
├── k8s/                                          # Kubernetes manifests
│   ├── crd/                                      # Custom Resource Definitions
│   ├── deploy/                                   # Deployment manifests
│   └── samples/                                  # Sample resources
└── pom.xml                                       # Maven configuration
```

## Building and Testing

### Building the Project

```bash
mvn clean package
```

This will compile the code, run the tests, and create a JAR file in the `target` directory.

### Running Tests

```bash
mvn test
```

To run a specific test:

```bash
mvn test -Dtest=SidecarInjectionServiceTest
```

### Building the Docker Image

```bash
docker build -t microservice-bootstrap-operator:latest .
```

## Deploying for Development

### Apply the CRD

```bash
kubectl apply -f k8s/crd/microservice.github.io_appconfigs.yaml
```

### Deploy the Operator

```bash
kubectl apply -f k8s/deploy/operator.yaml
```

### Create a Sample AppConfig

```bash
kubectl apply -f k8s/samples/sample-appconfig.yaml
```

## Development Workflow

1. Make changes to the code
2. Run tests to verify your changes
3. Build and deploy the operator
4. Test with sample resources
5. Submit a pull request

## Debugging

### Remote Debugging

To enable remote debugging, add the following JVM arguments when starting the operator:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

Then connect your IDE to port 5005.

### Logging

The operator uses SLF4J with Logback for logging. You can configure the log level in `src/main/resources/logback.xml`.

To increase the log level for development:

```xml
<logger name="io.github.kannann1.microservicebootstrapoperator" level="DEBUG" />
```

### Viewing Operator Logs

```bash
kubectl logs -f deployment/microservice-bootstrap-operator-controller-manager -n microservice-bootstrap-operator-system
```

## Common Development Tasks

### Adding a New Feature

1. Define the feature in the AppConfig CRD
2. Update the model classes
3. Create or update the service class
4. Add the service to the controller
5. Write tests
6. Update documentation

### Modifying the CRD

1. Update the CRD YAML file
2. Update the model classes
3. Implement version conversion if needed
4. Update documentation

### Adding Dependencies

Add new dependencies to the `pom.xml` file:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Best Practices

- Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Write unit tests for all new code
- Use meaningful commit messages
- Document public APIs
- Handle errors gracefully
- Use proper Kubernetes owner references
- Make operations idempotent
- Use server-side apply when appropriate

## Troubleshooting

### Common Issues

#### CRD Not Registered

```
Error: no matches for kind "AppConfig" in version "microservice.github.io/v1"
```

Solution: Apply the CRD:

```bash
kubectl apply -f k8s/crd/microservice.github.io_appconfigs.yaml
```

#### Permission Issues

```
Error: failed to create resource: Forbidden
```

Solution: Check the operator's RBAC permissions:

```bash
kubectl describe clusterrole microservice-bootstrap-operator-manager-role
```

#### Connection Issues

```
Error: Unable to connect to the server: dial tcp: lookup kubernetes.default.svc: no such host
```

Solution: Check your kubeconfig and network connectivity.

## Getting Help

If you need help with development, you can:

- Open an issue on GitHub
- Join our community Slack channel
- Email the maintainers
