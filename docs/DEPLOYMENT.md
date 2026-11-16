# Deployment guide: minijava-br55-con-1612 on AWS EKS

minijava-br55-con-1612 is a plain Java 11 application (Maven, `com.test.MiniApp`, packaged as a jar without a `Main-Class`).
It does not start Spring Boot: `spring-boot-starter-web` is only declared in the pom. After start-up the application's own
work ends (it opens its server socket on `APP_PORT` for one second, then closes it, and `main` returns). The process keeps
running because the health endpoint's HTTP server (JDK `com.sun.net.httpserver`) stays up on `HEALTH_PORT`.

## 1. Prerequisites and system requirements

- Docker 20.10 or later (builds the image) and, for local runs, Docker Compose v2.
- AWS CLI v2, configured with `aws configure` or SSO.
- kubectl matching the cluster's minor version (plus or minus one).
- eksctl 0.150 or later (cluster creation).
- IAM permissions: `ecr:*` on the repository (GetAuthorizationToken, CreateRepository, DescribeRepositories, PutImage, InitiateLayerUpload, UploadLayerPart, CompleteLayerUpload, BatchCheckLayerAvailability), `eks:DescribeCluster` for `aws eks update-kubeconfig`, and a Kubernetes RBAC role in the cluster that may create namespaces, Secrets, Deployments, Services and Ingresses.
- A machine with 2 CPU and 4 GB RAM or more for the Maven build. Each pod requests 250m CPU and 512Mi memory (limits 500m and 1Gi).
- An EKS cluster with the AWS Load Balancer Controller installed (for the Ingress).
- The external services the application names (MySQL, Redis, the external API, the payment service, RabbitMQ) are not part of this deployment: the application reaches them through environment variables.

## 2. Local setup with Docker Compose

`docker-compose.yml` holds one service, the application. Credentials are taken from the shell that runs compose; none is written in the file.

```bash
export DB_PASSWORD=...  REDIS_PASSWORD=...  EXTERNAL_API_KEY=...  PAYMENT_SERVICE_PASSWORD=...
export JWT_SECRET=...  ADMIN_PASSWORD=...  ENCRYPTION_KEY=...  MONITORING_PASSWORD=...  RABBITMQ_PASSWORD=...
docker compose up --build
curl http://localhost:8081/health      # {"status":"UP"}
```

Volumes: `./config` is mounted read-only at `/app/config` (`CONFIG_PATH`, the application reads `app.properties` from it) and the named volume `app-logs` at `/app/logs` (`LOG_PATH`).
The application starts without a database: a failed MySQL connection is logged and the process continues.

## 3. Build and push

Run from the repository root.

```bash
./scripts/build-push.sh        # Linux and macOS
scripts\build-push.bat         # Windows
```

The script asks for the registry (1. AWS ECR, 2. Docker Hub), then the details of the chosen one, then `IMAGE_TAG` (empty means `latest`), builds with
`docker build -f Dockerfile -t <registry>/<repo>:<tag> .`, logs in and pushes. For ECR it logs in with `aws ecr get-login-password` and creates the repository when it is missing.
The last line shows the full image URI to give to the deploy script.

## 4. Deploy to AWS EKS

### 4.1 Cluster setup and kubectl configuration

```bash
eksctl create cluster --name my-cluster --region us-east-1 --nodes 2 --node-type t3.medium
aws eks update-kubeconfig --region us-east-1 --name my-cluster
kubectl cluster-info
```

Install the AWS Load Balancer Controller (needed by `ingress.yaml`) following the AWS documentation (IAM OIDC provider, IAM policy, Helm chart `aws-load-balancer-controller` in `kube-system`).

### 4.2 Run the deployment script

```bash
./scripts/deploy-image.sh      # or scripts\deploy-image.bat
```

