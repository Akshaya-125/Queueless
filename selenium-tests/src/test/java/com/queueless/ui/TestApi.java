package com.queueless.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

/**
 * Sets up test data through the REST API (fast and independent of the UI), so each UI test
 * starts from a known state: its own user and its own service counter.
 */
final class TestApi {

    static final String BASE_URL = env("BASE_URL", "http://localhost:8080");
    static final String ADMIN_EMAIL = env("ADMIN_EMAIL", "admin@queueless.local");
    static final String ADMIN_PASSWORD = System.getenv("ADMIN_PASSWORD");
    static final String USER_PASSWORD = "Selenium#Pass123";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    record Account(String email, String token) { }

    private TestApi() { }

    static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    static JsonNode call(String method, String path, String token, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE_URL + "/api" + path))
                    .header("Content-Type", "application/json");
            if (token != null) b.header("Authorization", "Bearer " + token);
            HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
            HttpResponse<String> res = HTTP.send(b.method(method, pub).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                throw new IllegalStateException(method + " " + path + " -> " + res.statusCode() + " " + res.body());
            }
            return res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException("API call failed: " + method + " " + path, e);
        }
    }

    static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static Account registerUser() {
        String email = unique("selenium") + "@test.local";
        JsonNode res = call("POST", "/auth/register", null,
                Map.of("fullName", "Selenium User", "email", email, "password", USER_PASSWORD));
        return new Account(email, res.get("token").asText());
    }

    static String adminToken() {
        if (ADMIN_PASSWORD == null || ADMIN_PASSWORD.isBlank()) {
            throw new IllegalStateException("Set the ADMIN_PASSWORD environment variable (same value as in your .env)");
        }
        return call("POST", "/auth/login", null, Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).get("token").asText();
    }

    static long createService(String adminToken) {
        return call("POST", "/admin/services", adminToken,
                Map.of("name", unique("UI-Service"), "location", "Selenium")).get("id").asLong();
    }

    static JsonNode book(String userToken, long serviceId) {
        return call("POST", "/tokens", userToken, Map.of("serviceId", serviceId));
    }

    static JsonNode getToken(String userToken, long tokenId) {
        return call("GET", "/tokens/" + tokenId, userToken, null);
    }
}
