package com.docqa.config;

import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/** HTTP settings with timeouts, so a slow or overloaded API can never hang a request forever. */
public final class HttpClients {

    private HttpClients() {}

    /**
     * @param responseTimeout how long to wait for the response to start (headers).
     *                        Streaming bodies keep flowing after that.
     */
    public static JdkClientHttpRequestFactory withTimeouts(Duration responseTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(responseTimeout);
        return factory;
    }
}
