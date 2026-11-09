package edu.demo.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.booking.repo.BookingRepository;
import edu.demo.booking.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @BeforeEach void clean() { bookings.deleteAll(); users.deleteAll(); }

    @Test void registrationIssuesRealJwtThatResourceServerAccepts() throws Exception {
        String credentials = "{\"username\":\"new-user\",\"password\":\"MySafePass123\"}";
        MvcResult result = mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER")).andReturn();
        JsonNode token = json.readTree(result.getResponse().getContentAsString());
        assertEquals(3600, token.get("expiresInSeconds").asInt());
        String bearer = "Bearer " + token.get("token").asText();
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/user").header(HttpHeaders.AUTHORIZATION, bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin2\",\"password\":\"SomePass123\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"new-user\",\"password\":\"WrongPass123\"}"))
            .andExpect(status().isUnauthorized());
    }
}
