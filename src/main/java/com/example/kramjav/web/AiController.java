package com.example.kramjav.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.kramjav.service.AiResponse;
import com.example.kramjav.service.OllamaAiService;
import com.example.kramjav.service.ServiceBusyException;

@RestController
@RequestMapping("/api/v1")
public class AiController {

	private final OllamaAiService aiService;

	public AiController(OllamaAiService aiService) {
		this.aiService = aiService;
	}

	@PostMapping({"/ai", "/ai/chat", "/ai/response"})
	public ResponseEntity<Map<String, Object>> chat(@RequestBody(required = false) AiRequest request) {
		if (request == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Request body is required."));
		}

		try {
			AiResponse response = aiService.generate(request);
			return ResponseEntity.ok(Map.of(
				"model", response.model(),
				"response", response.response(),
				"provider", "ollama"
			));
		} catch (ServiceBusyException ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
		} catch (IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
		}
	}
}
