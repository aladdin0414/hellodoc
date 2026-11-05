package com.nopkg.hellodoc.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.config.AiProperties;
import com.nopkg.hellodoc.repositories.AiModelRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiDefaultConfigurationTest {
    @Test void reportsWhetherFallbackFieldsArePresent() {
        var properties = new AiProperties();
        var config = mock(ConfigService.class);
        var runtime = new AiStreamRuntime();
        try {
            var service = new AiService(properties, config, mock(AiModelRepository.class), new ObjectMapper(), runtime);
            assertFalse(service.isDefaultConfigured());
            properties.setApiKey("test-key");
            assertTrue(service.isDefaultConfigured());
            when(config.getConfigValue("ai.openai.base-url")).thenReturn(" ");
            properties.setBaseUrl("");
            assertFalse(service.isDefaultConfigured());
        } finally { runtime.shutdown(); }
    }
}
