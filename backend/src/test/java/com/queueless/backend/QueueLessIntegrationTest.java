package com.queueless.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueless.backend.dto.TokenDto;
import com.queueless.backend.exception.ApiException;
import com.queueless.backend.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Integration test: real Spring context, real security filter chain, in-memory H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueueLessIntegrationTest {

    private static final String PASSWORD = "Passw0rd!x";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired TokenService tokenService;

    // ------------------------------------------------------------ helpers

    private JsonNode send(MockHttpServletRequestBuilder req, String bearer, Object body, int expectedStatus) throws Exception {
        if (bearer != null) req.header("Authorization", "Bearer " + bearer);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        MvcResult r = mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn();
        String text = r.getResponse().getContentAsString();
        return text.isBlank() ? om.createObjectNode() : om.readTree(text);
    }

    private JsonNode registerUser(String label) throws Exception {
        Map<String, String> body = Map.of("fullName", "User " + label,
                "email", label + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local", "password", PASSWORD);
        return send(post("/api/auth/register"), null, body, 201);
    }

    private String adminToken() throws Exception {
        return send(post("/api/auth/login"), null, Map.of("email", "admin@test.local", "password", "Admin#Test123"), 200)
                .get("token").asText();
    }

    private long createService(String admin) throws Exception {
        String name = "Svc-" + UUID.randomUUID().toString().substring(0, 8);
        return send(post("/api/admin/services"), admin, Map.of("name", name, "location", "Test"), 201).get("id").asLong();
    }

    private JsonNode book(String userToken, long serviceId, int expected) throws Exception {
        return send(post("/api/tokens"), userToken, Map.of("serviceId", serviceId), expected);
    }

    // ------------------------------------------------------------ authentication

    @Test
    void registerLoginAndValidation() throws Exception {
        JsonNode reg = registerUser("alice");
        assertEquals("USER", reg.get("user").get("role").asText());
        String email = reg.get("user").get("email").asText();

        // duplicate email
        send(post("/api/auth/register"), null, Map.of("fullName", "Dup", "email", email, "password", PASSWORD), 409);
        // weak password -> field error
        JsonNode bad = send(post("/api/auth/register"), null,
                Map.of("fullName", "Bob", "email", "bob@test.local", "password", "short"), 400);
        assertTrue(bad.get("fieldErrors").has("password"));
        // login ok / wrong password
        send(post("/api/auth/login"), null, Map.of("email", email, "password", PASSWORD), 200);
        send(post("/api/auth/login"), null, Map.of("email", email, "password", "wrong-password"), 401);
    }

    @Test
    void protectedEndpointsNeedLoginAndAdminRole() throws Exception {
        send(get("/api/services"), null, null, 401);
        send(get("/api/tokens/my"), "not-a-real-token", null, 401);
        String user = registerUser("carol").get("token").asText();
        send(get("/api/services"), user, null, 200);
        send(get("/api/admin/stats"), user, null, 403);
        send(post("/api/admin/services"), user, Map.of("name", "Hack"), 403);
        send(get("/api/admin/stats"), adminToken(), null, 200);
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ full queue workflow

    @Test
    void fullTokenLifecycle() throws Exception {
        String admin = adminToken();
        long svc = createService(admin);
        String a = registerUser("a").get("token").asText();
        String b = registerUser("b").get("token").asText();

        JsonNode tokenA = book(a, svc, 201);
        JsonNode tokenB = book(b, svc, 201);
        assertEquals(1, tokenA.get("tokenNumber").asInt());
        assertEquals(2, tokenB.get("tokenNumber").asInt());
        assertEquals("WAITING", tokenA.get("status").asText());
        assertEquals(1, tokenA.get("queuePosition").asInt());
        assertEquals(2, tokenB.get("queuePosition").asInt());
        long idA = tokenA.get("id").asLong();
        long idB = tokenB.get("id").asLong();

        // duplicate active token for the same service is rejected
        book(a, svc, 409);

        // admin calls next -> token A; B moves to position 1
        JsonNode called = send(post("/api/admin/services/" + svc + "/call-next"), admin, null, 200);
        assertEquals(idA, called.get("id").asLong());
        assertEquals("CALLED", called.get("status").asText());
        assertEquals(1, send(get("/api/tokens/" + idB), b, null, 200).get("queuePosition").asInt());
        assertEquals("CALLED", send(get("/api/tokens/" + idA), a, null, 200).get("status").asText());

        // a called token can no longer be cancelled by the user; cannot jump to COMPLETED
        send(post("/api/tokens/" + idA + "/cancel"), a, null, 409);
        send(post("/api/admin/tokens/" + idA + "/complete"), admin, null, 409);

        send(post("/api/admin/tokens/" + idA + "/serving"), admin, null, 200);
        send(post("/api/admin/tokens/" + idA + "/complete"), admin, null, 200);
        send(post("/api/admin/tokens/" + idA + "/complete"), admin, null, 409);   // already final

        // user A may book again once the first token is completed
        JsonNode again = book(a, svc, 201);
        assertEquals(3, again.get("tokenNumber").asInt());

        // user B cancels while waiting; the queue shrinks
        assertEquals("CANCELLED", send(post("/api/tokens/" + idB + "/cancel"), b, null, 200).get("status").asText());
        assertEquals(1, send(get("/api/tokens/" + again.get("id").asLong()), a, null, 200).get("queuePosition").asInt());

        // admin list filters and statistics
        JsonNode completed = send(get("/api/admin/tokens?serviceId=" + svc + "&status=COMPLETED"), admin, null, 200);
        assertEquals(1, completed.size());
        assertEquals(idA, completed.get(0).get("id").asLong());
        JsonNode stats = send(get("/api/admin/stats"), admin, null, 200);
        assertTrue(stats.get("completed").asLong() >= 1);
        assertTrue(stats.get("totalTokens").asLong() >= 3);

        // history contains both of A's tokens
        assertEquals(2, send(get("/api/tokens/my"), a, null, 200).size());
    }

    @Test
    void usersCannotSeeOrChangeOthersTokens() throws Exception {
        String admin = adminToken();
        long svc = createService(admin);
        String owner = registerUser("owner").get("token").asText();
        String other = registerUser("other").get("token").asText();
        long id = book(owner, svc, 201).get("id").asLong();

        send(get("/api/tokens/" + id), other, null, 404);
        send(post("/api/tokens/" + id + "/cancel"), other, null, 404);
        assertEquals(0, send(get("/api/tokens/my"), other, null, 200).size());
    }

    @Test
    void errorsAreHandledSafely() throws Exception {
        String admin = adminToken();
        String user = registerUser("dave").get("token").asText();
        long emptySvc = createService(admin);

        send(post("/api/admin/services/" + emptySvc + "/call-next"), admin, null, 404);   // nobody waiting
        send(post("/api/tokens"), user, Map.of("serviceId", 999999), 404);                 // unknown service
        send(post("/api/tokens"), user, Map.of(), 400);                                     // missing field
        mvc.perform(post("/api/tokens").header("Authorization", "Bearer " + user)
                .contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isBadRequest());
        send(post("/api/admin/tokens/999999/serving"), admin, null, 404);

        // inactive services do not accept tokens
        JsonNode svc = send(get("/api/admin/services"), admin, null, 200);
        assertTrue(svc.size() >= 1);
        send(put("/api/admin/services/" + emptySvc), admin, Map.of("name", "Closed-" + UUID.randomUUID(), "active", false), 200);
        send(post("/api/tokens"), user, Map.of("serviceId", emptySvc), 409);
    }

    // ------------------------------------------------------------ concurrency

    @Test
    void concurrentBookingsGetUniqueSequentialNumbers() throws Exception {
        String admin = adminToken();
        long svc = createService(admin);
        int n = 6;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < n; i++) userIds.add(registerUser("c" + i).get("user").get("id").asLong());

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TokenDto>> futures = new ArrayList<>();
        for (Long uid : userIds) {
            futures.add(pool.submit(() -> {
                start.await();
                return tokenService.book(uid, svc);
            }));
        }
        start.countDown();
        Set<Integer> numbers = new TreeSet<>();
        for (Future<TokenDto> f : futures) numbers.add(f.get(30, TimeUnit.SECONDS).tokenNumber());
        pool.shutdown();

        assertEquals(n, numbers.size(), "every token number must be unique");
        assertEquals(1, Collections.min(numbers));
        assertEquals(n, Collections.max(numbers));
    }

    @Test
    void twoAdminsCannotCallTheSameToken() throws Exception {
        String admin = adminToken();
        long svc = createService(admin);
        long uid = registerUser("solo").get("user").get("id").asLong();
        tokenService.book(uid, svc);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    tokenService.callNext(svc);
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int successes = 0;
        for (Future<Boolean> f : results) if (f.get(30, TimeUnit.SECONDS)) successes++;
        pool.shutdown();

        assertEquals(1, successes, "exactly one admin may call the single waiting token");
    }
}
