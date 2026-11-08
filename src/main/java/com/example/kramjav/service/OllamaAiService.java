package com.example.kramjav.service;

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

	public OllamaAiService(RestClient restClient, OllamaProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public AiResponse generate(AiRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("A request body is required.");
		}

		List<AiRequest.Message> messages = request.normalizedMessages();
		if (messages.isEmpty()) {
			throw new IllegalArgumentException("Provide a prompt or at least one message.");
		}

		String model = resolveModel(request.model());
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

		try {
			Map<String, Object> response = restClient.post()
				.uri("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.body(Map.class);

			if (response == null) {
				throw new IllegalStateException("Ollama did not return a response payload.");
			}

			Object message = response.get("message");
			if (message instanceof Map<?, ?> messageMap) {
				Object content = messageMap.get("content");
				if (content instanceof String contentText && !contentText.isBlank()) {
					return new AiResponse(model, contentText);
				}
			}

			Object responseText = response.get("response");
			if (responseText instanceof String contentText && !contentText.isBlank()) {
				return new AiResponse(model, contentText);
			}

			throw new IllegalStateException("Ollama returned an unexpected payload.");
		} catch (RestClientException ex) {
			log.error("Unable to reach the local Ollama service at {}", properties.baseUrl(), ex);
			throw new IllegalStateException("Unable to reach the local Ollama service at " + properties.baseUrl(), ex);
		}
	}

	public String resolveModel(String requestedModel) {
		if (requestedModel != null && !requestedModel.isBlank()) {
			return requestedModel.trim();
		}
		return properties.defaultModel();
	}
}
