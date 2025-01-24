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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerMapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class APIToolkitFilter implements Filter {
  @Value("${apitoolkit.debug:false}")
  private Boolean debug;
  @Value("${apitoolkit.redactHeaders:}")
  private String[] redactHeaders;
  @Value("${apitoolkit.redactRequestBody:")
  private String[] redactRequestBody;
  @Value("${apitoolkit.redactResponseBody:")
  private String[] redactResponseBody;
  @Value("${apitoolkit.capture_request_body:false}")
  private String captureRequestBody;
  @Value("${apitoolkit.capture_response_body:false}")
  private String captureResponseBody;
  @Value("${apitoolkit.service_name:}")
  private String serviceName;
  @Value("${apitoolkit.service_version:}")
  private String serviceVersion;
  @Value("${apitoolkit.tags:}")
  private String tags;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // We use filterConfig for testing configurations
    // The @Value injection is used to set the values in the application.properties
    // file. The @Value get's inject first, so if apikey is null then we try getting
    // config from filterConfig.
    String rHeaders = filterConfig.getInitParameter("apitoolkit.redactHeaders");
    this.redactHeaders = this.redactHeaders != null ? this.redactHeaders
        : rHeaders != null ? rHeaders.split(",") : null;
    if (this.redactRequestBody == null) {
      String req_body = filterConfig.getInitParameter("apitoolkit.redactRequestBody");
      this.redactRequestBody = req_body != null ? req_body.split(",") : null;
    }

    if (this.redactResponseBody == null) {
      String res_body = filterConfig.getInitParameter("apitoolkit.redactResponseBody");
      this.redactResponseBody = res_body != null ? res_body.split(",") : null;
    }

    if (this.service_name == null) {
      this.service_name = filterConfig.getInitParameter("apitoolkit.serviceName") || "";
    }
    if (this.service_version == null) {
      this.service_version = filterConfig.getInitParameter("apitoolkit.serviceVersion") || "";
    }
    if (this.tags == null) {
      this.tags = filterConfig.getInitParameter("apitoolkit.tags");
    }
    if (this.debug == false) {
      this.debug = Boolean.parseBoolean(filterConfig.getInitParameter("apitoolkit.debug")) || false;
    }

    if (this.captureRequestBody == false) {
      this.captureRequestBody = filterConfig.getInitParameter("apitoolkit.captureRequestBody") || false;
    }
    if (this.captureResponseBody == false) {
      this.captureResponseBody = filterConfig.getInitParameter("apitoolkit.captureResponseBody") || false;
    }
    if (this.debug) {
      System.out.println("Client initialized successfully");
    }

  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    Tracer tracer = GlobalOpenTelemetry.getTracer("custom-span-example");
    Span span = tracer.spanBuilder("custom-operation").startSpan();

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
      final byte[] req_body = requestCache.getContentAsByteArray();
      final byte[] res_body = responseCache.getContentAsByteArray();
      statusCode = statusCode == 500 ? 500 : responseCache.getStatus();
      responseCache.copyBodyToResponse();
      try {
        ByteString payload = buildPayload(requestCache, responseCache, req_body, res_body,
            statusCode, msgId);
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

  public ByteString buildPayload(Span span, HttpServletRequest req, HttpServletResponse res,
      byte[] req_body, byte[] res_body, Integer statusCode, String msgid) {
    Enumeration<String> headerNames = req.getHeaderNames();

    HashMap<String, Object> reqHeadersV = new HashMap<>();

    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      String headerValue = req.getHeader(headerName);
      reqHeadersV.put(headerName, headerValue);
    }

    HashMap<String, Object> resHeaders = new HashMap<>();
    Collection<String> resHeadersV = res.getHeaderNames();

    for (String headerName : resHeadersV) {
      String headerValue = res.getHeader(headerName);
      resHeaders.put(headerName, headerValue);
    }

    Map<String, String[]> params = req.getParameterMap();

    String method = req.getMethod();
    String queryString = req.getQueryString() == null ? "" : "?" + req.getQueryString();
    String rawUrl = req.getRequestURI() + queryString;
    String matchedPattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    @SuppressWarnings("unchecked")
    Map<String, String> pathVariables = (Map<String, String>) req
        .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> errorList = (List<Map<String, Object>>) req.getAttribute("APITOOLKIT_ERRORS");

    Map<String,Object> config = new HashMap<>();
    config.put("serviceVersion", this.service_version);
    config.put("tags", this.tags);
    config.put("debug", this.debug);
    config.put("redactHeaders", this.redactHeaders);
    config.put("redactRequestBody", this.redactRequestBody);
    config.put("redactResponseBody", this.redactResponseBody);
    config.put("captureRequestBody", this.capture_request_body);
    config.put("captureResponseBody", this.capture_response_body);

    Utils.setApitoolkitAttributesAndEndSpan(
      span,
      req.getServerName(),
      statusCode,
      params,
      pathVariables,
      reqHeaders,
      resHeaders,
      method,
      rawUrl,
      msgid,
      matchedPattern,
      req_body,
      res_body,
      errorList,
      config,
       "JavaSpringBoot",
       null
      )
  }
}
