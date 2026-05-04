package com.algobullet_mipt.payment.yookassa;

public class YooKassaPaymentException extends RuntimeException {

    public YooKassaPaymentException(String message) {
        super(message);
    }

    public YooKassaPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
