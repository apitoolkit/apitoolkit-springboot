package io.apitoolkit.springboot;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import org.junit.Test;

public class UtilsTest {

    @Test
    public void testRedactHeaders() {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        headers.put("Content-Type", "application/json");

        List<String> redactedHeaders = Arrays.asList("Authorization");

        HashMap<String, Object> result = Utils.redactHeaders(headers, redactedHeaders);

        assertEquals("[CLIENT_REDACTED]", result.get("Authorization"));
        assertEquals("application/json", result.get("Content-Type"));
    }

    @Test
    public void testRedactJson() {
        String json = "{\"name\": \"John Doe\", \"password\": \"123456\", \"email\": \"john.doe@example.com\"}";
        byte[] jsonData = json.getBytes(StandardCharsets.UTF_8);

        List<String> jsonPaths = Arrays.asList("$.password");

        byte[] result = Utils.redactJson(jsonData, jsonPaths, false);
        String resultJson = new String(result, StandardCharsets.UTF_8);

        assertTrue(resultJson.contains("\"password\":\"[CLIENT_REDACTED]\""));
        assertTrue(resultJson.contains("\"name\":\"John Doe\""));
        assertTrue(resultJson.contains("\"email\":\"john.doe@example.com\""));
    }

    @Test
    public void testGetPathParamsFromPattern() {
        String pattern = "/users/{userId}/orders/{orderId}";
        String path = "/users/123/orders/456";

        HashMap<String, Object> result = Utils.getPathParamsFromPattern(pattern, path);

        assertEquals("123", result.get("userId"));
        assertEquals("456", result.get("orderId"));
    }

    @Test
    public void testRedactHeadersNullHeaders() {
        HashMap<String, Object> headers = null;
        List<String> redactedHeaders = Arrays.asList("Authorization");

        HashMap<String, Object> result = Utils.redactHeaders(headers, redactedHeaders);

        assertEquals(null, result);
    }

    @Test
    public void testRedactHeadersEmptyHeaders() {
        HashMap<String, Object> headers = new HashMap<>();
        List<String> redactedHeaders = Arrays.asList("Authorization");

        HashMap<String, Object> result = Utils.redactHeaders(headers, redactedHeaders);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testRedactHeadersEmptyRedactedHeaders() {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        headers.put("Content-Type", "application/json");

        List<String> redactedHeaders = Arrays.asList();

        HashMap<String, Object> result = Utils.redactHeaders(headers, redactedHeaders);

        assertEquals("Bearer token", result.get("Authorization"));
        assertEquals("application/json", result.get("Content-Type"));
    }

    @Test
    public void testRedactJsonEmptyData() {
        byte[] jsonData = new byte[0];
        List<String> jsonPaths = Arrays.asList("$.password");

        byte[] result = Utils.redactJson(jsonData, jsonPaths, false);

        assertEquals(0, result.length);
    }

    @Test
    public void testRedactJsonNullJsonPaths() {
        String json = "{\"name\": \"John Doe\", \"password\": \"123456\", \"email\": \"john.doe@example.com\"}";
        byte[] jsonData = json.getBytes(StandardCharsets.UTF_8);

        List<String> jsonPaths = null;

        byte[] result = Utils.redactJson(jsonData, jsonPaths, false);
        String resultJson = new String(result, StandardCharsets.UTF_8);

        assertEquals(json, resultJson);
    }

    @Test
    public void testGetPathParamsFromPatternNoParams() {
        String pattern = "/users/all";
        String path = "/users/all";

        HashMap<String, Object> result = Utils.getPathParamsFromPattern(pattern, path);

        assertTrue(result.isEmpty());
    }
}
