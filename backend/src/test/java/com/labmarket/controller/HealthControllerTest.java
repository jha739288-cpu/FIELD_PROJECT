package com.labmarket.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.labmarket.security.CustomUserDetailsService;
import com.labmarket.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Security filters are disabled here on purpose: this slice verifies the
// controller contract only. The @MockBeans below exist solely so the scanned
// JwtAuthenticationFilter can be constructed. The REAL chain (public
// /api/v1/health, 401/403 behaviour) is asserted in AuthIntegrationTest.
@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class HealthControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private JwtService jwtService;
  @MockBean private CustomUserDetailsService userDetailsService;

  @Test
  void healthIsPublicAndReturnsUp() throws Exception {
    mvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.service").value("labmarket-backend"))
        .andExpect(jsonPath("$.timestamp").exists());
  }
}
