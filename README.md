# Automated Assessment Platform for Programming Assignments

A comprehensive platform for automating the evaluation of programming assignments using GitLab CI/CD, Kubernetes, and modular test evaluators.

## Architecture Overview

```
+----------------------------------------------------------------------+
|                        Assessment Platform                           |
+----------------------------------------------------------------------+
|                                                                      |
|  +-----------+     +-------------+     +-----------+                 |
|  |  Frontend |---->|   Backend   |---->| GitLab CE |                 |
|  |  (React)  |     |(Spring Boot)|     |  (Source)  |                |
|  +-----------+     +------+------+     +-----+------+               |
|                           |                  |                       |
|                           |                  v                       |
|                           |          +-----------+                   |
|                           |          |  GitLab   |                   |
|                           |          |  Runner   |                   |
|                           |          |(Kubernetes)|                  |
|                           |          +-----+------+                  |
|                           |                |                         |
|              +------------+--------+-------+----------+              |
|              |            |        |                  |              |
|              v            v        v                  v              |
|        +---------+  +---------+  +---------+  +--------------+      |
|        |PostgreSQL|  |  Redis  |  |RabbitMQ |  |Module Gateway|      |
|        |(Database)|  | (Cache) |  | (Queue) |  |   (Nginx)    |      |
|        +---------+  +---------+  +---------+  +------+-------+      |
|                                                      |               |
|                              +----------+------------+----------+    |
|                              |          |            |          |    |
|                              v          v            v          v    |
|                         +---------+ +--------+ +---------+ +-----+  |
|                         |Correct- | |Perform-| |Code     | |AI   |  |
|                         |ness     | |ance    | |Quality  | |Plag.|  |
|                         |Evaluator| |Analyzer| |Evaluator| |Check|  |
|                         +---------+ +--------+ +---------+ +-----+  |
|                                                                      |
+----------------------------------------------------------------------+
```

## Features

- **Role-Based Access Control**: Admin, Teacher, and Student roles with granular permissions
- **SSO Integration**: Support for LDAP, SAML, and OAuth2 authentication
- **Dynamic CI/CD Generation**: Automatically generates `.gitlab-ci.yml` based on assignment configuration
- **Module Gateway (Nginx)**: Centralized reverse proxy routing to all evaluation module services
- **Modular Test Evaluators** (external HTTP services):
  - Correctness Evaluator: Validates code output against test cases
  - Performance Analyzer: Estimates time/space complexity
  - Code Quality: Checks coding standards and best practices
  - AI Plagiarism Checker: Detects AI-generated code
  - Error Evaluator: Provides detailed error analysis and suggestions
- **Kubernetes Executor**: Scalable test execution with pod-per-job isolation
- **Weighted Grading**: Configurable weights for each test module
- **Real-time Pipeline Tracking**: Monitor submission status and results

## Project Structure

```
assessment-platform/
├── backend/                    # Spring Boot backend service
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/assessment/
│   │   │   │   ├── config/         # Configuration classes
│   │   │   │   ├── controller/     # REST controllers
│   │   │   │   ├── dto/            # Data transfer objects
│   │   │   │   ├── exception/      # Exception handling
│   │   │   │   ├── model/          # JPA entities
│   │   │   │   ├── repository/     # Data repositories
│   │   │   │   ├── security/       # Authentication/Authorization
│   │   │   │   └── service/        # Business logic
│   │   │   └── resources/
│   │   │       ├── db/migration/   # Flyway migrations
│   │   │       └── application.yml # Configuration
│   │   └── test/
│   ├── Dockerfile
│   └── pom.xml
│
├── nginx/                      # Module gateway configuration
│   └── nginx.conf.template     # Nginx dynamic routing template
│
├── helm/                       # Kubernetes Helm charts
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── backend/
│       ├── gitlab/
│       ├── gitlab-runner/
│       └── module-gateway/
│
└── docker-compose.yaml         # Local development setup
```

## Module Gateway Architecture

All test evaluation modules are external HTTP services running as containers within the Kubernetes cluster. The **Module Gateway** (Nginx) provides a single entry point for the CI pipeline to communicate with these services.

### How It Works

1. CI pipeline jobs use a lightweight `alpine:3.19` image with `curl`
2. Student code is packaged as a `.tar.gz` archive
3. The archive is `POST`ed to the module gateway at `/api/modules/{module-name}/evaluate`
4. Nginx resolves the module service name via Kubernetes DNS and proxies the request
5. The module service processes the code and returns a JSON result
6. The CI job saves the result as a pipeline artifact

### Module HTTP API Contract

Each module service must expose a `POST /evaluate` endpoint accepting multipart form data:

```
POST /evaluate
Content-Type: multipart/form-data

Fields:
  code   - (file) tar.gz archive of the student code directory
  config - (text) JSON object with module-specific configuration
           e.g. {"language_id": 71, "model": "xgboost"}

Response:
  Content-Type: application/json
  Body: Module-specific evaluation result
```

### Adding a New Module

