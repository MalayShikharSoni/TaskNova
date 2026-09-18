package com.tasknova;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testApiLoginSeededAdminWithEmail() throws Exception {
        String json = """
            {
                "email": "admin@tasknova.com",
                "password": "Admin@1234"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(cookie().exists("tasknova_jwt"));
    }

    @Test
    void testApiLoginSeededAdminWithUsername() throws Exception {
        String json = """
            {
                "email": "admin",
                "password": "Admin@1234"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("tasknova_jwt"));
    }

    @Test
    void testApiLoginDemoUserWithUsername() throws Exception {
        String json = """
            {
                "email": "demo_user",
                "password": "Demo@1234"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    void testFormLoginProcessAdminRedirectsToAdmin() throws Exception {
        mockMvc.perform(post("/auth/login-process")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "admin@tasknova.com")
                .param("password", "Admin@1234"))
                .andDo(print())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(cookie().exists("tasknova_jwt"));
    }

    @Test
    void testFormLoginProcessDemoUserRedirectsToDashboard() throws Exception {
        mockMvc.perform(post("/auth/login-process")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "demo_user")
                .param("password", "Demo@1234"))
                .andDo(print())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(cookie().exists("tasknova_jwt"));
    }

    @Test
    void testDashboardAccessWithJwtCookie() throws Exception {
        // 1. Log in via API to get token
        String json = """
            {
                "email": "demo@tasknova.com",
                "password": "Demo@1234"
            }
            """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn();

        Cookie jwtCookie = loginResult.getResponse().getCookie("tasknova_jwt");

        // 2. Request dashboard with that cookie
        mockMvc.perform(get("/dashboard")
                .cookie(jwtCookie))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void testRegisterAndLoginWorkflow() throws Exception {
        String regJson = """
            {
                "username": "tester99",
                "email": "tester99@tasknova.com",
                "password": "Password@123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(regJson))
                .andExpect(status().isCreated());

        // Login with username
        String loginJson = """
            {
                "email": "tester99",
                "password": "Password@123"
            }
            """;

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester99"))
                .andReturn();

        Cookie cookie = loginRes.getResponse().getCookie("tasknova_jwt");

        // Verify can view dashboard
        mockMvc.perform(get("/dashboard")
                .cookie(cookie))
                .andExpect(status().isOk());
    }
}
