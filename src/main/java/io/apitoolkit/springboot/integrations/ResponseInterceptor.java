
package io.apitoolkit.springboot.integrations;

import java.util.HashMap;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Base64;
import java.util.Date;
import java.text.SimpleDateFormat;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.apitoolkit.springboot.APIToolkitFilter;
import io.apitoolkit.springboot.Utils;
import jakarta.servlet.http.HttpServletRequest;

public class ResponseInterceptor implements HttpResponseInterceptor {
    private HashMap<String, Object> config;
    private APIToolkitFilter apitoolkit;
    private String project_id;
    private String urlPathPattern;
    private List<String> redactHeaders;
    private List<String> redactRequestBody;
    private List<String> redactResponseBody;
    private Boolean debug;

    @SuppressWarnings("unchecked")
    ResponseInterceptor(HttpServletRequest req, String urlPathPattern, List<String> redactHeaders,
            List<String> redactRequestBody, List<String> redactResponseBody) {
        try {
            config = (HashMap<String, Object>) req.getAttribute("apitoolkit_config");
            apitoolkit = (APIToolkitFilter) req.getAttribute("apitoolkit_filter");
            if (config.get("debug") != null && (boolean) config.get("debug")) {
                this.debug = true;
            } else {
                this.debug = false;
            }
            this.project_id = (String) config.get("project_id");
            this.urlPathPattern = urlPathPattern;
            this.redactHeaders = redactHeaders;
            this.redactResponseBody = redactResponseBody;
            this.redactRequestBody = redactRequestBody;

        } catch (Exception e) {
            if (config.get("debug") != null && (boolean) config.get("debug")) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void process(HttpResponse response, HttpContext context) {
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            String host = (String) context.getAttribute("apitoolkit_host");
            long endTime = System.nanoTime();
            long startTime = (long) context.getAttribute("apitoolkit_start_time");
            long duration = endTime - startTime;

            String requestBodyStr = (String) context.getAttribute("apitoolkit_request_body");
            requestBodyStr = requestBodyStr == null ? "" : requestBodyStr;
            byte[] requestBody = Utils.redactJson(requestBodyStr.getBytes(), this.redactRequestBody, this.debug);
            HashMap<String, Object> requestHeaders = (HashMap<String, Object>) context
                    .getAttribute("apitoolkit_request_headers");
            requestHeaders = Utils.redactHeaders(requestHeaders, this.redactHeaders);

            HashMap<String, Object> responseHeaders = new HashMap<>();
            for (org.apache.http.Header header : response.getAllHeaders()) {
                responseHeaders.put(header.getName(), header.getValue());
            }
            responseHeaders = Utils.redactHeaders(responseHeaders, this.redactHeaders);

            String method = (String) context.getAttribute("apitoolkit_method");
            String rawUrl = (String) context.getAttribute("apitoolkit_raw_url");
            HashMap<String, String> queryParams = (HashMap<String, String>) context
                    .getAttribute("apitoolkit_query_params");

            Date currentDate = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            String isoString = dateFormat.format(currentDate);
            HttpEntity entity = response.getEntity();
            byte[] responseBody = null;
            if (entity != null) {
                responseBody = EntityUtils.toByteArray(entity);
                response.setEntity(new BufferedHttpEntity(entity, responseBody));

            }
            byte[] redactedResBody = responseBody != null
                    ? Utils.redactJson(responseBody, this.redactResponseBody, this.debug)
                    : null;

            HashMap<String, Object> payload = new HashMap<>();
            payload.put("request_headers", requestHeaders);
            payload.put("response_headers", responseHeaders);
            payload.put("status_code", statusCode);
            payload.put("method", method);
            payload.put("host", host);
            payload.put("raw_url", rawUrl);
            payload.put("duration", duration);
            payload.put("url_path", this.urlPathPattern != null ? this.urlPathPattern : rawUrl);
            payload.put("query_params", queryParams);
            payload.put("path_params", null);
            payload.put("project_id", this.project_id);
            payload.put("proto_major", 1);
            payload.put("proto_minor", 1);
            payload.put("timestamp", isoString);
            payload.put("referer",
                    requestHeaders != null ? requestHeaders.get("referer") == null ? "" : requestHeaders.get("referer")
                            : "");
            payload.put("sdk_type", "JavaApacheOutgoing");
            payload.put("request_body",
                    Base64.getEncoder().encodeToString(requestBody));
            payload.put("response_body",
                    Base64.getEncoder().encodeToString(redactedResBody));
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
                apitoolkit.publishMessage(ByteString.copyFrom(jsonBytes));
            } catch (Exception e) {
                if (this.debug) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            if (this.debug) {
                e.printStackTrace();
            }
        }
    }

    class BufferedHttpEntity extends HttpEntityWrapper {
        private final byte[] body;

        public BufferedHttpEntity(HttpEntity entity, byte[] body) {
            super(entity);
            this.body = body;
        }

        @Override
        public InputStream getContent() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public long getContentLength() {
            return body.length;
        }
    }
}
