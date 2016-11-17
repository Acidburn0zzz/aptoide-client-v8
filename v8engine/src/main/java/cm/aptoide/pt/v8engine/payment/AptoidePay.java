/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 12/08/2016.
 */

package cm.aptoide.pt.v8engine.payment;

import android.content.Context;
import cm.aptoide.pt.v8engine.payment.exception.PaymentAlreadyProcessedException;
import cm.aptoide.pt.v8engine.payment.product.AptoideProduct;
import cm.aptoide.pt.v8engine.repository.PaymentRepository;
import cm.aptoide.pt.v8engine.repository.exception.RepositoryItemNotFoundException;
import java.util.List;
import lombok.AllArgsConstructor;
import rx.Observable;
import rx.schedulers.Schedulers;

import static rx.Observable.error;

/**
 * Created by marcelobenites on 8/12/16.
 */
@AllArgsConstructor public class AptoidePay {

  private final PaymentRepository paymentRepository;

  public Observable<List<Payment>> getProductPayments(Context context, AptoideProduct product) {
    return paymentRepository.getPayments(context, product);
  }

  public Observable<Purchase> getPurchase(AptoideProduct product) {
    return paymentRepository.getPaymentConfirmation(product)
        .first(paymentConfirmation -> paymentConfirmation.isCompleted())
        .flatMap(paymentConfirmation -> paymentRepository.getPurchase(product))
        .onErrorResumeNext(throwable -> {
          if (throwable instanceof RepositoryItemNotFoundException) {
            return Observable.just(null);
          }
          return Observable.error(throwable);
        });
  }

  public Observable<Purchase> pay(Payment payment) {
    return isProductAlreadyPurchased((AptoideProduct) payment.getProduct()).flatMap(alreadyPurchased -> {

      if (alreadyPurchased) {
        return Observable.<Purchase>error(new PaymentAlreadyProcessedException(
            "Product " + payment.getProduct().getId() + " already purchased."));
      }

      return paymentRepository.getPaymentConfirmation((AptoideProduct) payment.getProduct())
          .onErrorResumeNext(confirmationThrowable -> {
            if (confirmationThrowable instanceof RepositoryItemNotFoundException) {
              return processPaymentAndGetConfirmation(payment);
            }
            return Observable.<PaymentConfirmation>error(confirmationThrowable);
          })
          .first(paymentConfirmation -> paymentConfirmation.isCompleted())
          .flatMap(saved -> paymentRepository.getPurchase((AptoideProduct) payment.getProduct()));
    }).subscribeOn(Schedulers.computation());
  }

  private Observable<PaymentConfirmation> processPaymentAndGetConfirmation(Payment payment) {
    return payment.process()
        .flatMap(paymentConfirmation -> paymentRepository.savePaymentConfirmation(
            paymentConfirmation))
        .flatMap(saved -> paymentRepository.getPaymentConfirmation(
            (AptoideProduct) payment.getProduct()));
  }

  private Observable<Boolean> isProductAlreadyPurchased(AptoideProduct product) {
    return paymentRepository.getPurchase(product)
        .map(purchase -> true)
        .onErrorResumeNext(throwable -> {
          if (throwable instanceof RepositoryItemNotFoundException) {
            return Observable.just(false);
          }
          return error(throwable);
        });
  }
}