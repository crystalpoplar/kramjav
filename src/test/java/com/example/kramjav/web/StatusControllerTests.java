package com.example.kramjav.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatusController.class)
class StatusControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void statusEndpointReturnsServiceHealthPayload() throws Exception {
		mockMvc.perform(get("/api/v1/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.service").value("kramjav"))
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}
}
