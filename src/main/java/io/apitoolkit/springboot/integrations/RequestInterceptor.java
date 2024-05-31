package io.apitoolkit.springboot.integrations;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

public class RequestInterceptor implements HttpRequestInterceptor {

    @Override
    public void process(HttpRequest request, HttpContext context) {
        String method = request.getRequestLine().getMethod();

        String uri = request.getRequestLine().getUri();
        List<NameValuePair> queryParams = null;
        if (uri.contains("?")) {
            String queryString = uri.substring(uri.indexOf("?") + 1);
            queryParams = URLEncodedUtils.parse(queryString, StandardCharsets.UTF_8);
        }

        try {
            String host = new URI(uri).getHost();
            context.setAttribute("host", host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HashMap<String, Object> requestHeaders = new HashMap<>();
        for (org.apache.http.Header header : request.getAllHeaders()) {
            requestHeaders.put(header.getName(), header.getValue());
        }
        String requestBody = null;

        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) request;
            if (entityRequest.getEntity() != null) {
                try {
                    requestBody = EntityUtils.toString(entityRequest.getEntity(), StandardCharsets.UTF_8);
                    entityRequest.setEntity(new StringEntity(requestBody, ContentType.get(entityRequest.getEntity())));

                } catch (ParseException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
        long startTime = System.nanoTime();
        context.setAttribute("apitoolkit_method", method);
        context.setAttribute("apitoolkit_raw_url", uri);
        context.setAttribute("start_time", startTime);
        context.setAttribute("query_params", queryParams);
        context.setAttribute("apitoolkit_reqbody", requestBody);
    }
}