It asks for the AWS region, the cluster name, the image URI, `REDIS_HOST` and `REDIS_PORT` (Enter skips), and the nine credentials (typed without echo with `read -s` in the .sh script, a hidden PowerShell prompt in the .bat).
It then configures kubectl, applies `namespace.yaml`, creates the Secret `minijava-br55-con-1612-secrets` with `kubectl create secret generic ... --dry-run=client -o yaml | kubectl apply -f -`,
fills the `{{...}}` placeholders of `deployment.yaml`, applies `deployment.yaml`, `service.yaml` and `ingress.yaml`, waits with `kubectl rollout status deployment/minijava-br55-con-1612 -n minijava-br55-con-1612`, and prints the pods, services, ingress and the load balancer address.
The .sh script restores `deployment.yaml` with its placeholders when it ends; the .bat script fills a temporary copy.

### 4.3 The manifests

- `kubernetes/namespace.yaml`: Namespace `minijava-br55-con-1612` with the label `name: minijava-br55-con-1612`.
- `kubernetes/deployment.yaml`: Deployment with 2 replicas, one container with `image: "{{IMAGE_URI}}"`, ports 8080 (application) and 8081 (health), plain environment variables (`APP_PORT`, `HEALTH_PORT`, `CONFIG_PATH`, `LOG_PATH`), `REDIS_HOST` and `REDIS_PORT` as `{{...}}` placeholders, the nine credentials from `secretKeyRef` of the Secret `minijava-br55-con-1612-secrets`, requests 250m / 512Mi, limits 500m / 1Gi, liveness and readiness probes `httpGet /health` on port 8081, and an `emptyDir` volume at `/app/logs`.
- `kubernetes/service.yaml`: Service `minijava-br55-con-1612-service`, ClusterIP, port 80 to container port 8080.
- `kubernetes/ingress.yaml`: Ingress `minijava-br55-con-1612-ingress` with the annotations `kubernetes.io/ingress.class: alb` and `alb.ingress.kubernetes.io/scheme: internet-facing`, host `minijava-br55-con-1612.example.com`, path `/` (Prefix) to the Service on port 80. The ALB health check is set to `/health` on port 8081 (`target-type: ip`), because the application's port 8080 is only open for a moment at start-up.

## 5. Configuration management and environment variables

| Variable | Purpose | Source |
|---|---|---|
| `APP_PORT` | port of the application's server socket (default 8080) | Deployment env |
| `HEALTH_PORT` | port of the health endpoint (default 8081, must differ from `APP_PORT`) | Deployment env |
| `CONFIG_PATH` | directory holding `app.properties` (default `/opt/app/config`, `/app/config` in the image) | Deployment env |
| `LOG_PATH` | directory of `mini-app.log` (default `/var/log`, `/app/logs` in the image) | Deployment env |
| `FILE_STORAGE_PATH` | when set, takes the place of `LOG_PATH`: mount a PersistentVolumeClaim (for example EFS) there to keep the log across pods | optional |
| `REDIS_HOST`, `REDIS_PORT` | cache endpoint (port default 6379) | `{{REDIS_HOST}}`, `{{REDIS_PORT}}` placeholders |
| `DB_PASSWORD` | MySQL password read by `DatabaseService` | Secret |
| `REDIS_PASSWORD`, `EXTERNAL_API_KEY`, `PAYMENT_SERVICE_PASSWORD`, `JWT_SECRET`, `ADMIN_PASSWORD`, `ENCRYPTION_KEY`, `MONITORING_PASSWORD`, `RABBITMQ_PASSWORD` | referenced by `application.properties` as `${NAME}` | Secret |

Database host, port, name and user in `DatabaseService` are still the values of the source code (`localhost:3306/mini_app_db`, `root`); they were not part of the containerization blockers.

## 6. Security considerations and best practices

- Credentials live only in the Kubernetes Secret (created by the deploy script from hidden prompts) and in the shell environment for Compose; no plain value is in the image, the code or the manifests.
- The container runs as the non-root user `app`; only `/app/logs` is written.
- Prefer AWS Secrets Manager with the External Secrets Operator, or the Secrets Store CSI driver, over hand-made Secrets in production; enable envelope encryption of Secrets in the EKS cluster.
- Use IRSA (IAM Roles for Service Accounts) when the pods need AWS APIs; add NetworkPolicies, and put TLS on the ALB (`alb.ingress.kubernetes.io/certificate-arn`, `listen-ports`).
- Scan the image (ECR scan on push) and keep the base image `eclipse-temurin:11-jdk` and the dependencies up to date.

