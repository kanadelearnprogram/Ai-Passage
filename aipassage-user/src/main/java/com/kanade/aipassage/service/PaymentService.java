package com.kanade.aipassage.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;

public interface PaymentService {
    String creatPaymentSession(Long id) throws StripeException;

    Event eventConstruct(String payload, String sigHeader) throws SignatureVerificationException;

    void handlePaymentSusses(Session session);

    boolean handleRefund(Long id, String reason) throws StripeException;
}
