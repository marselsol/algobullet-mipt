package com.algobullet_mipt.payment.yookassa;

import com.algobullet_mipt.entity.SubscriptionPlan;
import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.service.UserAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class YooKassaService {

    private final YooKassaClient yooKassaClient;
    private final YooKassaProperties properties;
    private final UserAccountService userAccountService;

    public YooKassaPaymentSession createPaymentSession(SubscriptionPlan plan, UserAccount user) {
        if (!plan.isPayable()) {
            log.warn("yookassa.createPaymentSession.nonPayablePlan userId={} plan={}", user.getId(), plan);
            throw new YooKassaPaymentException("Для выбранного тарифа онлайн-оплата не требуется.");
        }

        String idempotenceKey = UUID.randomUUID().toString();
        log.info("yookassa.createPaymentSession.start userId={} username={} plan={} amount={} returnUrl={} idempotenceKey={}",
                user.getId(), user.getUsername(), plan.name(), plan.getAmountValue(), properties.getReturnUrl(), idempotenceKey);

        JsonNode payment = yooKassaClient.createPayment(buildCreatePaymentRequest(plan, user), idempotenceKey);
        String paymentId = requiredText(payment, "id");
        String confirmationUrl = payment.path("confirmation").path("confirmation_url").asText();
        if (confirmationUrl.isBlank()) {
            log.error("yookassa.createPaymentSession.noConfirmationUrl paymentId={} response={}", paymentId, payment);
            throw new YooKassaPaymentException("ЮKassa не вернула confirmation_url для redirect-сценария.");
        }

        log.info("yookassa.createPaymentSession.success userId={} paymentId={} plan={} confirmationUrl={}",
                user.getId(), paymentId, plan.name(), confirmationUrl);
        return new YooKassaPaymentSession(paymentId, confirmationUrl, properties.getReturnUrl());
    }

    @Transactional
    public YooKassaPaymentStatusView getPaymentStatusForCurrentUser(String paymentId) {
        UserAccount user = userAccountService.getCurrentUser()
                .orElseThrow(() -> {
                    log.error("yookassa.getPaymentStatus.noCurrentUser paymentId={}", paymentId);
                    return new YooKassaPaymentException("Пользователь не найден.");
                });

        log.info("yookassa.getPaymentStatus.start paymentId={} userId={} username={}",
                paymentId, user.getId(), user.getUsername());

        JsonNode payment = yooKassaClient.getPayment(paymentId);
        validatePaymentOwnership(payment, user);

        String status = payment.path("status").asText();
        SubscriptionPlan plan = SubscriptionPlan.valueOf(payment.path("metadata").path("plan").asText());
        boolean paid = "succeeded".equalsIgnoreCase(status);
        SubscriptionPlan previousPlan = user.getSubscriptionPlan();
        boolean planActivated = false;

        if (paid && previousPlan != plan) {
            user.setSubscriptionPlan(plan);
            planActivated = true;
            log.info("yookassa.getPaymentStatus.planUpdated paymentId={} userId={} oldPlan={} newPlan={}",
                    paymentId, user.getId(), previousPlan, plan);
        }

        log.info("yookassa.getPaymentStatus.result paymentId={} userId={} status={} paid={} planActivated={}",
                paymentId, user.getId(), status, paid, planActivated);

        return new YooKassaPaymentStatusView(
                paymentId,
                status,
                paid,
                planActivated,
                plan.name(),
                buildStatusMessage(status)
        );
    }

    private Map<String, Object> buildCreatePaymentRequest(SubscriptionPlan plan, UserAccount user) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", Map.of(
                "value", plan.getAmountValue().toPlainString(),
                "currency", "RUB"
        ));
        request.put("capture", true);
        request.put("description", "ALGOBULLET " + plan.getDisplayName() + " for " + user.getUsername());
        request.put("payment_method_data", Map.of(
                "type", "sbp"
        ));
        request.put("confirmation", Map.of(
                "type", "redirect",
                "return_url", properties.getReturnUrl() + "?plan=" + plan.name()
        ));
        request.put("metadata", Map.of(
                "userId", String.valueOf(user.getId()),
                "plan", plan.name(),
                "username", user.getUsername()
        ));
        return request;
    }

    private void validatePaymentOwnership(JsonNode payment, UserAccount user) {
        String paymentUserId = payment.path("metadata").path("userId").asText();
        if (!String.valueOf(user.getId()).equals(paymentUserId)) {
            log.error("yookassa.validatePaymentOwnership.failed paymentId={} expectedUserId={} actualUserId={} payment={}",
                    payment.path("id").asText(), user.getId(), paymentUserId, payment);
            throw new YooKassaPaymentException("Платеж не принадлежит текущему пользователю.");
        }
    }

    private String buildStatusMessage(String status) {
        return switch (status) {
            case "pending" -> "Платеж создан и ожидает подтверждения в приложении банка.";
            case "waiting_for_capture" -> "Платеж авторизован и ожидает списания.";
            case "succeeded" -> "Оплата прошла успешно, тариф активирован.";
            case "canceled" -> "Платеж отменен или не был завершен.";
            default -> "Получен статус платежа: " + status;
        };
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (value.isBlank()) {
            log.error("yookassa.requiredText.missing fieldName={} node={}", fieldName, node);
            throw new YooKassaPaymentException("В ответе ЮKassa отсутствует поле " + fieldName + ".");
        }
        return value;
    }
}
