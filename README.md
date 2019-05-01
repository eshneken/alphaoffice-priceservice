
# Alpha Office PriceService:  Simple Example of Helidon SE service

This example implements a simple price REST service.

## Prerequisites

1. Maven 3.5 or newer
2. Java SE 8 or newer
3. Docker 17 or newer to build and run docker images
4. Kubernetes minikube v0.24 or newer to deploy to Kubernetes (or access to a K8s 1.7.4 or newer cluster)
5. Kubectl 1.7.4 or newer to deploy to Kubernetes

Verify prerequisites
```
java --version
mvn --version
docker --version
minikube version
kubectl version --short
```

## Build

```
mvn package
```

## Start the application

```
java -jar target/priceservice.jar
```

## Exercise the application

```
curl -X GET http://localhost:8080/price
{"message":"PriceService:1.0"}

curl -X GET http://localhost:8080/price/1001
{"price":"16.00"}

```

## Build the Docker Image

```
docker build -t priceservice target
```

## Start the application with Docker

```
docker run --rm -p 8080:8080 priceservice:latest
```

Exercise the application as described above

## Deploy the application to Kubernetes

```
kubectl cluster-info                # Verify which cluster
kubectl get pods                    # Verify connectivity to cluster
kubectl create -f target/app.yaml   # Deploy application
kubectl get service priceservice    # Get service info
```
