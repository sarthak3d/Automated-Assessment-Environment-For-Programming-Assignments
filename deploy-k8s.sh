#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="assessment-platform"
NAMESPACE="assessment-platform"
JOBS_NAMESPACE="assessment-jobs"
RELEASE_NAME="assessment"
HELM_CHART_DIR="${SCRIPT_DIR}/helm"
BACKEND_DIR="${SCRIPT_DIR}/backend"
BACKEND_IMAGE="assessment-platform/backend"
BACKEND_IMAGE_TAG="1.0.0"
MINIKUBE_CPUS="${MINIKUBE_CPUS:-6}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-16384}"
MINIKUBE_DISK="${MINIKUBE_DISK:-50g}"
MINIKUBE_DRIVER="${MINIKUBE_DRIVER:-}"
GITLAB_ROOT_PASSWORD="${GITLAB_ROOT_PASSWORD:-Admin1234!}"
GITLAB_ADMIN_TOKEN="${GITLAB_ADMIN_TOKEN:-}"
GITLAB_RUNNER_ENABLED="${GITLAB_RUNNER_ENABLED:-true}"
SSO_ISSUER_URI="${SSO_ISSUER_URI:-http://localhost:8080/realms/assessment}"

detect_os() {
    case "$(uname -s)" in
        Linux*)   OS="linux";;
        Darwin*)  OS="macos";;
        MINGW*|MSYS*|CYGWIN*) OS="windows";;
        *)        OS="unknown";;
    esac
    echo "${OS}"
}

log_info()  { echo -e "\033[0;34m[INFO]\033[0m  $*"; }
log_ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
log_warn()  { echo -e "\033[0;33m[WARN]\033[0m  $*"; }
log_error() { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

command_exists() { command -v "$1" &>/dev/null; }

check_prerequisites() {
    log_info "Checking prerequisites..."
    local missing=()

    for cmd in docker kubectl helm minikube java mvn; do
        if command_exists "${cmd}"; then
            log_ok "${cmd} found: $(${cmd} version --short 2>/dev/null || ${cmd} --version 2>/dev/null | head -1)"
        else
            missing+=("${cmd}")
        fi
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing[*]}"
        log_info "Install instructions:"
        for tool in "${missing[@]}"; do
            case "${tool}" in
                docker)   echo "  docker    -> https://docs.docker.com/get-docker/";;
                kubectl)  echo "  kubectl   -> https://kubernetes.io/docs/tasks/tools/";;
                helm)     echo "  helm      -> https://helm.sh/docs/intro/install/";;
                minikube) echo "  minikube  -> https://minikube.sigs.k8s.io/docs/start/";;
                java)     echo "  java 17+  -> https://adoptium.net/";;
                mvn)      echo "  mvn       -> https://maven.apache.org/install.html";;
            esac
        done
        exit 1
    fi

    log_ok "All prerequisites satisfied"
}

select_minikube_driver() {
    if [[ -n "${MINIKUBE_DRIVER}" ]]; then
        echo "${MINIKUBE_DRIVER}"
        return
    fi

    local os
    os="$(detect_os)"

    case "${os}" in
        linux)
            if command_exists docker; then
                echo "docker"
            elif command_exists kvm2; then
                echo "kvm2"
            else
                echo "docker"
            fi
            ;;
        macos)
            if command_exists hyperkit; then
                echo "hyperkit"
            else
                echo "docker"
            fi
            ;;
        windows)
            if command_exists "C:/Windows/System32/wsl.exe" 2>/dev/null || command_exists wsl; then
                echo "docker"
            else
                echo "hyperv"
            fi
            ;;
        *)
            echo "docker"
            ;;
    esac
}

start_minikube() {
    local driver
    driver="$(select_minikube_driver)"

    if minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
        log_ok "Minikube is already running"
    else
        log_info "Starting minikube (driver=${driver}, cpus=${MINIKUBE_CPUS}, memory=${MINIKUBE_MEMORY}MB, disk=${MINIKUBE_DISK})..."
        minikube start \
            --driver="${driver}" \
            --cpus="${MINIKUBE_CPUS}" \
            --memory="${MINIKUBE_MEMORY}" \
            --disk-size="${MINIKUBE_DISK}" \
            --addons=ingress,metrics-server,dashboard \
            --kubernetes-version=stable
        log_ok "Minikube started"
    fi

    log_info "Configuring kubectl context to minikube..."
    kubectl config use-context minikube
}

