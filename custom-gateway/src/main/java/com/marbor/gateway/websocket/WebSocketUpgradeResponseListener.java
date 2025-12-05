package com.marbor.gateway.websocket;

import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.websocket.client.JettyUpgradeListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WebSocketUpgradeResponseListener implements JettyUpgradeListener {

    private final CountDownLatch waitUntilListenerInvoked = new CountDownLatch(1);
    private volatile Map<String, List<String>> headers;
    private volatile Response response;

    @Override
    public void onHandshakeResponse(Request request, Response response) {
        HttpFields headers = response.getHeaders();
        this.headers = map(headers);
        this.response = response;
        waitUntilListenerInvoked.countDown();
    }

    public static Map<String, List<String>> map(HttpFields fields) {
        if (fields == null) {
            return new LinkedHashMap<>();
        }

        return fields.stream()
                .collect(Collectors.toMap(HttpField::getName, HttpField::getValueList));
    }

    public Map<String, List<String>> getHandshakeHeaders() {
        return new LinkedHashMap<>(headers);
    }

    public int getHandshakeStatus() {
        return response.getStatus();
    }

    public String getReason() {
        return response.getReason();
    }

    public boolean awaitForHandshakeResponse(long timeout, TimeUnit timeUnit) {
        try {
            return this.waitUntilListenerInvoked.await(timeout, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Listener waiting interrupted", e);
        }
    }
}