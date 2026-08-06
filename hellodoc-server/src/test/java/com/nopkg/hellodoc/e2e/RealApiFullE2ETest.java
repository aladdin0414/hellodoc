package com.nopkg.hellodoc.e2e;

import com.nopkg.hellodoc.web.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires a real server running on http://localhost:8080")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RealApiFullE2ETest {

        private final String BASE_URL = "http://localhost:8080";
        private final RestTemplate restTemplate = new RestTemplate();

        private String adminToken;
        private String userToken;
        private Long kbId;
        private Long docId;
        private String username = "e2e_user_" + System.currentTimeMillis();

        @Test
        @Order(1)
        @DisplayName("Step 1: Admin Login & System Config Check")
        void step1_adminFlow() {
                Map<String, String> loginReq = new HashMap<>();
                loginReq.put("username", "admin");
                loginReq.put("password", "admin123");

                ApiResponse<Map<String, Object>> res = restTemplate.exchange(
                                BASE_URL + "/api/auth/login", HttpMethod.POST, new HttpEntity<>(loginReq),
                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                                }).getBody();

                assertNotNull(res);
                assertEquals(0, res.getCode());
                adminToken = (String) res.getData().get("accessToken");
                assertNotNull(adminToken);

                ApiResponse<Object> dictRes = restTemplate.exchange(
                                BASE_URL + "/api/public/configs/frontend", HttpMethod.GET, null,
                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<Object>>() {
                                }).getBody();
                assertNotNull(dictRes);
                assertEquals(0, dictRes.getCode());
        }

        @Test
        @Order(2)
        @DisplayName("Step 2: User Register & Login")
        void step2_userAuthFlow() {
                Map<String, String> regReq = new HashMap<>();
                regReq.put("username", username);
                regReq.put("password", "111111");
                regReq.put("nickname", "E2E User");

                ApiResponse<Object> regRes = restTemplate.exchange(
                                BASE_URL + "/api/auth/register", HttpMethod.POST, new HttpEntity<>(regReq),
                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<Object>>() {
                                }).getBody();
                assertNotNull(regRes);
                assertEquals(0, regRes.getCode());

                Map<String, String> loginReq = new HashMap<>();
                loginReq.put("username", username);
                loginReq.put("password", "111111");

                ApiResponse<Map<String, Object>> loginRes = restTemplate.exchange(
                                BASE_URL + "/api/auth/login", HttpMethod.POST, new HttpEntity<>(loginReq),
                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                                }).getBody();
                assertNotNull(loginRes);
                assertEquals(0, loginRes.getCode());
                userToken = (String) loginRes.getData().get("accessToken");
                assertNotNull(userToken);
        }

        @Test
        @Order(3)
        @DisplayName("Step 3: KB & Document Creation")
        void step3_contentFlow() {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(userToken);
                headers.setContentType(MediaType.APPLICATION_JSON);

                // Explicitly using lowercase "private" to avoid enum serialization issues
                String kbJson = String.format("{\"title\":\"E2E KB %s\", \"visibility\":\"private\"}", username);

                HttpEntity<String> entity = new HttpEntity<>(kbJson, headers);
                ApiResponse<Map<String, Object>> kbRes = restTemplate.exchange(
                                BASE_URL + "/api/kb/createKnowledgeBase", HttpMethod.POST, entity,
                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                                }).getBody();

                assertNotNull(kbRes);
                if (kbRes.getCode() != 0) {
                        System.err.println("KB Create Failed: " + kbRes.getMessage());
                }
                assertEquals(0, kbRes.getCode());
                kbId = Long.valueOf(kbRes.getData().get("id").toString());

                Map<String, String> docReq = new HashMap<>();
                docReq.put("name", "E2E Doc");
                docReq.put("type", "file");
                docReq.put("content", "Initial Content [[link]]");

                HttpEntity<Map<String, String>> docEntity = new HttpEntity<>(docReq, headers);
                ApiResponse<Map<String, Object>> docRes = restTemplate.exchange(
                                BASE_URL + "/api/kb/" + kbId + "/documents", HttpMethod.POST, docEntity,
                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                                }).getBody();
                if (docRes.getCode() != 0) {
                        System.err.println("Doc Create Failed: " + docRes.getMessage());
                }
                assertEquals(0, docRes.getCode());
                docId = Long.valueOf(docRes.getData().get("id").toString());
        }

        @Test
        @Order(4)
        @DisplayName("Step 4: Update, Revision & Cleanup")
        void step4_revisionAndCleanup() {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(userToken);
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, String> updateReq = new HashMap<>();
                updateReq.put("content", "Updated Content");
                HttpEntity<Map<String, String>> updateEntity = new HttpEntity<>(updateReq, headers);
                restTemplate.exchange(BASE_URL + "/api/kb/" + kbId + "/documents/" + docId, HttpMethod.PUT,
                                updateEntity,
                                ApiResponse.class);

                Map<String, String> revReq = new HashMap<>();
                revReq.put("content", "Revision Content");
                revReq.put("message", "v1.0");
                HttpEntity<Map<String, String>> revEntity = new HttpEntity<>(revReq, headers);
                ApiResponse<Map<String, Object>> revRes = restTemplate.exchange(
                                BASE_URL + "/api/kb/" + kbId + "/documents/" + docId + "/revisions", HttpMethod.POST,
                                revEntity,
                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                                }).getBody();
                assertNotNull(revRes);
                assertEquals(0, revRes.getCode());

                restTemplate.exchange(BASE_URL + "/api/kb/" + kbId, HttpMethod.DELETE, new HttpEntity<>(headers),
                                ApiResponse.class);
        }
}
