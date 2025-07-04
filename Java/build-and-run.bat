@echo off
echo Building and running the Microservice Operator (Java)...

echo Building with Maven...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Maven build failed!
    exit /b %ERRORLEVEL%
)

echo Starting the operator...
java -jar target/microservice-bootstrap-operator-1.0-SNAPSHOT.jar
