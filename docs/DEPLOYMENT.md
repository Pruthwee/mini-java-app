# Deployment guide - minijava_s55_con_1433 on AWS EKS

This guide covers the container of the application `minijava_s55_con_1433` (Java 11, Maven): local setup with Docker Compose, building and pushing the image to AWS ECR, and the deployment on AWS EKS with kubectl. Only the application's own container is described. Every external service (MySQL, Redis, third-party APIs, RabbitMQ) is provided separately and reached through environment variables.

## 1. What the application is

- A plain Java 11 application (`com.test.MiniApp`), packaged as an ordinary jar by `mvn package` (no Spring Boot plugin, so no runnable jar). `spring-boot-starter-web` is declared in the `pom.xml`, but `main` never starts Spring, so there is no actuator endpoint.
- **The application's own work ends after start-up.** `main` loads the configuration, writes the log file, connects to the database, opens a server socket on `APP_PORT` for about one second and returns. The process stays up because the **health endpoint server** (JDK `com.sun.net.httpserver`, no extra dependency) keeps running.
- Health endpoint: `GET /health` answers `{"status":"UP"}` with HTTP 200, on `HEALTH_PORT` (default 8081), from start-up for as long as the process runs. It is on its own port because the application's server socket uses `APP_PORT` (8080).
- Because the application's own socket on 8080 is closed after about a second, the Service and Ingress that route to port 8080 have nothing to talk to in the running pod: the pods stay Ready through `/health` on 8081. This is the application's behaviour and was not changed.
- The database URL host and port (`localhost:3306`) and the database user are still constants in `DatabaseService`; only the password (`DB_PASSWORD`) and the Redis host and port are read from the environment, as the analysis report asked. A database that is not reachable is logged and the application continues.

## 2. Prerequisites and system requirements

- Docker 20.10+ (to build the image) and Docker Compose v2 (local run).
- AWS CLI v2, configured with `aws configure` or SSO.
- `kubectl` matching the cluster's Kubernetes version (+/- one minor version).
- `eksctl` (to create a cluster, optional if you already have one).
- JDK 11 and Maven 3.9 only if you build outside Docker.
- IAM permissions: `ecr:GetAuthorizationToken`, `ecr:CreateRepository`, `ecr:DescribeRepositories`, `ecr:BatchCheckLayerAvailability`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, `ecr:PutImage`; `eks:DescribeCluster` (for `aws eks update-kubeconfig`); and Kubernetes RBAC rights in the cluster (the creator of an EKS cluster is admin; others need an access entry or an `aws-auth` mapping).
- Resources per pod: requests 250m CPU / 512Mi memory, limits 500m CPU / 1Gi memory.
- In the cluster: the AWS Load Balancer Controller (for the Ingress).

## 3. Local setup with Docker Compose

`docker-compose.yml` has one service, `minijava-s55-con-1433`. Credentials are taken from the shell that runs compose; nothing is written in the file.

```bash
export REDIS_HOST=redis.example.internal
export DB_PASSWORD=...            # also REDIS_PASSWORD, EXTERNAL_API_KEY, PAYMENT_SERVICE_PASSWORD,
export JWT_SECRET=...             # JWT_SECRET, ADMIN_PASSWORD, ENCRYPTION_KEY, MONITORING_PASSWORD,
                                  # RABBITMQ_PASSWORD (compose stops with "set <NAME>" if one is missing)
docker compose up --build
curl http://localhost:8081/health
```

Volumes: `./config` is mounted read-only at `/app/config` (put `app.properties` there) and the named volume `app-logs` at `/app/logs`.

## 4. Build and push the image

Run both scripts from the repository root. They ask for the registry (1. AWS ECR, 2. Docker Hub), then its details, then `IMAGE_TAG` (empty means `latest`).

```bash
./scripts/build-push.sh          # Linux / macOS
scripts\build-push.bat           # Windows
```

For ECR the script logs in with `aws ecr get-login-password`, and creates the repository when it is missing. The last line prints the full image URI to give to `deploy-image`.

