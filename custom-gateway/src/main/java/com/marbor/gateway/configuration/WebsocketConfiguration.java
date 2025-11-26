package com.marbor.gateway.configuration;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;


@Configuration
public class WebsocketConfiguration {

    @Bean
    public HttpClient httpClient() {
        var httpClient = new HttpClient();
        httpClient.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        return httpClient;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WebSocketClient webSocketClient(HttpClient httpClient) {
        return new WebSocketClient(httpClient);
    }
}
