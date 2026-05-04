package com.algobullet_mipt.payment.yookassa;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class YooKassaClient {

    private final YooKassaProperties properties;

    public JsonNode createPayment(Map<String, Object> requestBody, String idempotenceKey) {
        try {
            log.info("yookassa.createPayment.start baseUrl={} shopId={} idempotenceKey={} request={}",
                    properties.getApiBaseUrl(), maskShopId(properties.getShopId()), idempotenceKey, requestBody);
            JsonNode response = restClient().post()
                    .uri("/payments")
                    .header("Idempotence-Key", idempotenceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            log.info("yookassa.createPayment.success idempotenceKey={} response={}", idempotenceKey, response);
            return response;
        } catch (RestClientResponseException ex) {
            log.error("yookassa.createPayment.httpError idempotenceKey={} status={} body={}",
                    idempotenceKey, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new YooKassaPaymentException("Не удалось создать платеж: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("yookassa.createPayment.unexpectedError idempotenceKey={}", idempotenceKey, ex);
            throw new YooKassaPaymentException("Не удалось создать платеж: " + ex.getMessage(), ex);
        }
    }

    public JsonNode getPayment(String paymentId) {
        try {
            log.info("yookassa.getPayment.start paymentId={}", paymentId);
            JsonNode response = restClient().get()
                    .uri("/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(JsonNode.class);
            log.info("yookassa.getPayment.success paymentId={} response={}", paymentId, response);
            return response;
        } catch (RestClientResponseException ex) {
            log.error("yookassa.getPayment.httpError paymentId={} status={} body={}",
                    paymentId, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new YooKassaPaymentException("Не удалось получить статус платежа: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("yookassa.getPayment.unexpectedError paymentId={}", paymentId, ex);
            throw new YooKassaPaymentException("Не удалось получить статус платежа: " + ex.getMessage(), ex);
        }
    }

    private RestClient restClient() {
        if (!properties.isConfigured()) {
            log.error("yookassa.restClient.notConfigured enabled={} shopIdPresent={} secretPresent={} returnUrl={}",
                    properties.isEnabled(),
                    properties.getShopId() != null && !properties.getShopId().isBlank(),
                    properties.getSecretKey() != null && !properties.getSecretKey().isBlank(),
                    properties.getReturnUrl());
            throw new YooKassaPaymentException("ЮKassa не настроена: отсутствуют обязательные ключи.");
        }

        return RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.getShopId(), properties.getSecretKey()))
                .build();
    }

    private String maskShopId(String shopId) {
        if (shopId == null || shopId.isBlank()) {
            return "<empty>";
        }
        if (shopId.length() <= 2) {
            return "**";
        }
        return shopId.substring(0, 2) + "***";
    }
}
