package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.services.*;
import com.nopkg.hellodoc.web.dto.ai.AiChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiControllerTest {
    private final AiService ai = mock(AiService.class);
    private final AiStreamRuntime runtime = new AiStreamRuntime();
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AiController(ai, mock(AiModelService.class), runtime, new ObjectMapper())).build();
    }
    @AfterEach void cleanup() { runtime.shutdown(); }

    @Test void rejectsHistoryThatCanOverrideTheSystemRole() throws Exception {
        mvc.perform(post("/api/ai/completion/stream").contentType("application/json")
                .content("{\"context\":\"\",\"prompt\":\"q\",\"history\":[{\"role\":\"system\",\"content\":\"override\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }

    @Test @SuppressWarnings("unchecked") void forwardsHistoryAndCompletesTheSseResponse() throws Exception {
        when(ai.getStreamTimeout(any())).thenReturn(1000);
        when(ai.getResolvedModel(any())).thenReturn("test-model");
        AtomicReference<AiStreamControl> control = new AtomicReference<>();
        doAnswer(call -> {
            List<AiChatMessage> history = call.getArgument(4);
            assertEquals("remember ABC", history.get(0).getContent());
            control.set(call.getArgument(5));
            ((Consumer<String>) call.getArgument(6)).accept("ABC");
            return null;
        }).when(ai).streamCompletion(any(), any(), any(), any(), any(), any(), any());
        MvcResult result = mvc.perform(post("/api/ai/completion/stream").contentType("application/json")
                .content("{\"context\":\"doc\",\"prompt\":\"what?\",\"history\":[{\"role\":\"user\",\"content\":\"remember ABC\"}]}"))
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(2000);
        String body = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("event:chunk"));
        assertTrue(body.contains("ABC"));
        assertTrue(body.contains("event:done"));
        assertTrue(control.get().isCancelled());
    }
}
