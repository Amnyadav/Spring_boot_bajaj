Assumptions
- You will supply the final SQL via environment variable `FINAL_QUERY` or JVM system property `-Dfinal.query`. This app does not attempt to solve or parse the PDF automatically.
- This implementation targets Question 2 (even regNo variant) as per MANIT question paper. It logs the reference URL and can optionally download the PDF for convenience.

Project Overview
Spring Boot 3.x (Java 17) Maven application that runs on startup (no web endpoints) to:
1) Call the generateWebhook API and parse `webhook` and `accessToken`.
2) Read `FINAL_QUERY` from env/system property.
3) Submit the final SQL to the testWebhook endpoint using `Authorization: <accessToken>` exactly as returned.

Key Behavior
- Startup runner: `CommandLineRunner` in `com.example.webhooksolver.WebhookSolverApplication`.
- HTTP client: `RestTemplate` with timeouts.
- Retries: Up to 3 attempts with incremental backoff for generateWebhook.
- DRY_RUN: If `DRY_RUN=true`, prints the request that would be submitted and exits 0.
- DOWNLOAD_PDF: If `DOWNLOAD_PDF=true`, downloads Question 2 PDF to `downloads/question2.pdf` (non-blocking if it fails).
- Even regNo: Logs Question 2 link: https://drive.google.com/file/d/143MR5cLFrlNEuHzzWJ5RHnEWuijuM9X/view?usp=sharing

Tech Stack
- Java 17
- Spring Boot 3.x
- Maven

Project Coordinates
- groupId: `com.example`
- artifactId: `spring-webhook-solver`
- version: `1.0.0`

Configuration Keys (can be set via `application.properties`, env vars, or `-D`)
- `generate.url` (default: hiring/generateWebhook/JAVA)
- `test.url` (default: hiring/testWebhook/JAVA)
- `user.name`, `user.regno`, `user.email`
- `final.query` (alternative to env `FINAL_QUERY`)
- `DRY_RUN` (env or property) -> `true`/`false`
- `DOWNLOAD_PDF` (env or property) -> `true`/`false`

Environment Setup
Windows (PowerShell, using Chocolatey):
```powershell
choco install temurin17jdk -y
java -version

choco install maven -y
mvn -version

choco install git -y
git --version
```

macOS (Homebrew):
```bash
brew install temurin17 maven git
java -version
mvn -version
git --version
```

Linux (Debian/Ubuntu):
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven git
java -version
mvn -version
git --version
```

Build
```bash
mvn clean package
```

Run (Windows PowerShell)
Set required environment variables and run the JAR:
```powershell
$env:FINAL_QUERY = "SELECT * FROM your_table WHERE id = 1;"
# Optional flags
$env:DRY_RUN = "false"        # set to "true" for dry run
$env:DOWNLOAD_PDF = "false"    # set to "true" to download the PDF

java -jar target/spring-webhook-solver-1.0.0.jar
```

Alternative: Use JVM property instead of env var
```powershell
java -Dfinal.query="SELECT * FROM your_table WHERE id = 1;" -jar target/spring-webhook-solver-1.0.0.jar
```

Override user details or endpoints
```powershell
java -Duser.name="Your Name" -Duser.regno="123456" -Duser.email="you@example.com" `
     -Dgenerate.url="https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA" `
     -Dtest.url="https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA" `
     -Dfinal.query="SELECT 1" `
     -jar target/spring-webhook-solver-1.0.0.jar
```

Run (macOS/Linux)
```bash
export FINAL_QUERY='SELECT * FROM your_table WHERE id = 1;'
export DRY_RUN=false
export DOWNLOAD_PDF=false

java -jar target/spring-webhook-solver-1.0.0.jar
```

Security Notes
- Prefer setting `FINAL_QUERY` via environment variable to avoid storing secrets in code or VCS.
- Authorization header uses the `accessToken` exactly as returned. If you need to prefix with `Bearer ` for any reason, change the code where the `Authorization` header is set.
- The app does not attempt to automatically solve the PDF; you must supply the SQL string.

Dry Run Mode
- Set `DRY_RUN=true` to avoid calling the submission endpoint. The app will print the exact URL, headers (token value), and JSON body it would send.

Packaging and Output
- Builds a runnable JAR at `target/spring-webhook-solver-1.0.0.jar`.

GitHub
```bash
git init
git add .
git commit -m "Initial commit: Spring Webhook Solver"
git branch -M main
git remote add origin <your_repo_url>
git push -u origin main
```

Troubleshooting
- Missing FINAL_QUERY: Set `FINAL_QUERY` or `-Dfinal.query`.
- Network/timeout: Ensure connectivity to `healthrx.co.in` API. Retries are built-in for generateWebhook.
- Exit codes: 0 on success (or dry-run), non-zero on fatal errors (missing inputs, failed requests, etc.).


"# Spring_boot_bajaj" 
