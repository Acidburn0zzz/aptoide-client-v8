/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 27/06/2016.
 */

package cm.aptoide.accountmanager;

import android.text.TextUtils;
import cm.aptoide.pt.crashreports.CrashReport;
import com.jakewharton.rxrelay.PublishRelay;
import java.net.SocketTimeoutException;
import rx.Completable;
import rx.Observable;
import rx.Single;

public class AptoideAccountManager {

  public static final String IS_FACEBOOK_OR_GOOGLE = "facebook_google";
  private final static String TAG = AptoideAccountManager.class.getSimpleName();

  private final Analytics analytics;
  private final CredentialsValidator credentialsValidator;
  private final PublishRelay<Account> accountRelay;
  private final AccountDataPersist dataPersist;
  private final AccountManagerService accountManagerService;

  public AptoideAccountManager(Analytics analytics, CredentialsValidator credentialsValidator,
      AccountDataPersist dataPersist, AccountManagerService accountManagerService,
      PublishRelay<Account> accountRelay) {
    this.credentialsValidator = credentialsValidator;
    this.analytics = analytics;
    this.dataPersist = dataPersist;
    this.accountManagerService = accountManagerService;
    this.accountRelay = accountRelay;
  }

  public Observable<Account> accountStatus() {
    return Observable.merge(accountRelay,
        getAccountAsync().onErrorReturn(throwable -> createLocalAccount()).toObservable());
  }

  public Single<Account> getAccountAsync() {
    return dataPersist.getAccount();
  }

  private Account createLocalAccount() {
    return new LocalAccount();
  }

  /**
   * Use {@link Account#isLoggedIn()} instead.
   *
   * @return true if user is logged in, false otherwise.
   */
  @Deprecated public boolean isLoggedIn() {
    final Account account = getAccount();
    return account != null && account.isLoggedIn();
  }

  /**
   * Use {@link #getAccountAsync()} method instead.
   *
   * @return user Account
   */
  @Deprecated public Account getAccount() {
    return getAccountAsync().onErrorReturn(throwable -> null).toBlocking().value();
  }

  public Completable logout() {
    return getAccountAsync().flatMapCompletable(
        account -> account.logout().andThen(removeAccount()));
  }

  public Completable removeAccount() {
    return dataPersist.removeAccount().doOnCompleted(() -> accountRelay.call(createLocalAccount()));
  }

  public Completable refreshToken() {
    return getAccountAsync().flatMapCompletable(
        account -> account.refreshToken().andThen(saveAccount(account)));
  }

  private Completable saveAccount(Account account) {
    return dataPersist.saveAccount(account).doOnCompleted(() -> accountRelay.call(account));
  }

  public Completable signUp(String email, String password) {
    return credentialsValidator.validate(email, password, true)
        .andThen(accountManagerService.createAccount(email, password))
        .andThen(login(Account.Type.APTOIDE, email, password, null))
        .doOnCompleted(() -> analytics.signUp())
        .onErrorResumeNext(throwable -> {
          if (throwable instanceof SocketTimeoutException) {
            return login(Account.Type.APTOIDE, email, password, null);
          }
          return Completable.error(throwable);
        });
  }

  public Completable login(Account.Type type, final String email, final String password,
      final String name) {
    return credentialsValidator.validate(email, password, false)
        .andThen(accountManagerService.login(type.name(), email, password, name))
        .flatMapCompletable(
            oAuth -> syncAccount(oAuth.getAccessToken(), oAuth.getRefreshToken(), password, type))
        .doOnCompleted(() -> analytics.login(email));
  }

  private Completable syncAccount(String accessToken, String refreshToken, String encryptedPassword,
      Account.Type type) {
    return accountManagerService.getAccount(accessToken, refreshToken, encryptedPassword,
        type.name()).flatMapCompletable(account -> saveAccount(account));
  }

  public void unsubscribeStore(String storeName, String storeUserName, String storePassword) {
    accountManagerService.unsubscribeStore(storeName, storeUserName, storePassword, this)
        .subscribe(() -> {
        }, throwable -> CrashReport.getInstance().log(throwable));
  }

  public Completable subscribeStore(String storeName, String storeUserName, String storePassword) {
    return accountManagerService.subscribeStore(storeName, storeUserName, storePassword, this);
  }

  /**
   * Use {@link Account#getEmail()} instead.
   *
   * @return user e-mail.
   */
  @Deprecated public String getAccountEmail() {
    final Account account = getAccount();
    return account == null ? null : account.getEmail();
  }

  /**
   * Use {@link Account#isAdultContentEnabled()} instead.
   *
   * @return true if adult content enabled, false otherwise.
   */
  @Deprecated public boolean isAccountMature() {
    final Account account = getAccount();
    return account == null ? false : account.isAdultContentEnabled();
  }

  /**
   * Use {@link Account#isAccessConfirmed()} instead.
   *
   * @return true if user {@link Account.Access} level is confirmed, false otherwise.
   */
  @Deprecated public boolean isAccountAccessConfirmed() {
    final Account account = getAccount();
    return account == null ? false : account.isAccessConfirmed();
  }

  /**
   * Use {@link Account#getAccess()} instead.
   *
   * @return user {@link Account.Access} level.
   */
  @Deprecated public Account.Access getAccountAccess() {
    return getAccount().getAccess();
  }

  public Completable syncCurrentAccount() {
    return getAccountAsync().flatMapCompletable(
        account -> syncAccount(account.getToken(), account.getRefreshToken(), account.getPassword(),
            account.getType()));
  }

  public Completable updateAccount(boolean adultContentEnabled) {
    return getAccountAsync().flatMapCompletable(
        account -> accountManagerService.updateAccount(adultContentEnabled, account.getToken())
            .andThen(
                syncAccount(account.getToken(), account.getRefreshToken(), account.getPassword(),
                    account.getType())));
  }

  public Completable updateAccount(Account.Access access) {
    return getAccountAsync().flatMapCompletable(
        account -> accountManagerService.updateAccount(access.name(), this)
            .andThen(
                syncAccount(account.getToken(), account.getRefreshToken(), account.getPassword(),
                    account.getType())));
  }

  public Completable updateAccount(String nickname, String avatarPath) {
    return getAccountAsync().flatMapCompletable(account -> {
      if (TextUtils.isEmpty(nickname) && TextUtils.isEmpty(avatarPath)) {
        return Completable.error(
            new AccountValidationException(AccountValidationException.EMPTY_NAME_AND_AVATAR));
      } else if (TextUtils.isEmpty(nickname)) {
        return Completable.error(
            new AccountValidationException(AccountValidationException.EMPTY_NAME));
      }
      return accountManagerService.updateAccount(account.getEmail(), nickname,
          account.getPassword(), TextUtils.isEmpty(avatarPath) ? "" : avatarPath,
          account.getToken());
    });
  }

  /**
   * Use {@link Account#getToken()} instead.
   *
   * @return account access token.
   */
  @Deprecated public String getAccessToken() {
    final Account account = getAccount();
    return account == null ? null : account.getToken();
  }
}
