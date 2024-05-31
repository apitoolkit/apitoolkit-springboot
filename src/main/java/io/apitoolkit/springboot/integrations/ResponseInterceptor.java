
package io.apitoolkit.springboot.integrations;

import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.protocol.HttpContext;

public class ResponseInterceptor implements HttpResponseInterceptor {
    public void process(HttpResponse response, HttpContext context) {
        int status = response.getStatusLine().getStatusCode();
        String host = (String) context.getAttribute("apitoolkit_host");
        long endTime = System.nanoTime();
        long startTime = (long) context.getAttribute("apitoolkit_start_time");
        long duration = endTime - startTime;
        String requestBody = (String) context.getAttribute("apitoolkit_request_body");
        HashMap<String, Object> requestHeaders = (HashMap<String, Object>) context
                .getAttribute("apitoolkit_request_headers");
        String method = (String) context.getAttribute("apitoolkit_method");
        String raw_url = (String) context.getAttribute("apitoolkit_raw_url");
        List<NameValuePair> queryParams = (List<NameValuePair>) context.getAttribute("apitoolkit_query_params");
    }
}
