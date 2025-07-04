#!/bin/bash
echo "Building and running the Microservice Bootstrap Operator (Java)..."

echo "Building with Maven..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Maven build failed!"
    exit 1
fi

echo "Starting the operator..."
java -jar target/microservice-bootstrap-operator-1.0-SNAPSHOT.jar
