package com.example.kramjav.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OllamaAiServiceTests {

	@Test
	void resolveModelUsesExplicitModelWhenProvided() {
		OllamaAiService service = new OllamaAiService(RestClient.builder().build(), new OllamaProperties("http://127.0.0.1:11434", "llama3.2"));

		assertEquals("mistral", service.resolveModel("mistral"));
	}

	@Test
	void resolveModelFallsBackToConfiguredDefault() {
		OllamaAiService service = new OllamaAiService(RestClient.builder().build(), new OllamaProperties("http://127.0.0.1:11434", "llama3.2"));

		assertEquals("llama3.2", service.resolveModel(null));
		assertEquals("llama3.2", service.resolveModel("   "));
	}
}
