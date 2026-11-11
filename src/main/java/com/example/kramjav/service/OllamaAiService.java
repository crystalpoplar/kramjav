package com.example.kramjav.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.kramjav.web.AiRequest;

@Service
public class OllamaAiService {

	private static final Logger log = LoggerFactory.getLogger(OllamaAiService.class);

	private final RestClient restClient;
	private final OllamaProperties properties;
	private final PcActivityDetector pcActivityDetector;

	public OllamaAiService(RestClient restClient, OllamaProperties properties, PcActivityDetector pcActivityDetector) {
		this.restClient = restClient;
		this.properties = properties;
		this.pcActivityDetector = pcActivityDetector;
	}

	public AiResponse generate(AiRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("A request body is required.");
		}

		List<AiRequest.Message> messages = request.normalizedMessages();
		if (messages.isEmpty()) {
			throw new IllegalArgumentException("Provide a prompt or at least one message.");
		}

		rejectIfPcIsActive();

		String model = resolveModel(request.model());
		Map<String, Object> payload = buildPayload(model, request, messages);

		AiResponse response = callOllamaWithRetry(payload, model, 1);
		if (response != null) {
			return response;
		}

		throw new IllegalStateException("Ollama returned an invalid payload for model '" + model + "'.");
	}

	private void rejectIfPcIsActive() {
		if (!Boolean.TRUE.equals(properties.rejectRequestsWhenPcActive())) {
			return;
		}

		Duration activityWindow = Duration.ofSeconds(properties.activityWindowSeconds());
		if (pcActivityDetector.hasRecentUserInput(activityWindow)) {
			throw new ServiceBusyException("Request rejected because this PC is active and Ollama offload is enabled.");
		}
	}

	private Map<String, Object> buildPayload(String model, AiRequest request, List<AiRequest.Message> messages) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("model", model);
		payload.put("stream", false);

		List<Map<String, String>> ollamaMessages = new ArrayList<>();
		if (request.system() != null && !request.system().isBlank()) {
			ollamaMessages.add(Map.of("role", "system", "content", request.system()));
		}
		for (AiRequest.Message message : messages) {
			String role = Optional.ofNullable(message.role()).filter(value -> !value.isBlank()).orElse("user");
			ollamaMessages.add(Map.of("role", role, "content", message.content()));
		}
		payload.put("messages", ollamaMessages);
		return payload;
	}

	private AiResponse callOllamaWithRetry(Map<String, Object> payload, String model, int attempt) {
		try {
			Map<String, Object> response = restClient.post()
				.uri("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.body(Map.class);

			String extracted = extractResponseText(response);
			if (extracted != null) {
				return new AiResponse(model, extracted);
			}
		} catch (RestClientException ex) {
			log.error("Unable to reach the local Ollama service at {} for model {}", properties.baseUrl(), model, ex);
			if (attempt <= 1) {
				restartModel(model);
				return callOllamaWithRetry(payload, model, attempt + 1);
			}
			throw new IllegalStateException("Unable to reach the local Ollama service at " + properties.baseUrl(), ex);
		}

		if (attempt <= 1) {
			log.warn("Invalid Ollama response for model {}. Attempting best-effort restart and retry.", model);
			restartModel(model);
			return callOllamaWithRetry(payload, model, attempt + 1);
		}

		return null;
	}

	private String extractResponseText(Map<String, Object> response) {
		if (response == null) {
			return null;
		}

		Object message = response.get("message");
		if (message instanceof Map<?, ?> messageMap) {
			Object content = messageMap.get("content");
			if (content instanceof String contentText && isUsableContent(contentText)) {
				return contentText;
			}
		}

		Object responseText = response.get("response");
		if (responseText instanceof String contentText && isUsableContent(contentText)) {
			return contentText;
		}

		return null;
	}

	private boolean isUsableContent(String content) {
		if (content == null) {
			return false;
		}
		String trimmed = content.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		if (trimmed.length() < 2) {
			return false;
		}
		if (trimmed.chars().anyMatch(ch -> ch == 0)) {
			return false;
		}
		return true;
	}

	private void restartModel(String model) {
		String normalizedModel = (model == null || model.isBlank()) ? properties.defaultModel() : model.trim();
		executeCliCommand("ollama stop " + normalizedModel);
		executeCliCommand("ollama start " + normalizedModel);
	}

	private void executeCliCommand(String command) {
		try {
			Process process = new ProcessBuilder("cmd", "/c", command)
				.redirectErrorStream(true)
				.start();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				log.warn("Ollama restart command exited with status {}: {}", exitCode, command);
			}
		} catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Best-effort restart of Ollama model failed for command: {}", command, ex);
		}
	}

	public String resolveModel(String requestedModel) {
		if (requestedModel != null && !requestedModel.isBlank()) {
			return requestedModel.trim();
		}
		return properties.defaultModel();
	}
}
