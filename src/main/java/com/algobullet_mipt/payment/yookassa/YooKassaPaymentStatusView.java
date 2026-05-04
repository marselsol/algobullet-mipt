package com.algobullet_mipt.payment.yookassa;

public record YooKassaPaymentStatusView(
        String paymentId,
        String status,
        boolean paid,
        boolean planActivated,
        String planName,
        String message
) {
}
