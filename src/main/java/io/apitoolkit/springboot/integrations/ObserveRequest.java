package io.apitoolkit.springboot.integrations;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.server.ServletServerHttpRequest;

public class ObserveRequest {
    public static CloseableHttpClient createHttpClient(ServletServerHttpRequest request) {
        return HttpClients.custom()
                .addInterceptorFirst(new RequestInterceptor())
                .addInterceptorFirst(new ResponseInterceptor())
                .build();
    }
}
