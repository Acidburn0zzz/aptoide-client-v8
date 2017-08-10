package cm.aptoide.pt.view.account;

import android.content.DialogInterface;
import cm.aptoide.pt.presenter.View;
import cm.aptoide.pt.view.account.exception.InvalidImageException;
import rx.Observable;

public interface ImagePickerView extends View {
  void loadImage(String pictureUri);

  Observable<DialogInterface> dialogCameraSelected();

  Observable<DialogInterface> dialogGallerySelected();

  void showImagePickerDialog();

  void showIconPropertiesError(InvalidImageException exception);

  Observable<Void> selectStoreImageClick();

  void dismissLoadImageDialog();
}
