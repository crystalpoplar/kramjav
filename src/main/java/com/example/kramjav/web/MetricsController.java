package com.example.kramjav.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.kramjav.service.OllamaMetricsRecorder;

@RestController
@RequestMapping("/api/v1")
public class MetricsController {

	private final OllamaMetricsRecorder metricsRecorder;

	public MetricsController(OllamaMetricsRecorder metricsRecorder) {
		this.metricsRecorder = metricsRecorder;
	}

	@GetMapping("/metrics/ollama")
	public Map<String, Object> ollamaMetrics() {
		return metricsRecorder.snapshot();
	}
}