The Dockerfile is multi-stage: `maven:3.9.4-eclipse-temurin-11` builds the jar and copies the runtime dependencies to `target/lib`; the runtime stage is `eclipse-temurin:11-jdk`, runs as the non-root user `appuser` (uid 1001) and starts `java -cp "app.jar:lib/*" com.test.MiniApp` through `sh -c "exec java ..."`, so java receives SIGTERM.

## 5. AWS EKS: cluster setup and kubectl

```bash
eksctl create cluster --name my-cluster --region us-east-1 --nodes 2 --node-type t3.medium
aws eks update-kubeconfig --region us-east-1 --name my-cluster
kubectl cluster-info
```

Install the AWS Load Balancer Controller (Helm chart `eks/aws-load-balancer-controller`, with an IAM role for its service account) so the Ingress annotations `kubernetes.io/ingress.class: alb` and `alb.ingress.kubernetes.io/scheme: internet-facing` create an internet-facing ALB.

## 6. Deploy

```bash
./scripts/deploy-image.sh        # Linux / macOS
scripts\deploy-image.bat         # Windows
```

The script asks for the AWS region, the cluster name, the image URI, `REDIS_HOST` and `REDIS_PORT` (Enter keeps `localhost` and `6379`), and each credential (not echoed). It then configures kubectl (`aws eks update-kubeconfig`), checks `kubectl cluster-info`, applies the namespace, creates the Secret `minijava-s55-con-1433-secrets`, fills the `{{...}}` placeholders of `kubernetes/deployment.yaml` (and puts the file back afterwards), applies the deployment, service and ingress, waits with `kubectl rollout status deployment/minijava-s55-con-1433 -n minijava-s55-con-1433`, and prints the pods, services, ingress and the application's URL.

### The manifests

- `kubernetes/namespace.yaml`: Namespace `minijava-s55-con-1433`, label `name`.
- `kubernetes/deployment.yaml`: 2 replicas; container ports 8080 (application) and 8081 (health); env for the paths and ports, `REDIS_HOST` and `REDIS_PORT` as placeholders, every credential from `secretKeyRef` of `minijava-s55-con-1433-secrets`; resources as above; liveness and readiness probes `GET /health` on port 8081; an `emptyDir` at `/app/logs` for the log file.
- `kubernetes/service.yaml`: ClusterIP `minijava-s55-con-1433-service`, port 80 to container port 8080.
- `kubernetes/ingress.yaml`: ALB ingress for host `minijava-s55-con-1433.example.com`, path `/` to the service on port 80; the ALB health check uses `/health` on port 8081.

### Failure and rollback

```bash
kubectl rollout undo deployment/minijava-s55-con-1433 -n minijava-s55-con-1433
kubectl rollout status deployment/minijava-s55-con-1433 -n minijava-s55-con-1433
```

## 7. Configuration management and environment variables

| Variable | Purpose | Default |
|---|---|---|
| `APP_PORT` | application server socket port | 8080 |
| `HEALTH_PORT` | `/health` port | 8081 |
| `CONFIG_PATH` | full path of `app.properties` | `/opt/app/config/app.properties` (image: `/app/config/app.properties`) |
| `FILE_STORAGE_PATH` | directory of the log file | `/var/log` (image: `/app/logs`) |
| `LOG_PATH` | full path of the log file | `$FILE_STORAGE_PATH/mini-app.log` |
| `REDIS_HOST`, `REDIS_PORT` | cache server | `localhost`, `6379` |
| `DB_PASSWORD` | database password (Secret) | none |
| `REDIS_PASSWORD`, `EXTERNAL_API_KEY`, `PAYMENT_SERVICE_PASSWORD`, `JWT_SECRET`, `ADMIN_PASSWORD`, `ENCRYPTION_KEY`, `MONITORING_PASSWORD`, `RABBITMQ_PASSWORD` | referenced by `application.properties` as `${NAME:}` (Secret) | empty |

