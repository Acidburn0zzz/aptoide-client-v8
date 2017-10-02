/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 29/08/2016.
 */

package cm.aptoide.pt.billing.view;

import android.os.Bundle;
import cm.aptoide.pt.billing.Billing;
import cm.aptoide.pt.billing.BillingAnalytics;
import cm.aptoide.pt.billing.PaymentMethod;
import cm.aptoide.pt.billing.Product;
import cm.aptoide.pt.billing.exception.PaymentMethodNotAuthorizedException;
import cm.aptoide.pt.presenter.Presenter;
import cm.aptoide.pt.presenter.View;
import java.io.IOException;
import java.util.List;
import rx.Completable;
import rx.android.schedulers.AndroidSchedulers;

public class PaymentPresenter implements Presenter {

  private static final int PAYER_AUTHORIZATION_REQUEST_CODE = 2001;

  private final PaymentView view;
  private final Billing billing;
  private final BillingNavigator navigator;
  private final BillingAnalytics analytics;
  private final String sellerId;
  private final String productId;
  private final String payload;

  public PaymentPresenter(PaymentView view, Billing billing, BillingNavigator navigator,
      BillingAnalytics analytics, String sellerId, String productId, String payload) {
    this.view = view;
    this.billing = billing;
    this.navigator = navigator;
    this.analytics = analytics;
    this.sellerId = sellerId;
    this.productId = productId;
    this.payload = payload;
  }

  @Override public void present() {

    onViewCreatedNavigateToPayerAuthentication();

    onViewCreatedHandlePayerAuthenticationResult();

    onViewCreatedShowPaymentInformation();

    onViewCreatedCheckPurchase();

    handleSelectPaymentMethodEvent();

    handleCancelEvent();

    handleBuyEvent();
  }

  @Override public void saveState(Bundle state) {

  }

  @Override public void restoreState(Bundle state) {

  }

  private void onViewCreatedNavigateToPayerAuthentication() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .doOnNext(__ -> view.showPaymentLoading())
        .flatMap(__ -> billing.getPayer()
            .isAuthenticated()
            .first())
        .doOnNext(authenticated -> analytics.sendPayerAuthenticatedEvent(authenticated))
        .filter(authenticated -> !authenticated)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(__ -> navigator.navigateToPayerAuthenticationForResult(
            PAYER_AUTHORIZATION_REQUEST_CODE))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, throwable -> navigator.popViewWithResult(throwable));
  }

  private void onViewCreatedHandlePayerAuthenticationResult() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(__ -> navigator.payerAuthenticationResults(PAYER_AUTHORIZATION_REQUEST_CODE))
        .doOnNext(authenticated -> analytics.sendPayerAuthenticationResultEvent(authenticated))
        .filter(authenticated -> !authenticated)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(__ -> navigator.popViewWithResult())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, throwable -> navigator.popViewWithResult(throwable));
  }

  private void onViewCreatedShowPaymentInformation() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(__ -> billing.getPayer()
            .isAuthenticated())
        .filter(authenticated -> authenticated)
        .flatMapSingle(loading -> billing.getProduct(sellerId, productId))
        .flatMapCompletable(product -> billing.getPaymentMethods(sellerId, productId)
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapCompletable(payments -> showPaymentInformation(product, payments))
            .doOnCompleted(() -> view.hidePaymentLoading()))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(__ -> {
        }, throwable -> {
          view.hidePaymentLoading();
          navigator.popViewWithResult(throwable);
        });
  }

  private void onViewCreatedCheckPurchase() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(__ -> billing.getPayer()
            .isAuthenticated())
        .filter(authenticated -> authenticated)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(__ -> view.showPurchaseLoading())
        .flatMap(__ -> billing.getPurchase(sellerId, productId)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(purchase -> {
              if (purchase.isPending()) {
                view.showPurchaseLoading();
              } else {
                view.hidePurchaseLoading();
              }

              if (purchase.isCompleted()) {
                analytics.sendPaymentSuccessEvent();
                navigator.popViewWithResult(purchase);
              }

              if (purchase.isFailed()) {
                analytics.sendPaymentErrorEvent();
                view.showUnknownError();
              }
            }))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(__ -> {
        }, throwable -> {
          view.hidePurchaseLoading();
          showError(throwable);
        });
  }

  private void handleCancelEvent() {
    view.getLifecycle()
        .filter(event -> View.LifecycleEvent.CREATE.equals(event))
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap(product -> view.cancelEvent())
        .flatMapCompletable(
            product -> sendPaymentCancelAnalytics().observeOn(AndroidSchedulers.mainThread())
                .doOnCompleted(() -> navigator.popViewWithResult()))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, throwable -> navigator.popViewWithResult());
  }

  private void handleSelectPaymentMethodEvent() {
    view.getLifecycle()
        .filter(event -> View.LifecycleEvent.CREATE.equals(event))
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap(created -> view.selectPaymentEvent())
        .flatMapCompletable(
            paymentMethodViewModel -> billing.selectPaymentMethod(paymentMethodViewModel.getId(),
                sellerId, productId))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, throwable -> navigator.popViewWithResult(throwable));
  }

  private void handleBuyEvent() {
    view.getLifecycle()
        .filter(event -> View.LifecycleEvent.CREATE.equals(event))
        .flatMap(__ -> view.buyEvent()
            .doOnNext(buySelection -> view.showBuyLoading())
            .flatMapSingle(selection -> billing.getProduct(sellerId, productId))
            .flatMapCompletable(product -> billing.getSelectedPaymentMethod(sellerId, productId)
                .doOnSuccess(
                    payment -> analytics.sendPaymentViewBuyEvent(product, payment.getName()))
                .flatMapCompletable(payment -> billing.processPayment(sellerId, productId, payload)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnCompleted(() -> {
                      analytics.sendAuthorizationSuccessEvent(payment.getName());
                      view.hideBuyLoading();
                    })
                    .onErrorResumeNext(
                        throwable -> navigateToAuthorizationView(payment, throwable))))
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError(throwable -> {
              view.hideBuyLoading();
              showError(throwable);
            })
            .retry())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, throwable -> navigator.popViewWithResult(throwable));
  }

  private Completable navigateToAuthorizationView(PaymentMethod paymentMethod,
      Throwable throwable) {
    if (throwable instanceof PaymentMethodNotAuthorizedException) {
      navigator.navigateToTransactionAuthorizationView(sellerId, productId, payload, paymentMethod);
      return Completable.complete();
    }
    return Completable.error(throwable);
  }

  private void showError(Throwable throwable) {
    if (throwable instanceof IOException) {
      view.showNetworkError();
    } else {
      view.showUnknownError();
    }
  }

  private Completable showPaymentInformation(Product product, List<PaymentMethod> paymentMethods) {
    view.showProduct(product);
    if (paymentMethods.isEmpty()) {
      view.showPaymentsNotFoundMessage();
    } else {
      view.showPayments(paymentMethods);
    }
    if (paymentMethods.isEmpty()) {
      return Completable.complete();
    }
    return showSelectedPaymentMethod();
  }

  private Completable sendPaymentCancelAnalytics() {
    return billing.getProduct(sellerId, productId)
        .flatMapCompletable(product -> billing.getSelectedPaymentMethod(sellerId, productId)
            .doOnSuccess(payment -> analytics.sendPaymentViewCancelEvent(product))
            .toCompletable());
  }

  private Completable showSelectedPaymentMethod() {
    return billing.getSelectedPaymentMethod(sellerId, productId)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(payment -> view.selectPayment(payment))
        .toCompletable();
  }
}
