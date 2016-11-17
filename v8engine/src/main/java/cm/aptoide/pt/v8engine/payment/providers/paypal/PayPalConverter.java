/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 10/08/2016.
 */

package cm.aptoide.pt.v8engine.payment.providers.paypal;

import cm.aptoide.pt.model.v3.ProductPaymentResponse;
import cm.aptoide.pt.v8engine.payment.PaymentConfirmation;
import cm.aptoide.pt.v8engine.payment.Price;
import cm.aptoide.pt.v8engine.payment.Product;
import com.paypal.android.sdk.payments.PayPalPayment;
import java.math.BigDecimal;

/**
 * Created by marcelobenites on 8/10/16.
 */
public class PayPalConverter {

  public PayPalPayment convertToPayPal(double price, String currency, String description) {
    return new PayPalPayment(BigDecimal.valueOf(price), currency, description,
        PayPalPayment.PAYMENT_INTENT_SALE);
  }

  public PaymentConfirmation convertFromPayPal(
      com.paypal.android.sdk.payments.PaymentConfirmation payPalConfirmation, int paymentId,
      Product product, Price price) {
    return new PaymentConfirmation(payPalConfirmation.getProofOfPayment().getPaymentId(), paymentId,
        product, price, ProductPaymentResponse.Status.UNKNOWN);
  }
}
