package com.example.kramjav.service;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
	String baseUrl,
	String defaultModel,
	Boolean rejectRequestsWhenPcActive,
	long activityWindowSeconds,
	Long requestTimeoutSeconds,
	Boolean metricsEnabled,
	String metricsFile
) {

	public OllamaProperties {
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://127.0.0.1:11434" : baseUrl;
		defaultModel = (defaultModel == null || defaultModel.isBlank()) ? "llama3.2" : defaultModel;
		rejectRequestsWhenPcActive = Boolean.TRUE.equals(rejectRequestsWhenPcActive);
		activityWindowSeconds = activityWindowSeconds <= 0 ? 60 : activityWindowSeconds;
		requestTimeoutSeconds = requestTimeoutSeconds == null || requestTimeoutSeconds <= 0 ? 20 : requestTimeoutSeconds;
		metricsEnabled = metricsEnabled != null && metricsEnabled;
		metricsFile = (metricsFile == null || metricsFile.isBlank()) ? defaultMetricsPath() : metricsFile;
	}

	private static String defaultMetricsPath() {
		return Path.of(System.getProperty("user.home"), ".kramjav", "ollama-metrics.json").toString();
	}
}
