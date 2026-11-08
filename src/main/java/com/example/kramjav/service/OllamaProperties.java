package com.example.kramjav.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(String baseUrl, String defaultModel) {

	public OllamaProperties {
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://127.0.0.1:11434" : baseUrl;
		defaultModel = (defaultModel == null || defaultModel.isBlank()) ? "llama3.2" : defaultModel;
	}
}