## 7. Java notes

- JVM settings (`JAVA_OPTS` in the Dockerfile): `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=UTC`. Where both `-Xmx` and `MaxRAMPercentage` are set, the explicit `-Xmx512m` wins; with the 1Gi limit this leaves room for metaspace and threads. To use more memory raise the limit and `-Xmx` together (keep the heap at 50 to 75 percent of the limit), or drop `-Xmx`/`-Xms` and tune `MaxRAMPercentage`. Override at run time with the `JAVA_OPTS` environment variable.
- The jar is not a runnable jar, so the container starts `java -cp 'app.jar:lib/*' com.test.MiniApp` through `sh -c "exec java ..."`: java is the first process and receives SIGTERM from Kubernetes.
- Spring profiles: the application does not boot Spring, so `SPRING_PROFILES_ACTIVE` has no effect now; `application.properties` is the only configuration file, and its credentials are `${...}` references.
- Health: `GET /health` on `HEALTH_PORT` (8081) answers `{"status":"UP"}` from start-up for as long as the process runs. There is no Spring Boot Actuator because the application does not start Spring Boot. The liveness and readiness probes and the ALB health check use it.

## 8. Troubleshooting

Pod failures
- `kubectl get pods -n minijava-br55-con-1612`, then `kubectl describe pod <pod> -n minijava-br55-con-1612` and `kubectl logs <pod> -n minijava-br55-con-1612`.
- `ImagePullBackOff`: wrong image URI, or the nodes' IAM role lacks `AmazonEC2ContainerRegistryReadOnly`.
- `CreateContainerConfigError`: the Secret is missing a key; compare with `kubectl get secret minijava-br55-con-1612-secrets -n minijava-br55-con-1612 -o yaml`.
- `CrashLoopBackOff` with "Failed to start health endpoint: Address already in use": `HEALTH_PORT` equals `APP_PORT`.
- `OOMKilled`: raise the memory limit and `-Xmx` together.
- "Failed to initialize logging": `LOG_PATH` is not writable; keep the `emptyDir` mount at `/app/logs`.

Service issues
- `kubectl get endpoints minijava-br55-con-1612-service -n minijava-br55-con-1612` must list pod IPs; if empty the readiness probe fails or the selector `app: minijava-br55-con-1612` does not match.
- The application's server socket on port 8080 is open only about one second after start-up, so the Service port 80 does not hold a lasting listener; only the health endpoint answers continuously.

Ingress problems
- `kubectl describe ingress minijava-br55-con-1612-ingress -n minijava-br55-con-1612`; no address means the AWS Load Balancer Controller is missing or lacks IAM permissions (`kubectl logs -n kube-system deployment/aws-load-balancer-controller`).
- Unhealthy targets: the ALB health check uses `/health` on 8081; check the security groups allow the ALB to reach the pods on 8081.
- Point a DNS record for `minijava-br55-con-1612.example.com` at the ALB address (or change the host).

EKS and kubectl
- `aws eks update-kubeconfig` fails: check the region, the cluster name and `aws sts get-caller-identity`; the IAM identity must be mapped in the cluster (aws-auth ConfigMap or access entries).

## 9. Scaling and management

- Manual: `kubectl scale deployment/minijava-br55-con-1612 -n minijava-br55-con-1612 --replicas=4`.
- HPA (needs metrics-server): `kubectl autoscale deployment minijava-br55-con-1612 -n minijava-br55-con-1612 --cpu-percent=60 --min=2 --max=10`. Add Cluster Autoscaler or Karpenter for node capacity.
- Rolling update: build and push a new tag, then `kubectl set image deployment/minijava-br55-con-1612 minijava-br55-con-1612=<new image URI> -n minijava-br55-con-1612` and `kubectl rollout status deployment/minijava-br55-con-1612 -n minijava-br55-con-1612`.
- Rollback: `kubectl rollout history deployment/minijava-br55-con-1612 -n minijava-br55-con-1612`, then `kubectl rollout undo deployment/minijava-br55-con-1612 -n minijava-br55-con-1612`.
- Remove everything: `kubectl delete namespace minijava-br55-con-1612`.
