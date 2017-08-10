package cm.aptoide.pt.view.account.store;

import android.support.annotation.StringRes;
import cm.aptoide.pt.view.account.ImagePickerView;
import rx.Completable;
import rx.Observable;

public interface ManageStoreView extends ImagePickerView {

  void loadImageStateless(String pictureUri);

  Observable<ManageStoreFragment.StoreViewModel> saveDataClick();

  Observable<Void> cancelClick();

  Completable showError(@StringRes int errorMessage);

  Completable showGenericError();

  void showWaitProgressBar();

  void dismissWaitProgressBar();

  void hideKeyboard();
}
