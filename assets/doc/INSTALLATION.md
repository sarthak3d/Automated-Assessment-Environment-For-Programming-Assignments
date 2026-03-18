# Installation Guide for Fresh Setup

This document provides in-depth instructions for setting up the **Automated Assessment Environment For Programming Assignments** from scratch on your local machine. 

The deployment leverages Kubernetes, Helm, Minikube, and Docker to orchestrate the backend services, a completely isolated GitLab instance, GitLab Runners, PostgreSQL, Redis, and RabbitMQ.

---

## 1. Prerequisites

Before beginning the installation, ensure the following tools are installed and accessible in your system's `PATH`.

*   **Docker**: For building images and running the Minikube hypervisor. ([Install Guide](https://docs.docker.com/get-docker/))
*   **Minikube**: Local Kubernetes cluster. ([Install Guide](https://minikube.sigs.k8s.io/docs/start/))
*   **Kubectl**: Kubernetes command-line tool. ([Install Guide](https://kubernetes.io/docs/tasks/tools/))
*   **Helm**: Kubernetes package manager. ([Install Guide](https://helm.sh/docs/intro/install/))
*   **Java 17+**: Required for building the Spring Boot backend. ([Install Guide](https://adoptium.net/))
*   **Maven**: Dependency management and build tool. ([Install Guide](https://maven.apache.org/install.html))

You can verify your installations by running:
```bash
docker --version
minikube version
kubectl version --client
helm version
java -version
mvn --version
```

---

## 2. Setting Up the Environment

### Resource Requirements
Because the platform spins up a fully-fledged GitLab instance, GitLab Runners, and numerous microservices internally, Minikube must be allocated sufficient resources:
- **CPU**: 6 Cores Minimum
- **Memory**: 16 GB (16384 MB) Minimum
- **Disk**: 50 GB Minimum

### The Automated Deployment Script
The easiest and recommended way to execute a fresh setup is by using the provided `deploy-k8s.sh` shell script, which takes care of starting Minikube, building the Java backend application, constructing Docker images inside the Minikube context, and pushing the Helm charts to the cluster.

> [!TIP]
> If you are on Windows, you must run this from a Bash emulator such as Git Bash or WSL.

```bash
# Give execution permissions to the script
chmod +x deploy-k8s.sh

# Run the automated deployment
./deploy-k8s.sh deploy
```

---

## 3. Step-by-Step Manual Installation

If you prefer to understand the internal mechanisms or deploy the platform manually step-by-step, follow the sequence below.

### Step 3.1: Start and Configure Minikube

Start Minikube with the required resources. Depending on your Operating System, select the correct driver (e.g., `docker`, `hyperkit`, `hyperv`, or `kvm2`).

```bash
minikube start \
    --driver=docker \
    --cpus=6 \
    --memory=16384 \
    --disk-size=50g \
    --addons=ingress,metrics-server,dashboard \
    --kubernetes-version=stable

# Configure kubectl to use the minikube context
kubectl config use-context minikube
```

### Step 3.2: Build the Backend

Build the backend JAR payload using Maven.

```bash
cd backend
mvn clean package -DskipTests -q
cd ..
```

### Step 3.3: Build Docker Image in Minikube's Context

To allow Kubernetes to access your local Docker image without needing a remote registry, switch your Docker environment variables to point to Minikube's internal Docker daemon:

```bash
# Point shell to minikube's docker daemon
eval $(minikube docker-env)

# Build the backend image
docker build -t assessment-platform/backend:1.0.0 -f backend/Dockerfile backend/
```

### Step 3.4: Setup Namespaces

Create the required namespaces in Kubernetes. Our charts enforce segmentation between the platform workload and discrete assessment jobs.

```bash
kubectl create namespace assessment-platform
kubectl create namespace assessment-jobs

# (Optional) label namespaces for Helm
kubectl label namespace assessment-platform app.kubernetes.io/managed-by=Helm
kubectl label namespace assessment-jobs app.kubernetes.io/managed-by=Helm
```

### Step 3.5: Deploy Using Helm

Update the Helm dependencies and perform the deployment. The Helm chart provisions PostgreSQL, Redis, RabbitMQ, the Java backend, the Module Gateway, a local GitLab instance, and GitLab runner configurations.

```bash
cd helm
helm dependency update .
cd ..

helm upgrade --install assessment ./helm \
    --namespace assessment-platform \
    --set gitlab.rootPassword="Admin1234!" \
    --set backend.sso.enabled=false \
    --set backend.image.pullPolicy=Never \
    --wait --timeout=15m
```
*(Review `deploy-k8s.sh` for an exhaustive list of resource limits and parameter overrides you can adjust using `--set`).*

---

## 4. Verification and Access

Wait for all pods to reach a `Running` state:
```bash
kubectl get pods -n assessment-platform -w
```

Once the platform deploys successfully, you can port-forward the services to expose them to your local `localhost` network:

```bash
# Forward Backend API
kubectl port-forward svc/assessment-assessment-platform-backend 8080:8080 -n assessment-platform
```

### Test Accounts Available
With SSO disabled, the backend provisions three roles for testing purposes via standard `/api/v1/auth/login`:
*   `admin` / `admin123`
*   `teacher` / `teacher123`
*   `student` / `student123`

---

## 5. Teardown & Troubleshooting

To completely remove the installation and free up resources:

```bash
# Using the deployment script
./deploy-k8s.sh teardown
```

If pods are crashing, you can inspect their logs via:
```bash
kubectl get pods -n assessment-platform
kubectl logs -n assessment-platform <pod-name>
```
