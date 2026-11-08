package com.example.kramjav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import com.example.kramjav.service.OllamaProperties;

@SpringBootApplication
@EnableConfigurationProperties(OllamaProperties.class)
public class KramjavApplication {

	public static void main(String[] args) {
		SpringApplication.run(KramjavApplication.class, args);
	}

	@Bean
	RestClient ollamaRestClient(OllamaProperties properties) {
		return RestClient.builder()
			.baseUrl(properties.baseUrl())
			.build();
	}
}
