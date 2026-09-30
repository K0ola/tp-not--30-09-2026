package com.fitconnect.classservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.classservice.repository.FitnessClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FitnessClassControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FitnessClassRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private String classJson(String name, String category, String level, int max, String location) {
        String date = LocalDateTime.now().plusDays(3).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return """
                {
                  "name": "%s",
                  "description": "Un super cours",
                  "instructor": "Marie Dupont",
                  "gymLocation": "%s",
                  "category": "%s",
                  "level": "%s",
                  "durationMinutes": 60,
                  "maxParticipants": %d,
                  "price": 25.00,
                  "dateTime": "%s"
                }
                """.formatted(name, location, category, level, max, date);
    }

    private long createClass(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.currentParticipants").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    @Test
    void shouldCreateAndFetchClass() throws Exception {
        long id = createClass(classJson("Yoga Vinyasa", "YOGA", "BEGINNER", 10, "Paris"));

        mockMvc.perform(get("/api/classes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yoga Vinyasa"))
                .andExpect(jsonPath("$.availableSpots").value(10))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldRejectInvalidClass() throws Exception {
        String invalid = classJson("Yo", "YOGA", "BEGINNER", 10, "Paris"); // nom trop court
        mockMvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        String badDuration = classJson("Yoga", "YOGA", "BEGINNER", 10, "Paris").replace("\"durationMinutes\": 60", "\"durationMinutes\": 50");
        mockMvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON).content(badDuration))
                .andExpect(status().isBadRequest());

        String badCategory = classJson("Yoga", "SWIMMING", "BEGINNER", 10, "Paris");
        mockMvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON).content(badCategory))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFilterAndPaginate() throws Exception {
        createClass(classJson("Yoga Matin", "YOGA", "BEGINNER", 10, "Paris"));
        createClass(classJson("Yoga Soir", "YOGA", "INTERMEDIATE", 10, "Lyon"));
        createClass(classJson("CrossFit WOD", "CROSSFIT", "ADVANCED", 10, "Paris"));

        mockMvc.perform(get("/api/classes").param("category", "YOGA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mockMvc.perform(get("/api/classes").param("category", "YOGA").param("level", "INTERMEDIATE"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Yoga Soir"));

        mockMvc.perform(get("/api/classes").param("location", "paris").param("instructor", "Marie"))
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/classes").param("page", "0").param("size", "2").param("sort", "dateTime,asc"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        String day = LocalDateTime.now().plusDays(3).toLocalDate().toString();
        mockMvc.perform(get("/api/classes/search").param("date", day).param("location", "Paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/classes").param("dateFrom", "2000-01-01").param("dateTo", "2000-01-02"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void shouldIncrementThenRefuseOverbooking() throws Exception {
        long id = createClass(classJson("Boxe", "BOXING", "BEGINNER", 5, "Paris"));

        mockMvc.perform(patch("/api/classes/{id}/increment", id).param("spots", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(4))
                .andExpect(jsonPath("$.availableSpots").value(1))
                .andExpect(jsonPath("$.version").value(1));

        // 4 + 2 > 5 -> 409 Conflict, state unchanged
        mockMvc.perform(patch("/api/classes/{id}/increment", id).param("spots", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Plus de places disponibles pour ce cours"));

        mockMvc.perform(get("/api/classes/{id}", id))
                .andExpect(jsonPath("$.currentParticipants").value(4));

        mockMvc.perform(patch("/api/classes/{id}/decrement", id).param("spots", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(1));

        mockMvc.perform(patch("/api/classes/{id}/increment", id).param("spots", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404ForUnknownClass() throws Exception {
        mockMvc.perform(get("/api/classes/{id}", 9999))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/classes/{id}/increment", 9999).param("spots", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesEmptyClassAndCancelsBookedClass() throws Exception {
        long emptyId = createClass(classJson("Pilates", "PILATES", "BEGINNER", 10, "Paris"));
        mockMvc.perform(delete("/api/classes/{id}", emptyId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/classes/{id}", emptyId)).andExpect(status().isNotFound());

        long bookedId = createClass(classJson("Spinning", "SPINNING", "BEGINNER", 10, "Paris"));
        mockMvc.perform(patch("/api/classes/{id}/increment", bookedId).param("spots", "2"));
        mockMvc.perform(delete("/api/classes/{id}", bookedId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/classes/{id}", bookedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // un cours annule n'accepte plus de reservation
        mockMvc.perform(patch("/api/classes/{id}/increment", bookedId).param("spots", "1"))
                .andExpect(status().isConflict());
    }
}
