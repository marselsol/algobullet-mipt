package com.algobullet_mipt.payment.yookassa;

public record YooKassaPaymentSession(
        String paymentId,
        String confirmationUrl,
        String returnUrl
) {
}
