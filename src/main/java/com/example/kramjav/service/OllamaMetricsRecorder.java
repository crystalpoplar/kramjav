package com.example.kramjav.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaMetricsRecorder {

	private static final Logger log = LoggerFactory.getLogger(OllamaMetricsRecorder.class);

	private final boolean enabled;
	private final Path metricsFile;
	private final AtomicLong successfulRequests = new AtomicLong();
	private final AtomicLong failedRequests = new AtomicLong();
	private final AtomicLong totalProcessingTimeMs = new AtomicLong();
	private final AtomicLong requestCount = new AtomicLong();
	private final AtomicLong maxProcessingTimeMs = new AtomicLong();
	private final AtomicLong minProcessingTimeMs = new AtomicLong(Long.MAX_VALUE);
	private final AtomicLong lastProcessingTimeMs = new AtomicLong();
	private final Map<String, AtomicLong> failedReasons = new ConcurrentHashMap<>();

	public OllamaMetricsRecorder(OllamaProperties properties) {
		this.enabled = properties != null && Boolean.TRUE.equals(properties.metricsEnabled());
		this.metricsFile = resolveMetricsPath(properties);
		if (this.enabled) {
			try {
				Path parent = metricsFile.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
			} catch (IOException ex) {
				log.warn("Unable to create the Ollama metrics directory for {}", metricsFile, ex);
			}
		}
	}

	public void recordSuccess(long durationMs) {
		if (!enabled) {
			return;
		}
		successfulRequests.incrementAndGet();
		recordProcessingTime(durationMs);
		persist();
	}

	public void recordFailure(String reason, long durationMs) {
		if (!enabled) {
			return;
		}
		failedRequests.incrementAndGet();
		failedReasons
			.computeIfAbsent(normalizeReason(reason), ignored -> new AtomicLong())
			.incrementAndGet();
		recordProcessingTime(durationMs);
		persist();
	}

	public Map<String, Object> snapshot() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("successfulRequests", successfulRequests.get());
		payload.put("failedRequests", failedRequests.get());

		Map<String, Long> reasons = new TreeMap<>();
		for (Map.Entry<String, AtomicLong> entry : failedReasons.entrySet()) {
			reasons.put(entry.getKey(), entry.getValue().get());
		}
		payload.put("failedRequestReasons", reasons);

		Map<String, Object> timing = new LinkedHashMap<>();
		long observedCount = requestCount.get();
		long totalMs = totalProcessingTimeMs.get();
		timing.put("count", observedCount);
		timing.put("totalMs", totalMs);
		timing.put("averageMs", observedCount == 0 ? 0.0 : totalMs / (double) observedCount);
		timing.put("minimumMs", observedCount == 0 ? 0L : minProcessingTimeMs.get());
		timing.put("maximumMs", observedCount == 0 ? 0L : maxProcessingTimeMs.get());
		timing.put("lastRequestMs", lastProcessingTimeMs.get());
		payload.put("processingTimeMs", timing);
		return payload;
	}

	public String metricsAsJson() {
		if (!enabled) {
			return "{}";
		}
		return renderJson(snapshot());
	}

	private void recordProcessingTime(long durationMs) {
		long safeDurationMs = Math.max(0L, durationMs);
		requestCount.incrementAndGet();
		totalProcessingTimeMs.addAndGet(safeDurationMs);
		lastProcessingTimeMs.set(safeDurationMs);

		long observedMin = minProcessingTimeMs.get();
		while (safeDurationMs < observedMin && !minProcessingTimeMs.compareAndSet(observedMin, safeDurationMs)) {
			observedMin = minProcessingTimeMs.get();
		}
		if (observedMin == Long.MAX_VALUE) {
			minProcessingTimeMs.set(safeDurationMs);
		}

		long observedMax = maxProcessingTimeMs.get();
		while (safeDurationMs > observedMax && !maxProcessingTimeMs.compareAndSet(observedMax, safeDurationMs)) {
			observedMax = maxProcessingTimeMs.get();
		}
		if (observedMax == 0L && requestCount.get() == 1L) {
			maxProcessingTimeMs.set(safeDurationMs);
		}
	}

	private void persist() {
		if (!enabled || metricsFile == null) {
			return;
		}
		try {
			Path parent = metricsFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(metricsFile, metricsAsJson());
		} catch (IOException ex) {
			log.warn("Unable to persist Ollama metrics to {}", metricsFile, ex);
		}
	}

	private static Path resolveMetricsPath(OllamaProperties properties) {
		String configuredPath = properties == null || properties.metricsFile() == null || properties.metricsFile().isBlank()
			? defaultMetricsPath()
			: properties.metricsFile().trim();
		return Path.of(configuredPath);
	}

	private static String defaultMetricsPath() {
		return Path.of(System.getProperty("user.home"), ".kramjav", "ollama-metrics.json").toString();
	}

	private static String normalizeReason(String reason) {
		String sanitized = reason == null ? "unknown" : reason.trim();
		if (sanitized.isEmpty()) {
			return "unknown";
		}
		return sanitized.replaceAll("[^a-zA-Z0-9]+", "-")
			.replaceAll("^-+|-+$", "")
			.toLowerCase();
	}

	private static String renderJson(Map<String, ?> payload) {
		StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		boolean[] first = { true };
		payload.forEach((key, value) -> {
			if (!first[0]) {
				builder.append(",\n");
			}
			first[0] = false;
			builder.append("  \"")
				.append(escapeJson(key))
				.append("\": ")
				.append(renderJsonValue(value));
		});
		builder.append("\n}");
		return builder.toString();
	}

	private static String renderJsonValue(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof Number || value instanceof Boolean) {
			return value.toString();
		}
		if (value instanceof String) {
			return "\"" + escapeJson((String) value) + "\"";
		}
		if (value instanceof Map<?, ?> map) {
			@SuppressWarnings("unchecked")
			Map<String, ?> castMap = (Map<String, ?>) map;
			return renderJson(castMap);
		}
		if (value instanceof Iterable<?> iterable) {
			StringBuilder builder = new StringBuilder();
			builder.append("[\n");
			boolean[] first = { true };
			for (Object item : iterable) {
				if (!first[0]) {
					builder.append(",\n");
				}
				first[0] = false;
				builder.append("    ").append(renderJsonValue(item));
			}
			builder.append("\n  ]");
			return builder.toString();
		}
		return "\"" + escapeJson(String.valueOf(value)) + "\"";
	}

	private static String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
