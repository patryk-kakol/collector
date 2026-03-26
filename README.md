# SFTP Collector Service

This Spring Boot application is designed to periodically scan configured SFTP servers and record file metadata into a PostgreSQL database. It supports password, SSH key, and combined (MFA) authentication methods.

## Local Development Setup

To make local development and testing easy, a complete environment is provided via Docker Compose. This environment spins up a replicated database setup along with mock SFTP servers:

1.  **pgaf_monitor**: PostgreSQL Monitor node
1.  **pgaf_node1**: PostgreSQL Node 1
1.  **pgaf_node2**: PostgreSQL Node 2
2.  **switchover_bot**: Schema initializer and switchover orchestrator working in 5 minute loop
3.  **local-sftp-pwd**: Mock server configured for password authentication.
4.  **local-sftp-key**: Mock server configured for SSH key authentication.
5.  **local-sftp-mfa**: Mock server configured for both password and key authentication.

### Prerequisites

*   Docker Desktop (or Docker Engine + Docker Compose)
*   Java 25
*   OpenSSH (specifically the `ssh-keygen` utility, usually included in Windows/Git Bash/Linux)

### Getting Started

Follow these steps to run the application locally:

#### 1. Generate SSH Keys

The local SFTP servers require SSH keys to function. A helper script is provided to generate these automatically.

**On Windows / Linux / macOS:**
Run the script from the terminal (use Git Bash or similar on Windows):
```bash
./scripts/generate-keys.sh
```

*Note: This will create a `local-env/keys` directory in your project root containing the generated `local_rsa` private key and `local_rsa.pub` public key. The `.pub` key is automatically mounted to the Docker containers.*

#### 2. Start the Docker Environment

Start the databases and all SFTP mock servers in the background:

```bash
docker-compose up -d
```

*(If you are switching from a previous non-replicated setup, make sure to wipe old volumes first with `docker-compose down -v` before running `up -d` This action also usually solves most other issues you can encounter.)*.

#### 3. Run the Application

Start the Spring Boot application using the `local` profile. This profile tells the application to use the `application-local.yml` configuration, which points to your new Docker containers.

**Using IntelliJ IDEA:**
*   Edit your Run Configuration for `Application.java`.
*   Add `--spring.profiles.active=local` to the "Program arguments" field.
*   *Alternatively*, set the Environment Variable: `SPRING_PROFILES_ACTIVE=local`.

**Using Gradle (Terminal):**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

#### 4. Testing the SFTP Scanning

Once the application is running, it will automatically scan the configured servers every 1 minute (as defined in `application-local.yml`).

To see the application in action, you can drop files into the mapped local directories:

*   **Password Server:** Add files to `local-env/sftp-pwd-upload/`
*   **Key Server:** Add files to `local-env/sftp-key-upload/`
*   **MFA Server:** Add files to `local-env/sftp-mfa-upload/`

*(If these directories don't exist yet, Docker will create them, or you can create them manually).*

Watch your application logs. Within a minute, you should see the `SftpProcessingService` detect the new files and save their metadata to the database.

#### 5. Database Connection Instructions

You can connect to the local PostgreSQL databases using IntelliJ's Database tool or any client (like DBeaver or pgAdmin) to verify records and test replication.

**Primary Node (Read/Write)**
*This is the connection the Spring Boot application uses.*
*   **Connection Type:** `URL only`
*   **Authentication:** `User & Password`
*   **User:** `collector`
*   **Password:** `collector`
*   **URL:** `jdbc:postgresql://localhost:5001,localhost:5002/postgres?targetServerType=primary`

### Stopping the Environment

When you are done developing, you can stop and remove the Docker containers:

```bash
docker-compose down
```

*(Note: The database data is persisted in Docker volumes named `postgres-primary-data` and `postgres-standby-data`. If you want to completely wipe the databases and start fresh next time, use `docker-compose down -v`)*.