build_backend() {
    log_info "Building backend JAR with Maven..."
    pushd "${BACKEND_DIR}" > /dev/null

    local mvn_cmd="mvn"
    if [[ -f "./mvnw" ]]; then
        chmod +x ./mvnw
        mvn_cmd="./mvnw"
    fi

    ${mvn_cmd} clean package -DskipTests -q
    log_ok "Backend JAR built successfully"

    popd > /dev/null
}

build_docker_image() {
    log_info "Configuring Docker environment to use minikube's daemon..."
    eval "$(minikube docker-env --shell bash)"

    log_info "Building backend Docker image: ${BACKEND_IMAGE}:${BACKEND_IMAGE_TAG}"
    docker build \
        -t "${BACKEND_IMAGE}:${BACKEND_IMAGE_TAG}" \
        -f "${BACKEND_DIR}/Dockerfile" \
        "${BACKEND_DIR}"
    log_ok "Docker image built: ${BACKEND_IMAGE}:${BACKEND_IMAGE_TAG}"
}

setup_namespaces() {
    log_info "Ensuring namespaces exist..."

    for ns in "${NAMESPACE}" "${JOBS_NAMESPACE}"; do
        if kubectl get namespace "${ns}" &>/dev/null; then
            log_ok "Namespace '${ns}' already exists"
        else
            kubectl create namespace "${ns}"
            log_ok "Namespace '${ns}' created"
        fi
        kubectl label namespace "${ns}" app.kubernetes.io/managed-by=Helm --overwrite &>/dev/null
        kubectl annotate namespace "${ns}" \
            meta.helm.sh/release-name="${RELEASE_NAME}" \
            meta.helm.sh/release-namespace="${NAMESPACE}" \
            --overwrite &>/dev/null
    done
}

update_helm_dependencies() {
    log_info "Updating Helm chart dependencies..."
    pushd "${HELM_CHART_DIR}" > /dev/null

    helm dependency update .
    log_ok "Helm dependencies updated"

    popd > /dev/null
}

deploy_helm() {
    log_info "Deploying ${PROJECT_NAME} via Helm..."

    helm upgrade --install "${RELEASE_NAME}" "${HELM_CHART_DIR}" \
        --namespace "${NAMESPACE}" \
        --create-namespace \
        --set gitlab.rootPassword="${GITLAB_ROOT_PASSWORD}" \
        --set gitlab.adminToken="${GITLAB_ADMIN_TOKEN}" \
        --set backend.sso.issuerUri="${SSO_ISSUER_URI}" \
        --set backend.sso.enabled=false \
        --set 'backend.env.SPRING_PROFILES_ACTIVE=kubernetes\,development' \
        --set backend.image.repository="${BACKEND_IMAGE}" \
        --set backend.image.tag="${BACKEND_IMAGE_TAG}" \
        --set backend.image.pullPolicy=Never \
        --set global.storageClass=standard \
        --set backend.replicaCount=1 \
        --set backend.resources.requests.cpu=250m \
        --set backend.resources.requests.memory=512Mi \
        --set backend.resources.limits.cpu=1 \
        --set backend.resources.limits.memory=1Gi \
        --set moduleGateway.replicaCount=1 \
        --set moduleGateway.resources.requests.cpu=50m \
        --set moduleGateway.resources.requests.memory=64Mi \
        --set gitlab.enabled=true \
        --set gitlab.resources.requests.cpu=1500m \
        --set gitlab.resources.requests.memory=3Gi \
        --set gitlab.resources.limits.cpu=3 \
        --set gitlab.resources.limits.memory=4Gi \
        --set gitlabRunner.enabled="${GITLAB_RUNNER_ENABLED}" \
        --set gitlabRunner.replicaCount=1 \
        --set gitlabRunner.concurrentJobs=3 \
        --set postgresql.auth.password=assessmentDev123 \
        --set postgresql.primary.resources.requests.cpu=100m \
        --set postgresql.primary.resources.requests.memory=128Mi \
        --set redis.architecture=standalone \
        --set redis.auth.password=redisDevPass123 \
        --set redis.master.resources.requests.cpu=50m \
        --set redis.master.resources.requests.memory=64Mi \
        --set rabbitmq.enabled=true \
        --set rabbitmq.image.repository=bitnamilegacy/rabbitmq \
        --set rabbitmq.image.tag=4.1.2-debian-12-r1 \
        --set rabbitmq.volumePermissions.image.repository=bitnamilegacy/os-shell \
        --set rabbitmq.volumePermissions.image.tag=12-debian-12-r48 \
        --set rabbitmq.auth.username=assessment \
        --set rabbitmq.auth.password=rabbitDevPass123 \
        --set rabbitmq.resources.requests.cpu=100m \
        --set rabbitmq.resources.requests.memory=128Mi \
        --set rabbitmq.resources.limits.cpu=500m \
        --set rabbitmq.resources.limits.memory=512Mi \
        --timeout 15m \
        --wait

    log_ok "Helm release '${RELEASE_NAME}' deployed to namespace '${NAMESPACE}'"
}

