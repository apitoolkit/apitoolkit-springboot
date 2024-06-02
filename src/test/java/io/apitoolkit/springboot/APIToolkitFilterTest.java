package io.apitoolkit.springboot;

import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class APIToolkitFilterTest {
    private HashMap<String, String> filterConfig;

    private APIToolkitFilter apiToolkitFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Test
    public void testDoFilter() throws IOException, ServletException, Exception {
        apiToolkitFilter = new APIToolkitFilter();
        filterConfig = new HashMap<>();
        filterConfig.put("apitoolkit.debug", "false");
        filterConfig.put("apitoolkit.redactHeaders", "cookies,authorization,x-api");
        filterConfig.put("apitoolkit.redactRequestBody", "$.password,$.email");
        filterConfig.put("apitoolkit.redactResponseBody", "$.password,$.email");
        filterConfig.put("apitoolkit.rootUrl", "https://app.apitoolkit.io");
        filterConfig.put("apitoolkit.apikey", "z6EYf5FMa3gzzNUfgKZsHjtN9GLETNaev7/v0LkNozFQ89nH");

        standaloneSetup(new TestController()).addFilter(apiToolkitFilter, "APIToolkitFilter", filterConfig, null, "*")
                .build()
                .perform(get("/"))
                .andExpect(status().isOk());

    }

    @Controller
    private static class TestController {
        @GetMapping("/")
        public String get(HttpServletRequest request) {
            assertEquals(request.getMethod(), "GET");
            return "got it";
        }
    }
}
