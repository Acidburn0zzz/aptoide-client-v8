package cm.aptoide.accountmanager;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.Button;
import android.widget.Toast;
import cm.aptoide.pt.dataprovider.DataProvider;
import cm.aptoide.pt.dataprovider.repository.IdsRepositoryImpl;
import cm.aptoide.pt.dataprovider.ws.v7.SetUserRequest;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.preferences.secure.SecurePreferencesImplementation;
import cm.aptoide.pt.utils.GenericDialogs;
import cm.aptoide.pt.utils.design.ShowMessage;
import com.jakewharton.rxbinding.view.RxView;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by pedroribeiro on 15/12/16.
 */

public class LoggedInActivity2ndStep extends BaseActivity {

  private static final String TAG = LoggedInActivity2ndStep.class.getSimpleName();

  private Button mContinueButton;
  private Button mPrivateProfile;

  private CompositeSubscription mSubscriptions;
  private Toolbar mToolbar;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(getLayoutId());
    mSubscriptions = new CompositeSubscription();
    bindViews();
    setupToolbar();
    setupListeners();
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    mSubscriptions.clear();
  }

  @Override protected String getActivityTitle() {
    return getString(R.string.create_profile_logged_in_activity_title);
  }

  @Override int getLayoutId() {
    return R.layout.logged_in_second_screen;
  }

  private void setupToolbar() {
    if (mToolbar != null) {
      setSupportActionBar(mToolbar);
      getSupportActionBar().setHomeButtonEnabled(false);
      getSupportActionBar().setDisplayHomeAsUpEnabled(false);
      getSupportActionBar().setTitle(getActivityTitle());
    }
  }

  private void bindViews() {
    mToolbar = (Toolbar) findViewById(R.id.toolbar);
    mContinueButton = (Button) findViewById(R.id.logged_in_continue);
    mPrivateProfile = (Button) findViewById(R.id.logged_in_private_button);
  }

  private void setupListeners() {
    mSubscriptions.add(RxView.clicks(mContinueButton).subscribe(clicks -> {

      ProgressDialog pleaseWaitDialog = GenericDialogs.createGenericPleaseWaitDialog(this,
          getApplicationContext().getString(R.string.please_wait));
      pleaseWaitDialog.show();

      SetUserRequest.of(new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(),
              DataProvider.getContext()).getAptoideClientUUID(), UserAccessState.PUBLIC.toString(),
          AptoideAccountManager.getAccessToken()).execute(answer -> {
        mSubscriptions.add(ShowMessage.asObservableSnack(this, R.string.successful)
            .subscribe(visibility -> {
              if (visibility == ShowMessage.DISMISSED) {
                pleaseWaitDialog.dismiss();
                startActivity(getIntent().setClass(this, CreateStoreActivity.class));
                finish();
              }
            })
        );
      }, throwable -> {
        mSubscriptions.add(ShowMessage.asObservableSnack(this, R.string.unknown_error)
            .subscribe(visibility -> {
              if (visibility == ShowMessage.DISMISSED) {
                pleaseWaitDialog.dismiss();
                startActivity(getIntent().setClass(this, CreateStoreActivity.class));
                finish();
              }
            })
        );
      });
    }));
    mSubscriptions.add(RxView.clicks(mPrivateProfile).subscribe(clicks -> {

      ProgressDialog pleaseWaitDialog = GenericDialogs.createGenericPleaseWaitDialog(this,
          getApplicationContext().getString(R.string.please_wait));
      pleaseWaitDialog.show();

      SetUserRequest.of(new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(),
              DataProvider.getContext()).getAptoideClientUUID(), UserAccessState.UNLISTED.toString(),
          AptoideAccountManager.getAccessToken()).execute(answer -> {
        if (answer.isOk()) {
          Logger.v(TAG, "user is private");
          Toast.makeText(LoggedInActivity2ndStep.this, R.string.successful, Toast.LENGTH_SHORT)
              .show();
        } else {
          Logger.v(TAG, "user is private: error: " + answer.getError().getDescription());
          Toast.makeText(LoggedInActivity2ndStep.this, R.string.unknown_error, Toast.LENGTH_SHORT)
              .show();
        }
        startActivity(getIntent().setClass(this, CreateStoreActivity.class));
        finish();
      }, throwable -> {
        pleaseWaitDialog.show();
        startActivity(getIntent().setClass(this, CreateStoreActivity.class));
        finish();
      });
    }));
  }
}
