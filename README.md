# kramjav

Baseline Java project for hosting web services, built with Spring Boot.

## Requirements

- Java 25 (latest LTS)

## Run locally

```powershell
.\mvnw.cmd spring-boot:run
```

The service starts on `http://localhost:8080`.

## Endpoints

- `GET /api/v1/status` — baseline service status payload
- `GET /actuator/health` — Spring Boot health endpoint

## Run tests

```powershell
.\mvnw.cmd test
```
