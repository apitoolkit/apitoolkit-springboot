
package io.apitoolkit.springboot.integrations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;

public class ResponseInterceptor implements HttpResponseInterceptor {
  private HashMap<String, Object> config;
  private String urlPathPattern;
  private List<String> redactHeaders;
  private List<String> redactRequestBody;
  private List<String> redactResponseBody;
  private Boolean debug;

  ResponseInterceptor(HttpServletRequest req, String urlPathPattern, List<String> redactHeaders,
      List<String> redactRequestBody, List<String> redactResponseBody) {
    try {
      config = (HashMap<String, Object>) req.getAttribute("apitoolkit_config");
      if (config.get("debug") != null && (boolean) config.get("debug")) {
        this.debug = true;
      } else {
        this.debug = false;
      }
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

  public void process(HttpResponse response, HttpContext context) {
    try {
      Span span = (Span) context.getAttribute("span");
      String host = (String) context.getAttribute("apitoolkit_host");

      byte[] requestBodyStr = (byte []) context.getAttribute("apitoolkit_request_body");
      requestBodyStr = requestBodyStr == null ? "" : requestBodyStr;
      byte[] requestBody = Utils.redactJson(requestBodyStr.getBytes(), this.redactRequestBody, this.debug);
      HashMap<String, Object> requestHeaders = (HashMap<String, Object>) context
          .getAttribute("apitoolkit_request_headers");

      HashMap<String, Object> responseHeaders = new HashMap<>();
      for (org.apache.http.Header header : response.getAllHeaders()) {
        responseHeaders.put(header.getName(), header.getValue());
      }

      String method = (String) context.getAttribute("apitoolkit_method");
      String rawUrl = (String) context.getAttribute("apitoolkit_raw_url");
      HashMap<String, String> queryParams = (HashMap<String, String>) context
          .getAttribute("apitoolkit_query_params");

      int statusCode = response.getStatusLine().getStatusCode();
      HttpEntity entity = response.getEntity();
      byte[] responseBody = null;
      if (entity != null) {
        responseBody = EntityUtils.toByteArray(entity);
        response.setEntity(new BufferedHttpEntity(entity, responseBody));

      }
      byte[] redactedResBody = responseBody != null
          ? Utils.redactJson(responseBody, this.redactResponseBody, this.debug)
          : null;

      HashMap<String, Object> pathParams = new HashMap<>();
      if (this.urlPathPattern != null && this.urlPathPattern.length() > 0) {
        pathParams = Utils.getPathParamsFromPattern(this.urlPathPattern, rawUrl);
      }

      String urlWithoutQuery = rawUrl.split("\\?")[0];
      String path = this.urlPathPattern != null ? this.urlPathPattern : urlWithoutQuery;

      HashMapap<String, Object> outConfig = new HashMap<>({
        "debug": this.debug,
        "redactHeaders": this.redactHeaders,
        "redactRequestBody": this.redactRequestBody,
        "redactResponseBody": this.redactResponseBody,
      });

      List<Map<String, Object>> errors = new ArrayList<>();

      Utils.setApitoolkitAttributesAndEndSpan(
        span,
        host,
        statusCode,
        queryParams,
        pathParams,
        requestHeaders,
        responseHeaders,
        method,
        rawUrl,
        "",
        path,
        requestBody,
        responseBody,
        errors,
        this.config,
        "JavaApacheOutgoing",
        this.parent_id,
      );
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
