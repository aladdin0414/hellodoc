package com.nopkg.hellodoc.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemInfoControllerTest {
    @Test
    void returnsPublicSystemInformationWithLocalTime() {
        SystemInfoController controller = new SystemInfoController();
        ReflectionTestUtils.setField(controller, "version", "3.6.9");
        LocalDateTime before = LocalDateTime.now().withNano(0);
        var response = controller.getSystemInfo();
        LocalDateTime after = LocalDateTime.now().withNano(0);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull().containsOnlyKeys("name", "version", "status", "serverTime");
        assertThat(body).containsEntry("name", "HelloDoc Server")
                .containsEntry("version", "3.6.9").containsEntry("status", "UP");
        LocalDateTime returned = LocalDateTime.parse((String) body.get("serverTime"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(returned).isBetween(before, after);
    }
    @Test
    // This test isolates the mapping. The running application applies GlobalExceptionHandler to POST.
    void standaloneMappingAcceptsGetAndRejectsPost() throws Exception {
        SystemInfoController controller = new SystemInfoController();
        ReflectionTestUtils.setField(controller, "version", "3.6.9");
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/system/info"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.name").value("HelloDoc Server"))
                .andExpect(jsonPath("$.version").value("3.6.9"))
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(post("/api/system/info")).andExpect(status().isMethodNotAllowed());
    }
}
