package com.example.kramjav.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.example.kramjav.web.AiRequest;

class OllamaAiServiceTests {

	private static OllamaProperties defaultProperties(boolean rejectRequestsWhenPcActive) {
		return new OllamaProperties(
			"http://127.0.0.1:11434",
			"llama3.2",
			rejectRequestsWhenPcActive,
			60,
			20L,
			false,
			Path.of(System.getProperty("user.home"), ".kramjav", "ollama-metrics.json").toString()
		);
	}

	@Test
	void resolveModelUsesExplicitModelWhenProvided() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().build(),
			defaultProperties(true),
			window -> false
		);

		assertEquals("mistral", service.resolveModel("mistral"));
	}

	@Test
	void resolveModelFallsBackToConfiguredDefault() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().build(),
			defaultProperties(true),
			window -> false
		);

		assertEquals("llama3.2", service.resolveModel(null));
		assertEquals("llama3.2", service.resolveModel("   "));
	}

	@Test
	void generateRejectsWhenPcActivityGatingIsEnabled() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().baseUrl("http://127.0.0.1:11434").build(),
			defaultProperties(true),
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
			defaultProperties(false),
			window -> false
		);

		assertThrows(IllegalArgumentException.class, () -> service.generate(new AiRequest(null, null, List.of(new AiRequest.Message("system", "Only system text")), null)));
	}

	@Test
	void generateTreatsBlankRoleAsUser() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().baseUrl("http://127.0.0.1:65535").build(),
			defaultProperties(false),
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
			defaultProperties(false),
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
			defaultProperties(false),
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

	@Test
	void generateRecordsFailureMetrics() throws Exception {
		Path metricsFile = Files.createTempFile("ollama-metrics", ".json");
		OllamaProperties properties = new OllamaProperties(
			"http://127.0.0.1:11434",
			"llama3.2",
			false,
			60,
			20L,
			true,
			metricsFile.toString()
		);
		OllamaMetricsRecorder metricsRecorder = new OllamaMetricsRecorder(properties);
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().build(),
			properties,
			window -> false,
			metricsRecorder
		);

		AiRequest request = new AiRequest(
			null,
			null,
			List.of(new AiRequest.Message("system", "Only system text")),
			null
		);

		assertThrows(IllegalArgumentException.class, () -> service.generate(request));
		String metrics = metricsRecorder.metricsAsJson();
		assertTrue(metrics.contains("\"failedRequests\": 1"));
		assertTrue(metrics.contains("\"invalid-request\": 1"));
	}

	@Test
	void generateInsertsTopLevelSystemWhenFirstMessageIsNotSystem() {
		OllamaAiService service = new OllamaAiService(
			RestClient.builder().baseUrl("http://127.0.0.1:65535").build(),
			defaultProperties(false),
			window -> false
		);

		AiRequest request = new AiRequest(
			null,
			null,
			List.of(
				new AiRequest.Message("user", "Hello"),
				new AiRequest.Message("assistant", "Hi")
			),
			"You are helpful."
		);

		assertThrows(IllegalStateException.class, () -> service.generate(request));
	}
}