`application.properties` is packaged in the jar but not loaded by `MiniApp`; its credential values are references to the variables above, so no plain value reaches the image. To use shared durable storage instead of the `emptyDir`, install the AWS EFS CSI driver, create a ReadWriteMany PersistentVolumeClaim and mount it at `FILE_STORAGE_PATH` in place of the `app-logs` volume. To supply `app.properties`, create a ConfigMap and mount it at `/app/config` with the key `app.properties`.

## 8. Security considerations and best practices

- Credentials only live in the Kubernetes Secret (created by the script from values typed at run time) and in the shell environment for Compose; never commit them. Prefer AWS Secrets Manager with the External Secrets Operator in production.
- The container runs as non-root (uid 1001); add `securityContext` (`runAsNonRoot`, `allowPrivilegeEscalation: false`) and NetworkPolicies as needed.
- Scan the image (ECR scanning), pin image tags (do not deploy `latest`), and use IRSA for any AWS access instead of access keys.
- Terminate TLS at the ALB (add `alb.ingress.kubernetes.io/certificate-arn` and a 443 listener).
- `spring-boot-starter-web` 2.7.0 and `mysql-connector-java` 8.0.33 are old; plan an update.

## 9. Troubleshooting

- **Pod failures**: `kubectl get pods -n minijava-s55-con-1433`, `kubectl describe pod <pod> -n minijava-s55-con-1433`, `kubectl logs <pod> -n minijava-s55-con-1433`. `ImagePullBackOff`: the node role lacks ECR read access or the image URI is wrong. `CreateContainerConfigError`: the Secret is missing a key. `CrashLoopBackOff`/probe failures: check that `/health` answers on 8081 (`kubectl port-forward <pod> 8081:8081`, then `curl localhost:8081/health`), and the log line "Failed to start health endpoint" (port already in use).
- **OOMKilled**: raise the memory limit and keep `MaxRAMPercentage` below 100.
- **Service issues**: `kubectl get endpoints -n minijava-s55-con-1433`; remember the application's socket on 8080 is only open for about a second after start-up.
- **Ingress problems**: `kubectl describe ingress minijava-s55-con-1433-ingress -n minijava-s55-con-1433`; check the AWS Load Balancer Controller logs (`kubectl logs -n kube-system deployment/aws-load-balancer-controller`), subnets tagged for ELB, and that the target group health check (`/health`, port 8081) is healthy.
- **kubectl access denied**: re-run `aws eks update-kubeconfig` and check the IAM identity has an EKS access entry.

## 10. Scaling and management

- Manual: `kubectl scale deployment/minijava-s55-con-1433 -n minijava-s55-con-1433 --replicas=4`.
- HPA (needs metrics-server): `kubectl autoscale deployment minijava-s55-con-1433 -n minijava-s55-con-1433 --cpu-percent=60 --min=2 --max=10`.
- Rolling update: set a new image with `kubectl set image deployment/minijava-s55-con-1433 minijava-s55-con-1433=<new image URI> -n minijava-s55-con-1433`, or re-run `deploy-image`; follow with `kubectl rollout status`.
- History and rollback: `kubectl rollout history deployment/minijava-s55-con-1433 -n minijava-s55-con-1433`, `kubectl rollout undo ...`.
- Cluster nodes: `eksctl scale nodegroup --cluster my-cluster --name <nodegroup> --nodes 3`.

## 11. Java notes

- JVM settings (`JAVA_OPTS` in the image): `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`. With a 1Gi limit, `-Xmx512m` caps the heap at half of the memory, leaving room for metaspace, threads and native memory. If you raise the pod's memory limit, raise `-Xmx` (or drop it and rely on `MaxRAMPercentage`, which `-Xmx` overrides) in step. Override with the `JAVA_OPTS` environment variable in the Deployment.
- `TZ=UTC`, `LANG=C.UTF-8`, `LC_ALL=C.UTF-8` are set in the image.
- Spring profiles: `SPRING_PROFILES_ACTIVE` is set to `prod` (Compose default and Deployment) for the day Spring is started; `MiniApp` does not start Spring, so it has no effect today. There are no `application-<profile>` files.
- Health endpoints: the platform uses `GET /health` on port 8081 for the liveness and readiness probes and the ALB health check. There is no actuator.
