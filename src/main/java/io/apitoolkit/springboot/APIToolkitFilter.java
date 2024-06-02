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
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;

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
        List<Map<String, Object>> errors = new ArrayList<>();

        final ContentCachingRequestWrapper requestCache = new ContentCachingRequestWrapper(req);
        final ContentCachingResponseWrapper responseCache = new ContentCachingResponseWrapper(res);
        Integer statusCode = 200;
        long startTime = System.nanoTime();
        String msgId = UUID.randomUUID().toString();

        try {
            requestCache.setAttribute("APITOOLKIT_ERRORS", errors);
            HashMap<String, Object> config = new HashMap<>();
            config.put("debug", this.debug);
            config.put("project_id", this.clientMetadata.projectId);
            requestCache.setAttribute("apitoolkit_config", config);
            req.setAttribute("apitoolkit_filter", this);
            req.setAttribute("apitoolkit_message_id", msgId);
            chain.doFilter(requestCache, responseCache);
        } catch (Exception e) {
            statusCode = 500;
            APErrors.reportError(requestCache, e);
            throw e;
        } finally {
            long duration = System.nanoTime() - startTime;
            final byte[] req_body = requestCache.getContentAsByteArray();
            final byte[] res_body = responseCache.getContentAsByteArray();
            statusCode = statusCode == 500 ? 500 : responseCache.getStatus();
            responseCache.copyBodyToResponse();
            try {
                ByteString payload = buildPayload(duration, requestCache, responseCache, req_body, res_body,
                        statusCode, msgId);
                this.publishMessage(payload);
            } catch (Exception e) {
                if (this.debug) {
                    e.printStackTrace();
                }
            }
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
            byte[] req_body, byte[] res_body, Integer statusCode, String msgid) {
        Enumeration<String> headerNames = req.getHeaderNames();

        HashMap<String, Object> reqHeadersV = new HashMap<>();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = req.getHeader(headerName);
            reqHeadersV.put(headerName, headerValue);
        }
        HashMap<String, Object> reqHeaders = Utils.redactHeaders(reqHeadersV, Arrays.asList(this.redactHeaders));

        HashMap<String, Object> resHeaders = new HashMap<>();
        Collection<String> resHeadersV = res.getHeaderNames();

        for (String headerName : resHeadersV) {
            String headerValue = res.getHeader(headerName);
            resHeaders.put(headerName, headerValue);
        }
        resHeaders = Utils.redactHeaders(resHeaders, Arrays.asList(this.redactHeaders));

        Map<String, String[]> params = req.getParameterMap();

        String method = req.getMethod();
        String queryString = req.getQueryString() == null ? "" : "?" + req.getQueryString();
        String rawUrl = req.getRequestURI() + queryString;
        String matchedPattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) req
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        byte[] redactedBody = Utils.redactJson(req_body,
                Arrays.asList(this.redactRequestBody), this.debug);
        byte[] redactedResBody = Utils.redactJson(res_body, Arrays.asList(this.redactResponseBody), this.debug);
        Date currentDate = new Date();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String isoString = dateFormat.format(currentDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errorList = (List<Map<String, Object>>) req.getAttribute("APITOOLKIT_ERRORS");

        Map<String, Object> payload = new HashMap<>();
        payload.put("request_headers", reqHeaders);
        payload.put("response_headers", resHeaders);
        payload.put("status_code", statusCode);
        payload.put("method", method);
        payload.put("errors", errorList);
        payload.put("host", req.getServerName());
        payload.put("raw_url", rawUrl);
        payload.put("duration", duration);
        payload.put("url_path", matchedPattern);
        payload.put("query_params", params);
        payload.put("path_params", pathVariables);
        payload.put("project_id", this.clientMetadata.projectId);
        payload.put("proto_major", 1);
        payload.put("proto_minor", 1);
        payload.put("msg_id", msgid);
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
            if (this.debug) {
                e.printStackTrace();
            }
            return ByteString.EMPTY;
        }

    }

    public void publishMessage(ByteString message) {
        if (this.pubsubClient != null) {
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(message).build();
            ApiFuture<String> messageIdFuture = this.pubsubClient.publish(pubsubMessage);
            try {
                String messageId = messageIdFuture.get();
                if (this.debug) {
                    System.out.println("Published a message with custom attributes: " + messageId);
                }
            } catch (Exception e) {
                if (this.debug) {
                    e.printStackTrace();
                }
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