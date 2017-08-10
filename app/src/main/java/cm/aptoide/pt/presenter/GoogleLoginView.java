/*
 * Copyright (c) 2017.
 * Modified by Marcelo Benites on 08/02/2017.
 */

package cm.aptoide.pt.presenter;

import cm.aptoide.pt.view.account.GoogleAccountViewModel;
import rx.Observable;

/**
 * Created by marcelobenites on 08/02/17.
 */
public interface GoogleLoginView extends View {

  void showGoogleLogin();

  void hideGoogleLogin();

  Observable<GoogleAccountViewModel> googleLoginClick();
}
