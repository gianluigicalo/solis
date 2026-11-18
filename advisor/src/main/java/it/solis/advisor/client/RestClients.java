package it.solis.advisor.client;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

final class RestClients {

    private RestClients() {
    }

    static RestClient create(URI baseUrl, Duration timeout) {
        var httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .build();
    }
}
