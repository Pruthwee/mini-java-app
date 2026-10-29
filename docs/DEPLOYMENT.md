# mini-java-app - Deployment Guide (AWS ECS Fargate)

mini-java-app is a plain Java 11 application built with Maven (`com.test.MiniApp`). It is **not** started through Spring Boot: `main` runs `MiniApp`, and `spring-boot-starter-web` is only declared in the `pom.xml`. After start-up the application opens its server socket on `APP_PORT` for one second, then closes it and its own work ends. The process keeps running because the health endpoint (`HealthServer`) keeps an HTTP server open, so ECS sees a running, healthy task.

## 1. Prerequisites and system requirements

- Docker 20.10 or later (to build the image locally and to run Docker Compose)
- AWS CLI v2, configured with credentials that may use ECR, ECS, ELBv2, CloudWatch Logs, Secrets Manager and STS
- For ECS Fargate:
  - a VPC with at least two subnets (public subnets when `assignPublicIp` is `ENABLED`)
  - a security group that lets in the application port (8080) and the health port (8081); with a load balancer, it must also let in port 80 from the clients
  - an EFS file system in the same VPC, with an access point for `/app/data` (POSIX user and group `10001:10001`), and a mount target in each subnet that allows NFS (TCP 2049) from the task security group
  - IAM roles `ecsTaskExecutionRole` and `ecsTaskRole` (see section 6)
- Java 11 and Maven 3.9 only if you build outside Docker: `mvn clean package`
- Sizing: 0.5 vCPU and 1 GB of memory per task (see section 7)

## 2. Local setup with Docker Compose

```bash
docker compose up --build
curl http://localhost:8081/health      # {"status":"UP"}
```

`docker-compose.yml` has one service, `mini-java-app`, with ports 8080 (application) and 8081 (health), a named volume for `/app/data` and a `./config` bind mount (read-only) for `/app/config`. There is no database, Redis or other infrastructure in it: the application reaches the cache through `REDIS_HOST` and `REDIS_PORT`. Set `REDIS_HOST` in your shell to point to a Redis you run yourself. Without one, the application only prints the cache address (the connection is simulated) and the database connection fails with a message, as before.

## 3. Build and push the image

Run from the repository root:

```bash
./scripts/build-push.sh          # Linux and macOS
scripts\build-push.bat           # Windows
```

The script asks for the registry (1. AWS ECR, 2. Docker Hub), then the registry's details, then `IMAGE_TAG` (empty means `latest`). It builds with `docker build -f Dockerfile -t <registry>/<repo>:<tag> .`, logs in (`aws ecr get-login-password ... | docker login --username AWS --password-stdin`), creates the ECR repository when it is missing and pushes. It prints the image URI to use for the deployment.

## 4. Deploy to ECS Fargate

```bash
./scripts/deploy-image.sh        # Linux and macOS
scripts\deploy-image.bat         # Windows
```

Walkthrough of what the script does:

1. Asks for the region, cluster name, VPC ID, subnet IDs (comma-separated, at least two), security group ID, the image URI, and the EFS file system and access point IDs.
2. Reads the account with `aws sts get-caller-identity` and creates the cluster when it is missing.
3. Creates the CloudWatch log group `/ecs/mini-java-app` when it is missing.
4. Stores `APP_PORT` and `REDIS_PORT` in Secrets Manager as `mini-java-app/APP_PORT` and `mini-java-app/REDIS_PORT` (create-secret, or put-secret-value when they exist) and puts the ARNs into the task definition.
5. Asks "Do you need a load balancer for this service? (y/n)". On yes it creates an Application Load Balancer `mini-java-app-alb` (listener on port 80) and a target group `mini-java-app-tg` with target type `ip`, health check `GET /health` on port 8081, and sets `healthCheckGracePeriodSeconds: 300`. On no it takes the `loadBalancers` section out of the service definition.
6. Fills the placeholders of `ecs/task-definition.json` and `ecs/service-definition.json` (the files in the repository stay as templates), registers the task definition and keeps the full ARN.
7. Creates the service, or updates it with that task definition ARN, then waits with `aws ecs wait services-stable`.
8. Shows the service status, the running task count, the load balancer DNS name and the log group.

### Task definition (`ecs/task-definition.json`)

- Family `mini-java-app-task`, `FARGATE`, `awsvpc` network mode.
- `cpu` `512` and `memory` `1024`, a valid Fargate pair. Other valid pairs: cpu 256 with 512, 1024 or 2048; 512 with 1024 to 4096; 1024 with 2048 to 8192; 2048 with 4096 to 16384; 4096 with 8192 to 30720.
- One container, `mini-java-app`, image `{{IMAGE_URI}}`, ports 8080 and 8081, no `hostPort`.
- `environment`: `HEALTH_PORT`, `DATA_PATH=/app/data`, `CONFIG_PATH=/app/config`, `REDIS_HOST`, `TZ`.
- `secrets`: `APP_PORT` and `REDIS_PORT`, read from Secrets Manager by the execution role.
- `mountPoints`: the EFS volume `efs-data` at `/app/data` (transit encryption on, IAM authorization on, through the access point).
- Logging: `awslogs`, group `/ecs/mini-java-app`, stream prefix `ecs`.
- No container `healthCheck`: the runtime image has no curl or wget, so the load balancer's target group checks `GET /health` on port 8081.

### Service (`ecs/service-definition.json`)

