package io.apitoolkit.springboot;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import org.springframework.web.servlet.HandlerMapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.Credentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.jayway.jsonpath.JsonPath;

import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import org.springframework.beans.factory.annotation.Value;

public class APIToolkitFilter implements Filter {
    private Publisher pubsubClient;
    private ClientMetadata clientMetadata;
    @Value("${apitoolkit.debug:false}")
    private Boolean debug;
    @Value("${apitoolkit.redactHeaders:cookies,authorization,x-api-key}")
    private String[] redactHeaders;
    @Value("${apitoolkit.redactRequestBody:$.password,$.email}")
    private String[] redactRequestBody;
    @Value("${apitoolkit.redactResponseBody:$.password,$.email}")
    private String[] redactResponseBody;
    @Value("${apitoolkit.rootUrl:https://app.apitoolkit.io}")
    private String rootUrl;
    @Value("${apitoolkit.apikey}")
    private String apikey;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ClientMetadata metadata;
        try {
            metadata = this.getClientMetadata(this.apikey, this.rootUrl);
            this.clientMetadata = metadata;
            ObjectMapper mapper = new ObjectMapper();
            String jsonStr = mapper.writeValueAsString(this.clientMetadata.pubsubPushServiceAccount);
            Credentials credentials;
            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(jsonStr.getBytes()))
                    .createScoped();
            ProjectTopicName topicName = ProjectTopicName.of(this.clientMetadata.pubsubProjectId,
                    this.clientMetadata.topicId);
            this.pubsubClient = Publisher.newBuilder(topicName).setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();

            if (this.debug) {
                System.out.println("Client initialized successfully");
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw new ServletException("APIToolkit: Error initializing client", e);
        }

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        final ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(req);
        final ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(res);
        long startTime = System.nanoTime();
        chain.doFilter(cachingRequest, cachingResponse);

        try {
            long duration = System.nanoTime() - startTime;

            final byte[] req_body = cachingRequest.getContentAsByteArray();
            final byte[] res_body = cachingResponse.getContentAsByteArray();
            cachingResponse.copyBodyToResponse();

            ByteString payload = buildPayload(duration, req, res, req_body, res_body);
            this.publishMessage(payload);
        } catch (Exception e) {
        }
    }

    @Override
    public void destroy() {
        // Cleanup code, if needed
    }

    public static class ClientMetadata {

        @JsonProperty("project_id")
        private String projectId;

        @JsonProperty("pubsub_project_id")
        private String pubsubProjectId;

        @JsonProperty("topic_id")
        private String topicId;

        @JsonProperty("pubsub_push_service_account")
        private Map<String, String> pubsubPushServiceAccount;
    }

    public ByteString buildPayload(long duration, HttpServletRequest req, HttpServletResponse res,
            byte[] req_body, byte[] res_body) {
        Enumeration<String> headerNames = req.getHeaderNames();

        HashMap<String, String> reqHeaders = new HashMap<>();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = req.getHeader(headerName);
            if (Arrays.asList(this.redactHeaders).contains(headerName)
                    || Arrays.asList(this.redactHeaders).contains(headerName.toLowerCase())) {
                headerValue = "[CLIENT_REDACTED]";
            }
            reqHeaders.put(headerName, headerValue);
        }

        HashMap<String, String> resHeaders = new HashMap<>();
        Collection<String> headerNames2 = res.getHeaderNames();

        for (String headerName : headerNames2) {
            String headerValue = res.getHeader(headerName);
            if (Arrays.asList(this.redactHeaders).contains(headerName)
                    || Arrays.asList(this.redactHeaders).contains(headerName.toLowerCase())) {
                headerValue = "[CLIENT_REDACTED]";
            }
            resHeaders.put(headerName, headerValue);
        }

        Map<String, String[]> params = req.getParameterMap();

        Integer statusCode = res.getStatus();
        String method = req.getMethod();
        String rawUrl = req.getRequestURI() + "?" + req.getQueryString();
        String matchedPattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) req
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        byte[] redactedBody = redactJson(req_body,
                Arrays.asList(this.redactRequestBody));
        byte[] redactedResBody = redactJson(res_body, Arrays.asList(this.redactResponseBody));
        Date currentDate = new Date();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String isoString = dateFormat.format(currentDate);

        Map<String, Object> payload = new HashMap<>();
        payload.put("request_headers", reqHeaders);
        payload.put("response_headers", resHeaders);
        payload.put("status_code", statusCode);
        payload.put("method", method);
        payload.put("host", req.getServerName());
        payload.put("raw_url", rawUrl);
        payload.put("duration", duration);
        payload.put("url_path", matchedPattern);
        payload.put("query_params", params);
        payload.put("path_params", pathVariables);
        payload.put("project_id", this.clientMetadata.projectId);
        payload.put("proto_major", 1);
        payload.put("proto_minor", 1);
        payload.put("timestamp", isoString);
        payload.put("referer", req.getHeader("referer") == null ? "" : req.getHeader("referer"));
        payload.put("sdk_type", "JavaSpringBoot");
        payload.put("request_body", Base64.getEncoder().encodeToString(redactedBody));
        payload.put("response_body", Base64.getEncoder().encodeToString(redactedResBody));

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
            return ByteString.copyFrom(jsonBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ByteString.EMPTY;
        }

    }

    public static byte[] redactJson(byte[] data, List<String> jsonPaths) {
        if (jsonPaths == null || jsonPaths.isEmpty() || data.length == 0) {
            return data;
        }
        try {
            String jsonData = new String(data, StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();

            for (String path : jsonPaths) {
                JsonArray tokens = JsonPath.read(jsonObject, path);
                if (tokens != null) {
                    for (int i = 0; i < tokens.size(); i++) {
                        tokens.set(i, new JsonPrimitive("[CLIENT_REDACTED]"));
                    }
                }
            }
            String redactedJson = jsonObject.toString();
            return redactedJson.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return data; // Return original data on error
        }
    }

    public void publishMessage(ByteString message) {
        if (this.pubsubClient != null) {
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(message).build();
            ApiFuture<String> messageIdFuture = this.pubsubClient.publish(pubsubMessage);
            try {
                String messageId = messageIdFuture.get();
                System.out.println("Published a message with custom attributes: " + messageId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ClientMetadata getClientMetadata(String apiKey, String rootUrl) throws IOException {
        String url = "https://app.apitoolkit.io";
        if (!rootUrl.isEmpty()) {
            url = rootUrl;
        }
        url += "/api/client_metadata";
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException("Failed to get client metadata: " + response);
        }
        String jsonResponse = response.body().string();
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(jsonResponse, ClientMetadata.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}