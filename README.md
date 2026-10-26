# QueueLess – Smart Queue and Token Management System

Book a digital token for a college counter (library, lab, accounts office, clinic), watch your queue position, and let administrators call, serve and complete tokens. Built as a DevOps case study: **Spring Boot + MySQL + plain HTML/CSS/JS, Git/GitHub, Jenkins, Docker, Kubernetes, Selenium.**

> **Honest status:** the code was written and syntax-checked, but the Java build, the automated tests, the Docker images and the Kubernetes deployment were **not executed** by the author of this package (no Maven/Docker access in that environment). Run the verification steps below on your machine and record your own results for the lab report. If something fails, paste the exact error and fix it before continuing.

## 1. Architecture

```
 Browser ──► nginx (frontend container, port 8080)
               │  static HTML/CSS/JS
               └─ /api/*  ──► Spring Boot backend (port 8081) ──► MySQL (volume)
```

* **frontend/** – static site served by nginx; nginx forwards `/api` to the service named `backend` (works in Docker Compose and Kubernetes).
* **backend/** – modular monolith: `controller → service → repository → entity`, DTOs, JWT security (BCrypt passwords), central exception handling.
* **Database** – MySQL 8.4 in Docker/Kubernetes; a local H2 file database when you run the backend without Docker.

```
queueless/
├── backend/            Spring Boot (pom.xml, Dockerfile, src/main, src/test)
├── frontend/           public/ (7 pages, css, js), nginx.conf, Dockerfile, check.sh
├── selenium-tests/     Selenium WebDriver + JUnit 5 (separate Maven project)
├── k8s/                Kubernetes manifests (00-…04-) and examples/secret.example.yaml
├── jenkins/            Dockerfile + compose.yaml for a local Jenkins
├── postman/            QueueLess.postman_collection.json
├── compose.yaml        whole application with MySQL
├── Jenkinsfile         CI/CD pipeline
├── .env.example        placeholders only
└── README.md
```

## 2. Prerequisites (Windows)

| Tool | Needed for | Mandatory |
|---|---|---|
| JDK 17 | running/testing backend without Docker, Selenium tests | Weeks 1-5, 10 |
| Git for Windows | version control | Yes |
| Docker Desktop (WSL 2) | Compose, Jenkins, Kubernetes | Weeks 4-9 |
| Chrome | Selenium tests | Week 10 |
| Postman | API testing | Optional |
| Python or Node | tiny static server for frontend-only local dev | Optional |

Tip: in Docker Desktop → Settings → Resources you can move the disk image to another drive to keep C: free.

## 3. Quick start with Docker (recommended)

PowerShell, in the project folder:

```powershell
Copy-Item .env.example .env
notepad .env                      # replace every CHANGE_ME (JWT_SECRET: 32+ random characters)
docker compose up -d --build
docker compose ps                 # wait until mysql, backend and frontend show "healthy"
```

Open **http://localhost:8080**. Log in as the administrator with `ADMIN_EMAIL` / `ADMIN_PASSWORD` from your `.env`. Register a normal user in another browser/profile to try the user side.

Useful commands:

```powershell
docker compose logs -f backend          # application logs
docker compose stop                     # stop, keep data
docker compose start                    # start again
docker compose down                     # remove containers, KEEP the database volume
docker compose down -v                  # ALSO DELETES the database volume = all users and tokens!
docker ps ; docker images ; docker network ls ; docker inspect queueless-backend
```

## 4. Run the backend without Docker (quickest for development)

```powershell
cd backend
.\mvnw.cmd test                         # unit + integration tests (in-memory H2)
.\mvnw.cmd spring-boot:run              # http://localhost:8081, file database in backend\data\
```

If `ADMIN_PASSWORD` is not set, a random admin password is **printed once in the startup log** (look for `Generated a one-time admin password`). To choose your own:

```powershell
$env:ADMIN_PASSWORD = "MyLocalAdminPass1"; .\mvnw.cmd spring-boot:run
```

Serve the frontend on port 3000 (config.js then calls the backend on 8081 directly; CORS allows it):

```powershell
cd frontend\public
python -m http.server 3000              # then open http://localhost:3000
```

If you used the earlier prototype, delete its old `backend\data\` folder; this version uses a new schema.

## 5. Features and rules

**Users:** register/login (BCrypt), dashboard with services, book a token, queue position, estimated wait (clearly an estimate), live status (auto-refresh every 5 s), history, cancel a WAITING token.
**Admins:** role-protected dashboard, per-service *Call next*, mark *Serving* / *Complete*, cancel WAITING/CALLED, filter tokens by service and status, create/edit/activate/deactivate service counters, statistics.

Queue rules (all enforced in the backend):

* Status lifecycle: `WAITING → CALLED → SERVING → COMPLETED`; `WAITING/CALLED → CANCELLED`. Anything else returns **409**.
* Token numbers are sequential per **service and day** (restart at 1 each day, history kept). A unique database constraint backs this up.
* **Ordering rule:** queue order = ascending token number within (service, day). Position = 1 + number of WAITING tokens with a smaller number.
* One active token (WAITING/CALLED/SERVING) per user per service per day (otherwise 409).
* **Concurrency:** every queue change first locks the service row (`SELECT … FOR UPDATE`) inside a transaction, so two requests can never get the same number and two admins can never call the same token. Tested in `QueueLessIntegrationTest`.
* Estimated wait = (waiting tokens ahead) × (average of the last 50 completed service durations, default 5 min, minimum 1 min each).
* Users only see their own tokens (others return 404). Admin endpoints require the ADMIN role (403 otherwise).
* "Today" uses the time zone `APP_TIMEZONE` (default `Asia/Kolkata`).

## 6. REST API

Base URL: `http://localhost:8081/api` (or `http://localhost:8080/api` through nginx). Send `Authorization: Bearer <token>` except for `/auth/*`. Import `postman/QueueLess.postman_collection.json` to try everything.

| Method & path | Role | Description | Success |
|---|---|---|---|
| POST `/auth/register` | public | create a USER account | 201 |
| POST `/auth/login` | public | get a JWT | 200 |
| GET `/services` | user | active services with waiting count | 200 |
| POST `/tokens` | user | book a token `{"serviceId":1}` | 201 |
| GET `/tokens/my` | user | my token history | 200 |
| GET `/tokens/{id}` | user | track one of my tokens | 200 |
| POST `/tokens/{id}/cancel` | user | cancel my WAITING token | 200 |
| GET/POST `/admin/services`, PUT `/admin/services/{id}` | admin | list / create / update counters | 200/201/200 |
| GET `/admin/tokens?serviceId=&status=` | admin | list tokens (latest 200) | 200 |
| POST `/admin/services/{id}/call-next` | admin | call next WAITING token | 200 |
| POST `/admin/tokens/{id}/serving` · `/complete` · `/cancel` | admin | change status | 200 |
| GET `/admin/stats` | admin | counts and average service time | 200 |
| GET `/actuator/health` | public | health check | 200 |

Examples:

```http
POST /api/auth/register
{ "fullName": "Asha Rao", "email": "asha@example.com", "password": "StrongPass1" }

201 { "token": "eyJ…", "user": { "id": 2, "fullName": "Asha Rao", "email": "asha@example.com", "role": "USER" } }
```

```http
POST /api/tokens            (Bearer token)
{ "serviceId": 1 }

201 { "id": 7, "serviceId": 1, "serviceName": "Library Desk", "tokenNumber": 3, "serviceDate": "2026-10-22",
      "status": "WAITING", "queuePosition": 3, "estimatedWaitMinutes": 10, "createdAt": "2026-10-22T05:12:09Z" }
```

Error format (same everywhere):

```json
{ "timestamp": "2026-10-22T05:12:09Z", "status": 409, "error": "Conflict",
  "message": "You already have an active token for this service" }
```

`400` validation (adds `fieldErrors`), `401` not logged in / bad credentials, `403` wrong role, `404` not found (also for other people's tokens), `409` rule violation or duplicate, `500` safe generic message.

## 7. Database / schema initialisation

Tables `users`, `service_counters`, `queue_tokens` are created and updated automatically by Hibernate (`spring.jpa.hibernate.ddl-auto=update`) on first start. This keeps the case study simple; for production you would switch to versioned migrations (Flyway/Liquibase). Seed data: four sample services and one admin whose password comes from `ADMIN_PASSWORD` (never from source code).

ER summary: `users 1—* queue_tokens *—1 service_counters`; unique `(service_id, service_date, token_number)`; unique `users.email`, unique `service_counters.name`.

## 8. Tests

* **Backend** (`backend/src/test`): `TokenStatusTest` (unit) and `QueueLessIntegrationTest` (register/login, authorization, full lifecycle, ownership, error handling, concurrent booking, concurrent call-next). Run: `cd backend; .\mvnw.cmd test`.
* **Selenium** (`selenium-tests`): invalid/valid login, registration validation, dashboard, booking and queue position, status update on the tracking page, cancel, admin call-next → serving → complete, empty-queue error, add service, non-admin blocked from admin page. Uses explicit waits and creates its own data via the API.

```powershell
docker compose up -d --build                      # application must be running
cd selenium-tests
$env:ADMIN_PASSWORD = "<same value as in .env>"   # optional: $env:BASE_URL = "http://localhost:8080"
mvnw.cmd test                                     # Selenium Manager downloads ChromeDriver automatically
$env:HEADLESS = "false"; .\mvnw.cmd test                 # watch the browser
```

(Use `mvnw.cmd test` in Command Prompt; the Maven wrapper is included, no separate Maven install needed.)

## 9. Jenkins

**A. Your own Jenkins on Windows (default `Jenkinsfile`, uses `bat`/PowerShell).** The Windows account running Jenkins needs Git, JDK 17+ and Docker Desktop (`docker ps` must work for that account; if Jenkins runs as a Windows service, set its *Log On* account to your user and add that user to the `docker-users` group). Add the credentials below, create a Pipeline job from SCM with script path `Jenkinsfile`, then *Build with Parameters*. Extra credential for Selenium only: *Secret text* with ID `queueless-admin-password`.

**B. Jenkins in Docker (Linux pipeline: `jenkins/Jenkinsfile.linux`)** – use this only if you do not have your own Jenkins; set the job's script path to `jenkins/Jenkinsfile.linux`.

```powershell
docker compose -f jenkins/compose.yaml up -d --build
docker exec queueless-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

1. Open http://localhost:9090, paste the password, install suggested plugins, create an admin user.
2. **Credentials** (Manage Jenkins → Credentials → Global → Add):
   * *Secret file*, ID `queueless-env-file`: upload your real `.env`.
   * *Username with password*, ID `registry-credentials`: only if you tick `PUSH_IMAGES`.
3. **New Item → Pipeline** → *Pipeline script from SCM* → Git → your GitHub repo URL → branch `main` → script path `Jenkinsfile`.
4. **Build with Parameters** (manual build works without any webhook).
5. Optional automatic builds: GitHub repo → Settings → Webhooks → `http://<public-url>/github-webhook/` (a local Jenkins needs a tunnel such as ngrok; otherwise use *Build Now* or *Poll SCM*).

Stages: checkout → backend compile+test → frontend checks → Docker build → (push) → deploy with Compose → health check → (Selenium). Secrets are read from Jenkins credentials and never echoed; `.env` is deleted after each run. Note: the Jenkins container controls the host Docker through `docker.sock`, which is acceptable for a personal lab but not for production.

## 10. Kubernetes (Docker Desktop or Minikube)

Enable Kubernetes in Docker Desktop (Settings → Kubernetes), **or** use Minikube. Build the images with the tags the manifests expect:

```powershell
docker build -t queueless-backend:1.0 backend
docker build -t queueless-frontend:1.0 frontend
# Minikube only: minikube image load queueless-backend:1.0 ; minikube image load queueless-frontend:1.0

kubectl apply -f k8s/00-namespace.yaml
kubectl create secret generic queueless-secrets -n queueless `
  --from-literal=DB_PASSWORD='<choose>' --from-literal=MYSQL_ROOT_PASSWORD='<choose>' `
  --from-literal=JWT_SECRET='<32+ random characters>' --from-literal=ADMIN_PASSWORD='<choose>'
kubectl apply -f k8s/01-configmap.yaml -f k8s/02-mysql.yaml -f k8s/03-backend.yaml -f k8s/04-frontend.yaml
kubectl get pods -n queueless -w
```

Open **http://localhost:30080** (Docker Desktop) or `minikube service frontend -n queueless --url`.

```powershell
kubectl get pods,svc,pvc -n queueless
kubectl logs deploy/backend -n queueless
kubectl describe pod <pod-name> -n queueless       # image-pull problems appear under Events
kubectl exec -n queueless deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"'
kubectl scale deploy/backend --replicas=2 -n queueless        # only after the 1st backend pod is Ready
docker build -t queueless-backend:1.1 backend
kubectl set image deploy/backend backend=queueless-backend:1.1 -n queueless
kubectl rollout status deploy/backend -n queueless
kubectl rollout undo deploy/backend -n queueless              # roll back
```

`ErrImagePull`/`ImagePullBackOff` almost always means the image tag is missing from the cluster's Docker (Minikube: run `minikube image load`) or the tag in the manifest differs from the one you built.

**Local vs production:** this is a single-node *development* setup: one MySQL pod with a PVC, NodePort exposure, no TLS, no Ingress, no backups, no high availability. A production setup would use a managed database, Ingress with TLS, a secrets manager, image registry pulls, network policies and monitoring.

## 11. Git and GitHub (Windows)

```powershell
git init -b main
git add .
git status                                   # confirm .env and target/ are NOT listed
git commit -m "feat: initial QueueLess application"
# create an empty repo named queueless on github.com (no README), then:
git remote add origin https://github.com/<your-username>/queueless.git
git push -u origin main
```

Feature-branch workflow (Week 3):

```powershell
git switch -c feature/admin-stats
# edit, then:
git add -p ; git commit -m "feat(admin): show average service time"
git push -u origin feature/admin-stats       # open a Pull Request on GitHub, review, merge
git switch main ; git pull
```

Never commit `.env`, real Kubernetes secrets or passwords. If you ever do, rotate them immediately.

## 12. Ten-week mapping

| Week | What to do with this repository | Evidence for the lab record |
|---|---|---|
| 1 | Run backend + frontend locally, book tokens | screenshots of pages, API responses |
| 2 | Push to GitHub | commit list, repo page |
| 3 | Branch, merge, pull request | PR screenshot, `git log --graph --oneline` |
| 4 | Start Jenkins, create credentials and a first job | Jenkins dashboard |
| 5 | Pipeline stages: build + test | stage view, JUnit results |
| 6 | Docker commands (images, containers, networks, logs) | command outputs |
| 7 | `docker compose up`, use the app on :8080 | `docker compose ps`, app screenshot |
| 8 | Apply `k8s/`, scale, update, roll back | `kubectl get` outputs |
| 9 | Full pipeline with deploy + health check | green build, console log |
| 10 | Selenium tests | `mvn test` result, report |

Record only what you actually ran and observed.

## 13. Known limitations

* No email/SMS notifications; status updates use 5-second polling, not WebSockets.
* Tokens still WAITING at midnight are not auto-expired; each day's queue only considers that day's tokens.
* JWTs last 2 hours and cannot be revoked; they are kept in `sessionStorage` (cleared when the tab closes).
* No rate limiting or account lockout; schema managed by Hibernate `update`, not versioned migrations.
* The first backend start creates tables, so run one backend replica until it is Ready before scaling.
* Single-node Kubernetes and a single MySQL instance are not highly available.
