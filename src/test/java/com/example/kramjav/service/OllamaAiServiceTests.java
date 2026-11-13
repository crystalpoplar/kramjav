package com.example.kramjav.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.example.kramjav.web.AiRequest;

class OllamaAiServiceTests {

	@Test
	void resolveModelUsesExplicitModelWhenProvided() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().build(),
			new OllamaProperties("http://127.0.0.1:11434", "llama3.2", true, 60, 20L),
			window -> false
		);

		assertEquals("mistral", service.resolveModel("mistral"));
	}

	@Test
	void resolveModelFallsBackToConfiguredDefault() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().build(),
			new OllamaProperties("http://127.0.0.1:11434", "llama3.2", true, 60, 20L),
			window -> false
		);

		assertEquals("llama3.2", service.resolveModel(null));
		assertEquals("llama3.2", service.resolveModel("   "));
	}

	@Test
	void generateRejectsWhenPcActivityGatingIsEnabled() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().baseUrl("http://127.0.0.1:11434").build(),
			new OllamaProperties("http://127.0.0.1:11434", "llama3.2", true, 60, 20L),
			window -> true
		);

		AiRequest request = new AiRequest(
			null,
			null,
			List.of(
				new AiRequest.Message("system", "You are helpful."),
				new AiRequest.Message("user", "Hello")
			),
			null
		);

		assertThrows(ServiceBusyException.class, () -> service.generate(request));
	}

	@Test
	void generateRejectsRequestsWithoutUserMessages() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().build(),
			new OllamaProperties("http://127.0.0.1:11434", "llama3.2", false, 60, 20L),
			window -> false
		);

		assertThrows(IllegalArgumentException.class, () -> service.generate(new AiRequest(null, null, List.of(new AiRequest.Message("system", "Only system text")), null)));
	}

	@Test
	void generateTreatsBlankRoleAsUser() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().baseUrl("http://127.0.0.1:65535").build(),
			new OllamaProperties("http://127.0.0.1:11434", "llama3.2", false, 60, 20L),
			window -> false
		);

		AiRequest request = new AiRequest(
			null,
			null,
			List.of(
				new AiRequest.Message("system", "Context"),
				new AiRequest.Message(" ", "Are you working?")
			),
			null
		);

		assertThrows(IllegalStateException.class, () -> service.generate(request));
	}

	@Test
	void generateAllowsRequestsWhenPcActivityGatingIsDisabled() {
		PcActivityDetector detector = window -> {
			assertEquals(Duration.ofSeconds(60), window);
			return true;
		};
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().baseUrl("http://127.0.0.1:65535").build(),
			new OllamaProperties("http://127.0.0.1:11434", "llama3.2", false, 60, 20L),
			detector
		);

		AiRequest request = new AiRequest(
			null,
			null,
			List.of(
				new AiRequest.Message("system", "You are helpful."),
				new AiRequest.Message("user", "Hello")
			),
			null
		);

		assertThrows(IllegalStateException.class, () -> service.generate(request));
	}

	@Test
	void generateRejectsWhenFirstMessageIsNotSystem() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().build(),
			new OllamaProperties("http://127.0.0.1:11434", "llama3.2", false, 60, 20L),
			window -> false
		);

		AiRequest request = new AiRequest(
			null,
			null,
			List.of(
				new AiRequest.Message("user", "Hello"),
				new AiRequest.Message("assistant", "Hi")
			),
			null
		);

		assertThrows(IllegalArgumentException.class, () -> service.generate(request));
	}
}
