# kramjav

Baseline Java project for hosting web services, built with Spring Boot and backed by a local Ollama service.

## Requirements

- Java 25 (latest LTS)
- Local Ollama instance running on the same machine, typically on `http://127.0.0.1:11434`
- A model pulled into Ollama, for example `llama3.2`

## Run locally

```powershell
.\mvnw.cmd spring-boot:run
```

The service starts on `http://localhost:8080`.

## Endpoints

- `GET /api/v1/status` — baseline service status payload
- `GET /actuator/health` — Spring Boot health endpoint
- `POST /api/v1/ai/chat` — send a prompt or message list to the local Ollama service

By default, AI requests are rejected while this PC has recent local user activity so Ollama load can be offloaded to another machine. Disable this by setting `ollama.reject-requests-when-pc-active=false`. The activity lookback window is configured with `ollama.activity-window-seconds` (default: `60`).

Requests to Ollama also time out after `ollama.request-timeout-seconds` (default: `20`), and if the request hangs or the response is invalid the service performs a best-effort restart by issuing `ollama stop <model>` and `ollama start <model>` before retrying once.

### AI request payload

```json
{
  "model": "llama3.2",
  "messages": [
    { "role": "system", "content": "Answer concisely and in plain English." },
    { "role": "user", "content": "What is the purpose of kramjav?" }
  ],
  "system": "Optional extra instructions prepended to messages[0].content."
}
```

`messages[0]` must be a `system` message. If any later `messages[i]` have role `system`, they are converted to `user` messages with content prefixed by `System: ` and a warning log is emitted with the original and normalized payload. If `messages[0]` is not `system`, the request is rejected before calling Ollama and the payload is logged.

If `model` is not provided or cannot be resolved, the service falls back to `ollama.default-model` from `application.properties` (default: `llama3.2`). The service calls the local Ollama API on the same machine, so it does not require any external network access.

## Windows service startup

There are Windows startup scripts in the `scripts` directory:

- `scripts\start-kramjav-service.cmd` — direct Windows startup script that calls `java -jar`
- `scripts\start-kramjav-service.ps1` — PowerShell version for interactive or service-setup scenarios
- `scripts\install-kramjav-service.ps1` — installs the application as a Windows service using NSSM

Example:

```powershell
.\scripts\install-kramjav-service.ps1 -ServiceName "KramjavAI"
```

The scripts build the jar before installation or startup, and then launch the Spring Boot application against the local Ollama service.

## Run tests

```powershell
.\mvnw.cmd test
```
