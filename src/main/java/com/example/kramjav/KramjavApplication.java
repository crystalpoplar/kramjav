package com.example.kramjav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) java.util.concurrent.TimeUnit.SECONDS.toMillis(properties.requestTimeoutSeconds()));
		requestFactory.setReadTimeout((int) java.util.concurrent.TimeUnit.SECONDS.toMillis(properties.requestTimeoutSeconds()));

		return RestClient.builder()
			.baseUrl(properties.baseUrl())
			.requestFactory(requestFactory)
			.build();
	}
}