wait_for_pods() {
    log_info "Waiting for all pods in '${NAMESPACE}' to become ready (timeout: 10m)..."

    local deadline=$((SECONDS + 600))
    while true; do
        local not_ready
        not_ready=$(kubectl get pods -n "${NAMESPACE}" --no-headers 2>/dev/null \
            | grep -cvE "Running|Completed" || true)

        if [[ "${not_ready}" -eq 0 ]]; then
            break
        fi

        if [[ $SECONDS -ge $deadline ]]; then
            log_error "Timeout waiting for pods. Current status:"
            kubectl get pods -n "${NAMESPACE}" --no-headers
            exit 1
        fi

        log_info "  ${not_ready} pod(s) not ready yet, retrying in 15s..."
        sleep 15
    done

    log_ok "All pods are running"
    echo ""
    kubectl get pods -n "${NAMESPACE}" -o wide
}

print_access_info() {
    echo ""
    echo "============================================================"
    echo "  ${PROJECT_NAME} -- Deployment Complete"
    echo "============================================================"
    echo ""

    local backend_url
    backend_url="$(minikube service "${RELEASE_NAME}-${PROJECT_NAME}-backend" \
        -n "${NAMESPACE}" --url 2>/dev/null || true)"

    if [[ -z "${backend_url}" ]]; then
        backend_url="<run: minikube service ${RELEASE_NAME}-${PROJECT_NAME}-backend -n ${NAMESPACE} --url>"
    fi

    echo "  Backend API         : ${backend_url}"
    echo "  Minikube Dashboard  : minikube dashboard"
    echo "  Port-Forward Backend: kubectl port-forward svc/${RELEASE_NAME}-${PROJECT_NAME}-backend 8080:8080 -n ${NAMESPACE}"

}

show_status() {
    echo ""
    log_info "Cluster info:"
    kubectl cluster-info 2>/dev/null || log_warn "Cluster not reachable"
    echo ""

    log_info "Pods in namespace '${NAMESPACE}':"
    kubectl get pods -n "${NAMESPACE}" -o wide 2>/dev/null || log_warn "No pods found"
    echo ""

    log_info "Services in namespace '${NAMESPACE}':"
    kubectl get svc -n "${NAMESPACE}" 2>/dev/null || log_warn "No services found"
    echo ""

    log_info "Pods in namespace '${JOBS_NAMESPACE}':"
    kubectl get pods -n "${JOBS_NAMESPACE}" -o wide 2>/dev/null || log_warn "No pods found"
    echo ""
}

