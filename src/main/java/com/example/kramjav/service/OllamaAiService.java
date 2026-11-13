package com.example.kramjav.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		List<AiRequest.Message> normalizedForOllama = normalizeMessageRolesForOllama(request, messages);
		validateUserMessages(normalizedForOllama);

		rejectIfPcIsActive();

		String model = resolveModel(request.model());
		Map<String, Object> payload = buildPayload(model, normalizedForOllama);

		AiResponse response = callOllamaWithRetry(payload, model, 1);
		if (response != null) {
			return response;
		}

		throw new IllegalStateException("Ollama returned an invalid payload for model '" + model + "'.");
	}

	private void validateUserMessages(List<AiRequest.Message> messages) {
		boolean hasUserMessage = messages.stream()
			.anyMatch(message -> "user".equals(normalizeRole(message.role())) && message.content() != null && !message.content().isBlank());
		if (!hasUserMessage) {
			throw new IllegalArgumentException("Request must contain at least one user message with content.");
		}
	}

	private List<AiRequest.Message> normalizeMessageRolesForOllama(AiRequest request, List<AiRequest.Message> messages) {
		AiRequest.Message firstMessage = messages.get(0);
		if (!"system".equals(normalizeRole(firstMessage.role()))) {
			String payloadSummary = summarizeRequestPayload(request, messages);
			log.warn("Rejected request because messages[0] is not a system message. Payload: {}", payloadSummary);
			throw new IllegalArgumentException("Request must include a system message at messages[0].");
		}

		List<AiRequest.Message> normalized = new ArrayList<>();
		String firstSystemContent = firstMessage.content();
		if (request.system() != null && !request.system().isBlank()) {
			firstSystemContent = request.system().trim() + "\n\n" + firstSystemContent;
		}
		normalized.add(new AiRequest.Message("system", firstSystemContent));

		boolean transformedOutOfOrderSystemMessage = false;
		for (int i = 1; i < messages.size(); i++) {
			AiRequest.Message message = messages.get(i);
			String role = normalizeRole(message.role());
			if ("system".equals(role)) {
				transformedOutOfOrderSystemMessage = true;
				normalized.add(new AiRequest.Message("user", "System: " + message.content()));
				continue;
			}
			normalized.add(new AiRequest.Message(role, message.content()));
		}

		if (transformedOutOfOrderSystemMessage) {
			log.warn(
				"Converted out-of-order system messages to user messages for Ollama compatibility. Original payload: {}. Normalized payload: {}",
				summarizeRequestPayload(request, messages),
				summarizeMessages(normalized)
			);
		}

		return normalized;
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

	private Map<String, Object> buildPayload(String model, List<AiRequest.Message> messages) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("model", model);
		payload.put("stream", false);

		List<Map<String, String>> ollamaMessages = new ArrayList<>();
		for (AiRequest.Message message : messages) {
			String role = normalizeRole(message.role());
			ollamaMessages.add(Map.of("role", role, "content", message.content()));
		}
		payload.put("messages", ollamaMessages);
		return payload;
	}

	private String normalizeRole(String role) {
		if (role == null || role.isBlank()) {
			return "user";
		}
		String normalized = role.trim().toLowerCase();
		if ("assistant".equals(normalized) || "tool".equals(normalized) || "system".equals(normalized) || "user".equals(normalized)) {
			return normalized;
		}
		return "user";
	}

	private AiResponse callOllamaWithRetry(Map<String, Object> payload, String model, int attempt) {
		String payloadSummary = summarizePayload(payload);
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
			log.error("Ollama request failed for model {}. Triggering message payload: {}. Error: {}", model, payloadSummary, ex.getMessage(), ex);
			if (isMalformedRequestError(ex)) {
				throw new IllegalArgumentException("Ollama rejected the request because it did not contain a valid user prompt. " + payloadSummary, ex);
			}
			if (attempt <= 1) {
				restartModel(model);
				return callOllamaWithRetry(payload, model, attempt + 1);
			}
			throw new IllegalStateException("Unable to reach the local Ollama service at " + properties.baseUrl(), ex);
		}

		if (attempt <= 1) {
			log.warn("Invalid Ollama response for model {}. Triggering message payload: {}. Attempting best-effort restart and retry.", model, payloadSummary);
			restartModel(model);
			return callOllamaWithRetry(payload, model, attempt + 1);
		}

		return null;
	}

	private boolean isMalformedRequestError(RestClientException ex) {
		String message = ex.getMessage();
		if (message == null) {
			return false;
		}
		String lower = message.toLowerCase();
		return lower.contains("no user query found in messages")
			|| lower.contains("jinja exception")
			|| lower.contains("invalid request")
			|| lower.contains("missing user message")
			|| lower.contains("system message must be first");
	}

	private String summarizePayload(Map<String, Object> payload) {
		if (payload == null) {
			return "<empty payload>";
		}
		Object messages = payload.get("messages");
		if (messages instanceof List<?> list) {
			return list.toString();
		}
		return payload.toString();
	}

	private String summarizeRequestPayload(AiRequest request, List<AiRequest.Message> messages) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("model", request.model());
		payload.put("system", request.system());
		payload.put("messages", summarizeMessages(messages));
		return payload.toString();
	}

	private String summarizeMessages(List<AiRequest.Message> messages) {
		List<Map<String, String>> summarized = new ArrayList<>();
		for (AiRequest.Message message : messages) {
			summarized.add(Map.of(
				"role", normalizeRole(message.role()),
				"content", message.content()
			));
		}
		return summarized.toString();
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