1. Deploy your module service to the Kubernetes cluster in the same namespace
2. Ensure it exposes port 8080 with a `POST /evaluate` endpoint
3. Register it in the `test_modules` database table with its `service_url`
4. The gateway automatically routes to it by service name -- no Nginx config changes needed

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Kubernetes cluster (minikube, k3s, or cloud-managed)
- Helm 3.x
- Java 17+ (for local development)

### Local Development

1. **Start dependencies with Docker Compose:**
   ```bash
   docker compose up -d postgres redis rabbitmq module-gateway
   ```

2. **Run the backend:**
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

3. **Access the application:**
   - Backend API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Module Gateway: http://localhost:8090
   - GitLab: http://localhost:8929

### Kubernetes Deployment

1. **Add Helm dependencies:**
   ```bash
   cd helm
   helm dependency update
   ```

2. **Install the chart:**
   ```bash
   helm install assessment-platform . -n assessment --create-namespace \
     --set gitlab.rootPassword=your-secure-password \
     --set backend.sso.issuerUri=https://your-sso.example.com/realms/assessment
   ```

3. **Verify deployment:**
   ```bash
   kubectl get pods -n assessment
   ```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | localhost |
| `DB_PORT` | PostgreSQL port | 5432 |
| `DB_NAME` | Database name | assessment |
| `REDIS_HOST` | Redis host | localhost |
| `RABBITMQ_HOST` | RabbitMQ host | localhost |
| `GITLAB_URL` | GitLab CE URL | http://localhost:8929 |
| `SSO_ISSUER_URI` | SSO/OIDC issuer URI | - |
| `SSO_CLIENT_ID` | OAuth2 client ID | assessment-platform |
| `JWT_SECRET` | JWT signing key | - |
| `MODULE_GATEWAY_URL` | Nginx module gateway base URL | http://module-gateway |
| `CI_RUNNER_IMAGE` | Alpine image used by CI jobs | alpine:3.19 |

### Test Module Weights

Configure weights per assignment via the API:

```json
POST /api/v1/courses/{courseId}/assignments/{assignmentId}/test-modules
[
  {"testModuleId": "uuid", "weight": 40.0, "enabled": true},
  {"testModuleId": "uuid", "weight": 30.0, "enabled": true},
  {"testModuleId": "uuid", "weight": 20.0, "enabled": true},
  {"testModuleId": "uuid", "weight": 10.0, "enabled": true}
]
```

## CI/CD Pipeline Structure

The platform generates a diamond-pattern CI/CD pipeline. All evaluate jobs use
lightweight Alpine containers with `curl` to send student code to module
services through the Nginx gateway.

```yaml
stages:
  - compile      # Validate syntax and compilation
  - evaluate     # Parallel module calls via HTTP
  - error_handling # Only on compile failure
  - grade        # Aggregate results and POST to backend

          +-------------+
          |   Compile   |
          +------+------+
                 |
    +------------+------------+
    v                         v
 +--------------+    +--------------+
 | Correctness  |    | Performance  |
 | (curl POST)  |    | (curl POST)  |
 +------+-------+    +------+-------+
        |                   |
 +------v-------+    +------v-------+
 | Code Quality |    | AI Plagiarism|
 | (curl POST)  |    | (curl POST)  |
 +------+-------+    +------+-------+
        |                   |
    +---+-------------------+---+
    v                           v
         +-----------------+
         |     Grader      |
         |  (jq + curl)    |
         +-----------------+
```

## API Endpoints

### Authentication
- `POST /api/v1/auth/sso/callback` - SSO callback
- `POST /api/v1/auth/refresh` - Refresh token
- `POST /api/v1/auth/logout` - Logout
- `GET /api/v1/auth/me` - Current user

### Courses
- `GET /api/v1/courses` - List courses
- `POST /api/v1/courses` - Create course
- `POST /api/v1/courses/{id}/enroll` - Enroll student

### Assignments
- `GET /api/v1/courses/{courseId}/assignments` - List assignments
- `POST /api/v1/courses/{courseId}/assignments` - Create assignment
- `POST /api/v1/courses/{courseId}/assignments/{id}/publish` - Publish

### Submissions
- `POST /api/v1/courses/{courseId}/assignments/{assignmentId}/submissions` - Submit
- `GET /api/v1/courses/{courseId}/assignments/{assignmentId}/submissions/{id}/grade` - Get grade

### Webhooks
- `POST /api/v1/webhooks/pipeline` - GitLab pipeline events
- `POST /api/v1/webhooks/pipeline-complete` - CI pipeline grade results

## Security

- **Container Isolation**: All tests run in isolated Kubernetes pods
- **Network Policies**: CI job pods have restricted egress, only allowing module gateway access
- **Resource Limits**: CPU/memory limits enforced per job
- **Non-root Execution**: All containers run as non-root users
- **Secrets Management**: Sensitive data stored in Kubernetes secrets

## Development

### Running Tests

```bash
cd backend
./mvnw test
```

### Database Migrations

Migrations are managed by Flyway and run automatically on startup.

## License

MIT License
