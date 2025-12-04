# Docker & Kubernetes Basics

**A comprehensive guide to containerization and orchestration for the cross-border payment system**

---

## Table of Contents

1. [Docker Fundamentals](#docker-fundamentals)
2. [Building Production Docker Images](#building-production-docker-images)
3. [Docker Compose for Local Development](#docker-compose-for-local-development)
4. [Kubernetes Fundamentals](#kubernetes-fundamentals)
5. [Deploying to Kubernetes](#deploying-to-kubernetes)
6. [Helm Charts](#helm-charts)
7. [Production Best Practices](#production-best-practices)

---

## Docker Fundamentals

### What is Docker?

Docker is a platform for developing, shipping, and running applications in **containers**

- lightweight, standalone, executable packages that include everything needed to run an application.

**Key Benefits:**

- **Consistency**: "Works on my machine" → "Works everywhere"
- **Isolation**: Each container runs independently
- **Portability**: Run anywhere (dev laptop, production cluster)
- **Efficiency**: Share OS kernel, faster than VMs

### Container vs Virtual Machine

```text
Virtual Machines:
┌────────────────┐  ┌────────────────┐
│   App A        │  │   App B        │
│   Bins/Libs    │  │   Bins/Libs    │
│   Guest OS     │  │   Guest OS     │
└────────────────┘  └────────────────┘
        │                    │
┌───────────────────────────────────┐
│        Hypervisor                 │
│        Host OS                    │
│        Infrastructure             │
└───────────────────────────────────┘

Containers:
┌────────────────┐  ┌────────────────┐
│   App A        │  │   App B        │
│   Bins/Libs    │  │   Bins/Libs    │
└────────────────┘  └────────────────┘
        │                    │
┌───────────────────────────────────┐
│     Docker Engine                 │
│     Host OS                       │
│     Infrastructure                │
└───────────────────────────────────┘

VMs:    Heavy (GBs), slow boot (minutes)
Containers: Light (MBs), fast boot (seconds)
```

### Docker Core Concepts

**1. Image**: Read-only template (blueprint)

```bash
# List images
docker images

# Pull an image from Docker Hub
docker pull postgres:15
```

**2. Container**: Running instance of an image

```bash
# Run a container
docker run -d -p 8080:8080 --name my-app my-image:tag

# List running containers
docker ps

# Stop a container
docker stop my-app
```

**3. Dockerfile**: Recipe to build an image

```dockerfile
FROM openjdk:17
COPY target/app.jar /app/app.jar
CMD ["java", "-jar", "/app/app.jar"]
```

**4. Registry**: Storage for images (Docker Hub, ECR, etc.)

```bash
# Push to registry
docker push myregistry/my-app:v1.0
```

### Basic Docker Commands

```bash
# Build an image
docker build -t my-app:latest .

# Run container (interactive)
docker run -it my-app:latest /bin/bash

# View logs
docker logs my-app

# Execute command in running container
docker exec -it my-app bash

# Remove container
docker rm my-app

# Remove image
docker rmi my-app:latest

# Clean up unused resources
docker system prune -a
```

---

## Building Production Docker Images

### Multi-Stage Build (Best Practice)

For Java/Spring Boot applications, use multi-stage builds to minimize image size:

```dockerfile
# ═══════════════════════════════════════════════════════════
# Stage 1: Build the application
# ═══════════════════════════════════════════════════════════
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

# Copy Maven files first (for better layer caching)
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src src

# Build application (skip tests for faster builds)
RUN ./mvnw clean package -DskipTests

# ═══════════════════════════════════════════════════════════
# Stage 2: Create runtime image
# ═══════════════════════════════════════════════════════════
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy only the JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
```

**Why Multi-Stage?**

- Build stage: ~800MB (includes Maven, JDK)
- Runtime stage: ~250MB (only JRE + JAR)
- 70% size reduction!

### Layer Caching Strategy

Docker caches each instruction. Order matters for build speed:

```dockerfile
# ✅ GOOD: Dependencies change less frequently than code
COPY pom.xml .
RUN mvn dependency:go-offline    # Cached unless pom.xml changes
COPY src .                        # Cached unless source changes
RUN mvn package                   # Only re-runs if src changed

# ❌ BAD: Everything rebuilds when code changes
COPY . .                          # Copies everything
RUN mvn package                   # Rebuilds all dependencies
```

### Security Best Practices

**1. Use Official Base Images**

```dockerfile
# ✅ GOOD: Official, maintained
FROM eclipse-temurin:17-jre-jammy

# ❌ BAD: Unknown source
FROM random-person/java:17
```

**2. Run as Non-Root User**

```dockerfile
RUN adduser --disabled-password --gecos '' appuser
USER appuser
```

**3. Scan for Vulnerabilities**

```bash
# Using Docker Scout
docker scout cves my-app:latest

# Using Trivy
trivy image my-app:latest
```

**4. Use Minimal Base Images**

```dockerfile
# Smaller attack surface
FROM eclipse-temurin:17-jre-alpine  # ~150MB
# vs
FROM eclipse-temurin:17-jdk         # ~450MB
```

### Optimizing for Java Applications

```dockerfile
# JVM arguments for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport",      # Respect container memory
  "-XX:MaxRAMPercentage=75.0",     # Use 75% of container memory
  "-XX:+ExitOnOutOfMemoryError",   # Exit on OOM for restart
  "-Djava.security.egd=file:/dev/./urandom", # Faster startup
  "-jar", "app.jar"]
```

---

## Docker Compose for Local Development

### What is Docker Compose?

Tool to define and run multi-container applications using YAML.

**Our Payment System Docker Compose:**

```yaml
version: "3.8"

services:
  # ═══════════════════════════════════════════════════════════
  # PostgreSQL Database
  # ═══════════════════════════════════════════════════════════
  postgres:
    image: postgres:15-alpine
    container_name: crossborder-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infra/init-databases.sh:/docker-entrypoint-initdb.d/init.sh
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - payment-network

  # ═══════════════════════════════════════════════════════════
  # Kafka + Zookeeper
  # ═══════════════════════════════════════════════════════════
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - payment-network

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    healthcheck:
      test: kafka-broker-api-versions --bootstrap-server localhost:9092
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - payment-network

  # ═══════════════════════════════════════════════════════════
  # Application Services
  # ═══════════════════════════════════════════════════════════
  auth-service:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        SERVICE_NAME: auth-service
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_URL: jdbc:postgresql://postgres:5432/auth_db
      DB_USERNAME: postgres
      DB_PASSWORD: postgres
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    networks:
      - payment-network

  # ... (other services)

# ═══════════════════════════════════════════════════════════
# Volumes
# ═══════════════════════════════════════════════════════════
volumes:
  postgres-data:
  kafka-data:

# ═══════════════════════════════════════════════════════════
# Networks
# ═══════════════════════════════════════════════════════════
networks:
  payment-network:
    driver: bridge
```

### Docker Compose Commands

```bash
# Start all services
docker-compose up

# Start in background (detached)
docker-compose up -d

# Rebuild images before starting
docker-compose up --build

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v

# View logs
docker-compose logs -f auth-service

# Scale a service
docker-compose up -d --scale payment-service=3

# Execute command in service
docker-compose exec postgres psql -U postgres
```

### Health Checks and Dependencies

```yaml
auth-service:
  # ...
  depends_on:
    postgres:
      condition: service_healthy # Wait for postgres health check
    kafka:
      condition: service_healthy

  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
    interval: 30s # Check every 30s
    timeout: 10s # Timeout after 10s
    retries: 3 # Try 3 times
    start_period: 40s # Wait 40s before first check
```

---

## Kubernetes Fundamentals

### What is Kubernetes?

Kubernetes (K8s) is an **orchestration platform** for automating deployment, scaling, and management of containerized applications.

**Key Capabilities:**

- **Auto-scaling**: Scale pods based on CPU/memory
- **Self-healing**: Restart failed containers
- **Load balancing**: Distribute traffic
- **Rolling updates**: Zero-downtime deployments
- **Service discovery**: Find services automatically

### Kubernetes Architecture

```text
┌────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                      │
├────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Control Plane (Master)                   │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │  │
│  │  │ API Server   │  │  Scheduler   │  │  etcd      │ │  │
│  │  └──────────────┘  └──────────────┘  └────────────┘ │  │
│  │  ┌──────────────────────────────────────────────────┘ │  │
│  │  │ Controller Manager                                 │  │
│  └──│────────────────────────────────────────────────────┘  │
│     │                                                        │
│  ┌──┴──────────────────────────────────────────────────────┐│
│  │                    Worker Nodes                          ││
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────┐││
│  │  │ Node 1         │  │ Node 2         │  │ Node 3     │││
│  │  │  ┌──────────┐  │  │  ┌──────────┐  │  │  ┌──────┐ │││
│  │  │  │ Pod      │  │  │  │ Pod      │  │  │  │ Pod  │ │││
│  │  │  │ ┌──────┐ │  │  │  │ ┌──────┐ │  │  │  │┌────┐│ │││
│  │  │  │ │ App1 │ │  │  │  │ │ App2 │ │  │  │  ││App3││ │││
│  │  │  │ └──────┘ │  │  │  │ └──────┘ │  │  │  │└────┘│ │││
│  │  │  └──────────┘  │  │  └──────────┘  │  │  └──────┘ │││
│  │  │   kubelet      │  │   kubelet      │  │  kubelet   │││
│  │  │   kube-proxy   │  │   kube-proxy   │  │  kube-proxy│││
│  │  └────────────────┘  └────────────────┘  └────────────┘││
│  └──────────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────┘
```

**Components:**

- **API Server**: Frontend for K8s (all communication goes through it)
- **etcd**: Distributed key-value store (cluster state)
- **Scheduler**: Assigns pods to nodes
- **Controller Manager**: Runs controller processes
- **kubelet**: Agent on each node (runs pods)
- **kube-proxy**: Network proxy on each node

### Kubernetes

Objects

**1. Pod**: Smallest deployable unit (one or more containers)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: auth-service-pod
spec:
  containers:
    - name: auth-service
      image: my-registry/auth-service:v1.0
      ports:
        - containerPort: 8080
```

**2. Deployment**: Manages replica sets and rolling updates

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
spec:
  replicas: 3 # Run 3 pods
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
        - name: auth-service
          image: my-registry/auth-service:v1.0
          ports:
            - containerPort: 8080
```

**3. Service**: Exposes pods to network

```yaml
apiVersion: v1
kind: Service
metadata:
  name: auth-service
spec:
  selector:
    app: auth-service
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP # Internal only
```

**4. ConfigMap**: Configuration data

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  LOG_LEVEL: "INFO"
  MAX_CONNECTIONS: "100"
```

**5. Secret**: Sensitive data (base64 encoded)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  username: cG9zdGdyZXM= # postgres (base64)
  password: cGFzc3dvcmQ= # password (base64)
```

### kubectl Commands

```bash
# View cluster info
kubectl cluster-info

# List nodes
kubectl get nodes

# List all pods
kubectl get pods

# List pods in namespace
kubectl get pods -n production

# Describe a pod
kubectl describe pod auth-service-xxx

# View logs
kubectl logs auth-service-xxx

# Follow logs
kubectl logs -f auth-service-xxx

# Execute command in pod
kubectl exec -it auth-service-xxx -- /bin/bash

# Apply configuration
kubectl apply -f deployment.yaml

# Delete resources
kubectl delete -f deployment.yaml

# Port forward (for testing)
kubectl port-forward pod/auth-service-xxx 8080:8080

# Scale deployment
kubectl scale deployment auth-service --replicas=5

# Check deployment status
kubectl rollout status deployment/auth-service

# Rollback deployment
kubectl rollout undo deployment/auth-service
```

---

## Deploying to Kubernetes

### Complete Deployment Example

**1. Deployment (payment-service-deployment.yaml)**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: production
  labels:
    app: payment-service
    version: v1.0.0
spec:
  replicas: 3
  selector:
    matchLabels:
      app: payment-service

  # Rolling update strategy
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1 # Create 1 extra pod during update
      maxUnavailable: 0 # Don't kill any pod until new one is ready

  template:
    metadata:
      labels:
        app: payment-service
        version: v1.0.0
    spec:
      # Security context
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 2000

      containers:
        - name: payment-service
          image: 123456789012.dkr.ecr.us-east-1.amazonaws.com/payment-service:v1.0.0

          ports:
            - name: http
              containerPort: 8080
              protocol: TCP

          # Environment variables
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "production"

            - name: DB_URL
              valueFrom:
                configMapKeyRef:
                  name: app-config
                  key: database_url

            - name: DB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: username

            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: password

            - name: KAFKA_BOOTSTRAP_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: app-config
                  key: kafka_servers

          # Resource limits
          resources:
            requests:
              cpu: "500m" # 0.5 CPU
              memory: "512Mi" # 512MB RAM
            limits:
              cpu: "1" # 1 CPU
              memory: "1Gi" # 1GB RAM

          # Liveness probe (restart if fails)
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3

          # Readiness probe (don't send traffic if fails)
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3

          # Security settings
          securityContext:
            allowPrivilegeEscalation: false
            read OnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
```

**2. Service (payment-service-service.yaml)**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: payment-service
  namespace: production
spec:
  selector:
    app: payment-service
  ports:
    - name: http
      port: 80
      targetPort: 8080
  type: ClusterIP # Internal service
```

**3. Horizontal Pod Autoscaler (HPA)**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 3
  maxReplicas: 10

  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70 # Scale up if CPU > 70%

    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80 # Scale up if memory > 80%
```

**4. Ingress (External Access)**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: payment-api-ingress
  namespace: production
  annotations:
    kubernetes.io/ingress.class: "nginx"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
    - hosts:
        - api.payment-system.com
      secretName: payment-api-tls

  rules:
    - host: api.payment-system.com
      http:
        paths:
          - path: /v1/payments
            pathType: Prefix
            backend:
              service:
                name: payment-service
                port:
                  number: 80
```

### Deploy to Kubernetes

```bash
# Create namespace
kubectl create namespace production

# Apply all configs
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/ingress.yaml

# Verify deployment
kubectl get all -n production

# Watch rollout
kubectl rollout status deployment/payment-service -n production

# Check HPA
kubectl get hpa -n production

# View pod logs
kubectl logs -f deployment/payment-service -n production
```

---

## Helm Charts

### What is Helm?

**Helm** is the package manager for Kubernetes (like npm for Node.js, apt for Ubuntu).

**Benefits:**

- **Templating**: Reuse configs for dev/staging/prod
- **Versioning**: Track releases
- **Rollback**: Easy rollback to previous version
- **Dependency Management**: Manage dependencies between charts

### Helm Chart Structure

```
payment-service/
├── Chart.yaml          # Chart metadata
├── values.yaml         # Default configuration values
├── values-dev.yaml     # Dev overrides
├── values-prod.yaml    # Production overrides
├── templates/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── hpa.yaml
│   ├── ingress.yaml
│   └── _helpers.tpl   # Template helpers
└── charts/             # Dependencies
```

**Chart.yaml:**

```yaml
apiVersion: v2
name: payment-service
description: Payment Service for Cross-Border Payments
version: 1.0.0
appVersion: "1.0.0"
```

**values.yaml:**

```yaml
# Default values (can be overridden)
replicaCount: 3

image:
  repository: 123456789012.dkr.ecr.us-east-1.amazonaws.com/payment-service
  tag: "v1.0.0"
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 80
  targetPort: 8080

resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: 1
    memory: 1Gi

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

ingress:
  enabled: true
  host: api.payment-system.com
  tls:
    enabled: true

database:
  url: jdbc:postgresql://postgres:5432/payment_db
  username: postgres

kafka:
  bootstrapServers: kafka:9092
```

**templates/deployment.yaml:**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: { { include "payment-service.fullname" . } }
  labels: { { - include "payment-service.labels" . | nindent 4 } }
spec:
  replicas: { { .Values.replicaCount } }
  selector:
    matchLabels:
      { { - include "payment-service.selectorLabels" . | nindent 6 } }
  template:
    metadata:
      labels: { { - include "payment-service.selectorLabels" . | nindent 8 } }
    spec:
      containers:
        - name: { { .Chart.Name } }
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: { { .Values.image.pullPolicy } }
          ports:
            - name: http
              containerPort: { { .Values.service.targetPort } }
          env:
            - name: DB_URL
              value: { { .Values.database.url } }
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: { { .Values.kafka.bootstrapServers } }
          resources: { { - toYaml .Values.resources | nindent 12 } }
```

### Helm Commands

```bash
# Install chart
helm install payment-service ./payment-service -n production

# Install with custom values
helm install payment-service ./payment-service \
  -f values-prod.yaml \
  -n production

# Upgrade release
helm upgrade payment-service ./payment-service \
  -f values-prod.yaml \
  -n production

# Rollback to previous version
helm rollback payment-service -n production

# List releases
helm list -n production

# Uninstall
helm uninstall payment-service -n production

# Dry run (test without installing)
helm install payment-service ./payment-service --dry-run --debug

# Package chart
helm package payment-service

# Push to registry
helm push payment-service-1.0.0.tgz oci://myregistry.azurecr.io/helm
```

---

## Production Best Practices

### 1. Resource Management

**Always set resource requests and limits:**

```yaml
resources:
  requests:
    cpu: "500m" # Reserve at least 0.5 CPU
    memory: "512Mi" # Reserve at least 512MB
  limits:
    cpu: "2" # Don't use more than 2 CPU
    memory: "2Gi" # Don't use more than 2GB
```

**Why?**

- **Requests**: Scheduler uses this to place pods
- **Limits**: Prevents one app from consuming all resources
- **OOMKilled**: Pod killed if exceeds memory limit

### 2. Health Checks

**Liveness vs Readiness:**

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60 # Wait 60s before first check
  periodSeconds: 10 # Check every 10s
  failureThreshold: 3 # Restart after 3 failures

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  failureThreshold: 3 # Remove from service after 3 failures
```

**Difference:**

- **Liveness**: Should I restart this pod?
- **Readiness**: Should I send traffic to this pod?

### 3. Rolling Updates

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1 # Max 1 extra pod during update
    maxUnavailable: 0 # Always keep all pods running
```

**Zero-downtime deployment:**

1. Create new pod with new version
2. Wait for new pod to be ready
3. Start sending traffic to new pod
4. Terminate old pod
5. Repeat for all replicas

### 4. Secrets Management

**Never commit secrets to Git!**

```bash
# Create secret from file
kubectl create secret generic db-secret \
  --from-file=username=./db-username.txt \
  --from-file=password=./db-password.txt \
  -n production

# Or use external secret managers
# - AWS Secrets Manager
# - HashiCorp Vault
# - Azure Key Vault
```

**External Secrets Operator:**

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: db-secret
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: db-secret
  data:
    - secretKey: password
      remoteRef:
        key: prod/database/password
```

### 5. Monitoring and Logging

**Prometheus Scraping:**

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/actuator/prometheus"
```

**Centralized Logging:**

```yaml
# Use fluentd/fluent-bit to ship logs to:
# - Elasticsearch
# - CloudWatch Logs
# - Splunk
```

### 6. Network Policies

**Restrict pod-to-pod communication:**

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: payment-service-policy
spec:
  podSelector:
    matchLabels:
      app: payment-service
  policyTypes:
    - Ingress
    - Egress

  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: gateway
      ports:
        - protocol: TCP
          port: 8080

  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - protocol: TCP
          port: 5432
```

### 7. Pod Disruption Budget

**Ensure minimum availability during disruptions:**

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: payment-service-pdb
spec:
  minAvailable: 2 # Always keep at least 2 pods running
  selector:
    matchLabels:
      app: payment-service
```

---

## Summary

**Docker:**
✅ Containers provide consistency across environments  
✅ Multi-stage builds minimize image size  
✅ Run as non-root user for security  
✅ Use health checks for reliability

**Docker Compose:**
✅ Perfect for local development  
✅ Define multi-container apps in YAML  
✅ Health checks and dependencies

**Kubernetes:**
✅ Orchestrates containers at scale  
✅ Auto-scaling, self-healing, rolling updates  
✅ Deployments, Services, ConfigMaps, Secrets

**Helm:**
✅ Package manager for Kubernetes  
✅ Templating for different environments  
✅ Version control and rollbacks

**Production Checklist:**

- [ ] Resource requests/limits set
- [ ] Liveness and readiness probes configured
- [ ] Rolling update strategy defined
- [ ] Secrets in external secret manager
- [ ] Monitoring/logging configured
- [ ] Network policies applied
- [ ] Pod disruption budget set
- [ ] HPA configured for auto-scaling

**Next Steps:**

- Read [aws-deployment.md](aws-deployment.md) for cloud deployment
- Read [ci-cd.md](ci-cd.md) for automated pipelines
- Practice with local K8s (minikube, kind, Docker Desktop)
