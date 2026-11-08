package com.example.kramjav.web;

import java.util.List;
import java.util.stream.Collectors;

public record AiRequest(String model, String prompt, List<Message> messages, String system) {

	public record Message(String role, String content) {
	}

	public List<Message> normalizedMessages() {
		if (messages != null && !messages.isEmpty()) {
			return messages.stream()
				.filter(message -> message != null)
				.filter(message -> message.content() != null && !message.content().isBlank())
				.collect(Collectors.toList());
		}
		if (prompt != null && !prompt.isBlank()) {
			return List.of(new Message("user", prompt));
		}
		return List.of();
	}
}
