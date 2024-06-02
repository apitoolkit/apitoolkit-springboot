package io.apitoolkit.springboot.integrations;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public class ObserveRequest {
    private List<String> redactHeaders;
    private List<String> redactRequestBody;
    private List<String> redactResponseBody;

    // Primary constructor
    public ObserveRequest(List<String> redactHeaders,
            List<String> redactRequestBody,
            List<String> redactResponseBody) {
        this.redactHeaders = redactHeaders;
        this.redactRequestBody = redactRequestBody;
        this.redactResponseBody = redactResponseBody;
    }

    public ObserveRequest() {
        this(null, null, null);
    }

    public ObserveRequest(List<String> redactHeaders) {
        this(redactHeaders, null, null);
    }

    public ObserveRequest(List<String> redactHeaders,
            List<String> redactRequestBody) {
        this(redactHeaders, redactRequestBody, null);
    }

    public CloseableHttpClient createHttpClient(HttpServletRequest request, String urlPathPattern) {
        return HttpClients.custom()
                .addInterceptorFirst(new RequestInterceptor())
                .addInterceptorFirst(new ResponseInterceptor(request, urlPathPattern, this.redactHeaders,
                        this.redactRequestBody, this.redactResponseBody))
                .build();
    }

    public CloseableHttpClient createHttpClient(HttpServletRequest request) {
        return createHttpClient(request, null);
    }
}