`mini-java-app-service`, launch type `FARGATE`, `desiredCount` 2, awsvpc networking with the two subnets and the security group, `assignPublicIp` `ENABLED`, `maximumPercent` 200 and `minimumHealthyPercent` 50, ECS managed tags and `propagateTags: SERVICE`. The load balancer sends traffic to container port 8081, the port that keeps answering for as long as the process runs (the application's own socket on 8080 is open for one second only).

## 5. Configuration management and environment variables

| Variable | Used for | Default | Where it is set |
|---|---|---|---|
| `APP_PORT` | the application's server socket | 8080 | Secrets Manager `mini-java-app/APP_PORT` (compose: plain) |
| `HEALTH_PORT` | the health endpoint `GET /health` | 8081 | task definition `environment` |
| `DATA_PATH` | writable data and the log file `mini-app.log` | `/app/data` | task definition `environment`, EFS volume |
| `CONFIG_PATH` | folder with `app.properties` | `/app/config` | task definition `environment` |
| `REDIS_HOST` | cache host | `redis` | task definition `environment` |
| `REDIS_PORT` | cache port | 6379 | Secrets Manager `mini-java-app/REDIS_PORT` |

If you change `APP_PORT` in Secrets Manager, change `containerPort` in `ecs/task-definition.json` to the same value and register a new task definition revision. A changed secret is read only when a task starts: update the service with `--force-new-deployment`.

`REDIS_HOST` is `redis` in the task definition. Make that name resolve (for example with ECS Service Connect or Cloud Map) or change the value to your cache's DNS name.

## 6. Security considerations and best practices

- **Execution role** (`ecsTaskExecutionRole`): attach `AmazonECSTaskExecutionRolePolicy` (pull from ECR, write logs) and a policy that allows `secretsmanager:GetSecretValue` on `arn:aws:secretsmanager:<region>:<account>:secret:mini-java-app/*`.
- **Task role** (`ecsTaskRole`): allow `elasticfilesystem:ClientMount` and `elasticfilesystem:ClientWrite` on the EFS file system.
- The container runs as the non-root user `app` (UID/GID 10001), so the EFS access point must use the same POSIX user and group.
- No credential is stored in the image or in the task definition. The plain passwords and keys of `src/main/resources/application.properties` were emptied; that file is not loaded by the application. Provide such values through Secrets Manager and the task definition `secrets` if you start to use them.
- **Open issue:** `DatabaseService.java` still holds a hard-coded database password in its source (see the transformation report, blocker-5); move it to a Secrets Manager secret read through an environment variable before going to production.
- Rotate the secrets, keep the ECR repository private and turn on image scanning, restrict the security group to the load balancer, and use private subnets with a NAT gateway where you can (`assignPublicIp` is `ENABLED` for simple setups).

## 7. Troubleshooting

- **Task fails to start**: look at the stopped reason with `aws ecs describe-tasks`. `CannotPullContainerError` means a wrong image URI or missing ECR permission. `ResourceInitializationError` about a secret means the execution role cannot read `mini-java-app/*`; one about EFS means the mount target, the NFS rule (TCP 2049) or the access point is wrong. EFS needs platform version 1.4.0 or later.
- **Network issues**: unhealthy targets mean the security group does not let in port 8081 from the load balancer, or the target group is not of type `ip`. A task in a public subnet needs `assignPublicIp: ENABLED` to pull the image.
- **CPU and memory errors**: the cpu and memory must be a valid Fargate pair. Exit code 137 means the JVM was killed for memory: raise `memory` and keep `-Xmx` below it.
- **Permission denied on /app/data**: the access point's POSIX user is not 10001:10001.
- Logs: `aws logs tail /ecs/mini-java-app --follow`.

## 8. Scaling and management

- **Service Auto Scaling**: register the service with Application Auto Scaling (`aws application-autoscaling register-scalable-target --service-namespace ecs --resource-id service/<cluster>/mini-java-app-service --scalable-dimension ecs:service:DesiredCount --min-capacity 2 --max-capacity 6`) and add a target tracking policy on average CPU.
- **Rolling updates** are the default (`maximumPercent` 200, `minimumHealthyPercent` 50). For **blue/green deployments**, use the CodeDeploy deployment controller with two target groups.
- Update the image: run `build-push` then `deploy-image` again; the service gets a new task definition revision.

## 9. Java notes

- **JVM settings** (`JAVA_OPTS` in the Dockerfile): `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8`. `TZ=UTC`, `LANG=C.UTF-8`. With the 1024 MB task, the 512 MB heap leaves room for metaspace, threads and the JVM. If you raise the task memory, raise `-Xmx` (or drop it and rely on `MaxRAMPercentage`); override `JAVA_OPTS` in the task definition `environment` to tune it. Note that an explicit `-Xmx` takes precedence over `MaxRAMPercentage`.
- The jar is not an executable jar (no Spring Boot repackage, no Main-Class), so the image holds `app.jar` and its dependencies in `lib/` and starts `java -cp "app.jar:lib/*" com.test.MiniApp`. `exec` makes java the first process so it receives SIGTERM from ECS.
- **Spring profiles**: the application does not start Spring, so `SPRING_PROFILES_ACTIVE` has no effect and is not set. If you move to `SpringApplication.run`, set it in the task definition `environment`.
- **Health endpoint**: `GET /health` on `HEALTH_PORT` (8081) answers `{"status":"UP"}` from start-up for as long as the process runs. It is a plain JDK `HttpServer` (`com.test.HealthServer`), because the application does not boot Spring, so Spring Boot Actuator would not start. The application's own work ends after start-up; the health server keeps the process running. The load balancer's target group uses it; the task definition has no container health check.
