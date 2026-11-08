package com.example.kramjav.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatusControllerTests {

	@Test
	void statusEndpointReturnsServiceHealthPayload() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StatusController()).build();

		mockMvc.perform(get("/api/v1/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.service").value("kramjav"))
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}
}