show_logs() {
    local component="${1:-backend}"
    local lines="${2:-100}"

    log_info "Fetching last ${lines} lines of logs for component '${component}'..."

    local pod
    pod=$(kubectl get pods -n "${NAMESPACE}" \
        -l "app.kubernetes.io/component=${component}" \
        -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

    if [[ -z "${pod}" ]]; then
        log_error "No pod found for component '${component}'"
        exit 1
    fi

    kubectl logs "${pod}" -n "${NAMESPACE}" --tail="${lines}"
}

teardown() {
    log_warn "This will destroy the entire ${PROJECT_NAME} deployment."
    read -rp "Continue? (y/N): " confirm
    if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
        log_info "Aborted."
        exit 0
    fi

    log_info "Uninstalling Helm release '${RELEASE_NAME}'..."
    helm uninstall "${RELEASE_NAME}" -n "${NAMESPACE}" 2>/dev/null || true
    log_ok "Helm release removed"

    log_info "Deleting namespaces..."
    kubectl delete namespace "${NAMESPACE}" --ignore-not-found
    kubectl delete namespace "${JOBS_NAMESPACE}" --ignore-not-found
    log_ok "Namespaces deleted"

    read -rp "Also stop minikube? (y/N): " stop_mk
    if [[ "${stop_mk}" == "y" || "${stop_mk}" == "Y" ]]; then
        minikube stop
        log_ok "Minikube stopped"
    fi
}

port_forward() {
    log_info "Starting port-forwarding for local access..."
    log_info "  Backend  -> localhost:8080"
    log_info "Press Ctrl+C to stop all port-forwards."

    kubectl port-forward "svc/${RELEASE_NAME}-${PROJECT_NAME}-backend" 8080:8080 -n "${NAMESPACE}" &
    local pf_backend=$!

    trap "kill ${pf_backend} 2>/dev/null; exit 0" INT TERM
    wait
}

usage() {
    cat <<EOF
Usage: $0 <command> [options]

Commands:
  deploy        Build and deploy the full platform to minikube (default)
  status        Show cluster and pod status
  logs          Show logs for a component
  port-forward  Forward service ports for local access
  teardown      Remove the deployment and clean up

Options:
  --skip-build      Skip Maven build and Docker image build
  --skip-minikube   Assume minikube is already running
  --component=NAME  Component name for 'logs' command (default: backend)
  --lines=N         Number of log lines to show (default: 100)

Environment Variables:
  MINIKUBE_CPUS           CPU cores for minikube  (default: 6)
  MINIKUBE_MEMORY         Memory in MB            (default: 16384)
  MINIKUBE_DISK           Disk size               (default: 50g)
  MINIKUBE_DRIVER         Minikube driver override (auto-detected)
  GITLAB_ROOT_PASSWORD    GitLab root password     (default: Admin1234!)
  GITLAB_ADMIN_TOKEN      GitLab PAT for API actions (default: empty)
  GITLAB_RUNNER_ENABLED   Enable GitLab Runner deployment (default: true)
  SSO_ISSUER_URI          SSO issuer URI           (default: http://localhost:8080/realms/assessment)

Examples:
  $0 deploy
  $0 deploy --skip-build
  $0 status
  $0 logs --component=gitlab --lines=200
  $0 port-forward
  $0 teardown
EOF
}

main() {
    local cmd="${1:-deploy}"
    shift || true

    local skip_build=false
    local skip_minikube=false
    local log_component="backend"
    local log_lines=100

    for arg in "$@"; do
        case "${arg}" in
            --skip-build)       skip_build=true;;
            --skip-minikube)    skip_minikube=true;;
            --component=*)     log_component="${arg#*=}";;
            --lines=*)         log_lines="${arg#*=}";;
            --help|-h)         usage; exit 0;;
            *)                 log_error "Unknown option: ${arg}"; usage; exit 1;;
        esac
    done

    case "${cmd}" in
        deploy)
            echo ""
            echo "============================================================"
            echo "  ${PROJECT_NAME} -- Kubernetes Deployment"
            echo "  OS: $(detect_os) | Date: $(date '+%Y-%m-%d %H:%M:%S')"
            echo "============================================================"
            echo ""

            check_prerequisites

            if [[ "${skip_minikube}" == false ]]; then
                start_minikube
            fi

            if [[ "${skip_build}" == false ]]; then
                build_backend
                build_docker_image
            fi

            setup_namespaces
            update_helm_dependencies
            deploy_helm
            wait_for_pods
            print_access_info
            ;;

        status)
            show_status
            ;;

        logs)
            show_logs "${log_component}" "${log_lines}"
            ;;

        port-forward)
            port_forward
            ;;

        teardown)
            teardown
            ;;

        help|--help|-h)
            usage
            ;;

        *)
            log_error "Unknown command: ${cmd}"
            usage
            exit 1
            ;;
    esac
}

main "$@"
