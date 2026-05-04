package com.algobullet_mipt.payment.yookassa;

import com.algobullet_mipt.entity.SubscriptionPlan;
import com.algobullet_mipt.entity.UserAccount;
import com.algobullet_mipt.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
public class YooKassaController {

    private final YooKassaService yooKassaService;
    private final UserAccountService userAccountService;

    @PostMapping("/api/billing/yookassa/payment")
    public YooKassaPaymentSession createPayment(@RequestParam("plan") SubscriptionPlan plan) {
        UserAccount user = userAccountService.getCurrentUser()
                .orElseThrow(() -> new YooKassaPaymentException("Пользователь не найден."));
        log.info("yookassa.controller.createPayment plan={} userId={} username={}",
                plan.name(), user.getId(), user.getUsername());
        return yooKassaService.createPaymentSession(plan, user);
    }

    @GetMapping("/api/billing/yookassa/payment/{paymentId}")
    public YooKassaPaymentStatusView getPaymentStatus(@PathVariable String paymentId) {
        log.info("yookassa.controller.getPaymentStatus paymentId={}", paymentId);
        return yooKassaService.getPaymentStatusForCurrentUser(paymentId);
    }

    @PostMapping("/api/billing/yookassa/client-log")
    public ResponseEntity<Void> logClientEvent(@RequestBody ClientLogRequest request) {
        log.error("yookassa.client.{} message={} details={}",
                request.level(), request.message(), request.details());
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(YooKassaPaymentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleYooKassaPaymentException(YooKassaPaymentException ex) {
        log.error("yookassa.controller.paymentError message={}", ex.getMessage(), ex);
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpectedException(Exception ex) {
        log.error("yookassa.controller.unexpectedError", ex);
        return new ErrorResponse("Internal payment error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    public record ErrorResponse(String message) {
    }

    public record ClientLogRequest(String level, String message, String details) {
    }
}
