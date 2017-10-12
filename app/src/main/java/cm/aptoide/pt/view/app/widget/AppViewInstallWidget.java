/*
 * Copyright (c) 2016.
 * Modified on 02/09/2016.
 */

package cm.aptoide.pt.view.app.widget;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.AptoideApplication;
import cm.aptoide.pt.BuildConfig;
import cm.aptoide.pt.Install;
import cm.aptoide.pt.InstallManager;
import cm.aptoide.pt.R;
import cm.aptoide.pt.account.view.AccountNavigator;
import cm.aptoide.pt.actions.PermissionManager;
import cm.aptoide.pt.actions.PermissionService;
import cm.aptoide.pt.analytics.Analytics;
import cm.aptoide.pt.app.AppBoughtReceiver;
import cm.aptoide.pt.crashreports.CrashReport;
import cm.aptoide.pt.dataprovider.WebService;
import cm.aptoide.pt.dataprovider.interfaces.TokenInvalidator;
import cm.aptoide.pt.dataprovider.model.v7.GetApp;
import cm.aptoide.pt.dataprovider.model.v7.GetAppMeta;
import cm.aptoide.pt.dataprovider.model.v7.Malware;
import cm.aptoide.pt.dataprovider.model.v7.listapp.App;
import cm.aptoide.pt.dataprovider.model.v7.listapp.ListAppVersions;
import cm.aptoide.pt.dataprovider.ws.BodyInterceptor;
import cm.aptoide.pt.dataprovider.ws.v7.BaseBody;
import cm.aptoide.pt.download.event.DownloadEvent;
import cm.aptoide.pt.download.event.DownloadEventConverter;
import cm.aptoide.pt.download.DownloadFactory;
import cm.aptoide.pt.download.event.DownloadInstallBaseEvent;
import cm.aptoide.pt.download.event.InstallEvent;
import cm.aptoide.pt.download.event.InstallEventConverter;
import cm.aptoide.pt.downloadmanager.base.Download;
import cm.aptoide.pt.downloadmanager.DownloadAction;
import cm.aptoide.pt.install.InstallerFactory;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.notification.NotificationAnalytics;
import cm.aptoide.pt.preferences.managed.ManagerPreferences;
import cm.aptoide.pt.timeline.SocialRepository;
import cm.aptoide.pt.timeline.TimelineAnalytics;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.utils.GenericDialogs;
import cm.aptoide.pt.utils.SimpleSubscriber;
import cm.aptoide.pt.utils.design.ShowMessage;
import cm.aptoide.pt.view.app.AppViewFragment;
import cm.aptoide.pt.view.app.AppViewNavigator;
import cm.aptoide.pt.view.app.displayable.AppViewInstallDisplayable;
import cm.aptoide.pt.view.dialog.SharePreviewDialog;
import cm.aptoide.pt.view.install.InstallWarningDialog;
import cm.aptoide.pt.view.navigator.ActivityResultNavigator;
import cm.aptoide.pt.view.recycler.widget.Widget;
import com.facebook.appevents.AppEventsLogger;
import com.jakewharton.rxbinding.view.RxView;
import okhttp3.OkHttpClient;
import retrofit2.Converter;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created on 06/05/16.
 */
public class AppViewInstallWidget extends Widget<AppViewInstallDisplayable> {

  private static final String TAG = AppViewInstallWidget.class.getSimpleName();

  private RelativeLayout downloadProgressLayout;
  private RelativeLayout installAndLatestVersionLayout;

  private ProgressBar downloadProgress;
  private TextView textProgress;
  private ImageView actionResume;
  private ImageView actionPause;
  private ImageView actionCancel;
  private Button actionButton;

  private TextView versionName;
  private View latestAvailableLayout;
  private View latestAvailableTrustedSeal;
  private View notLatestAvailableText;
  private TextView otherVersions;

  private App trustedVersion;
  private InstallManager installManager;
  private boolean isUpdate;
  private DownloadEventConverter downloadInstallEventConverter;
  private Analytics analytics;
  private InstallEventConverter installConverter;
  private AptoideAccountManager accountManager;
  private AppViewInstallDisplayable displayable;
  private SocialRepository socialRepository;
  private DownloadFactory downloadFactory;
  private PermissionService permissionService;
  private PermissionManager permissionManager;
  private String marketName;
  private boolean createStoreUserPrivacyEnabled;
  private SharedPreferences sharedPreferences;
  private AccountNavigator accountNavigator;
  private AppViewNavigator appViewNavigator;
  private CrashReport crashReport;

  public AppViewInstallWidget(View itemView) {
    super(itemView);
  }

  @Override protected void assignViews(View itemView) {
    downloadProgressLayout = (RelativeLayout) itemView.findViewById(R.id.download_progress_layout);
    installAndLatestVersionLayout =
        (RelativeLayout) itemView.findViewById(R.id.install_and_latest_version_layout);

    downloadProgress = (ProgressBar) itemView.findViewById(R.id.download_progress);
    textProgress = (TextView) itemView.findViewById(R.id.text_progress);
    actionPause = (ImageView) itemView.findViewById(R.id.ic_action_pause);
    actionResume = (ImageView) itemView.findViewById(R.id.ic_action_resume);
    actionCancel = (ImageView) itemView.findViewById(R.id.ic_action_cancel);
    actionButton = (Button) itemView.findViewById(R.id.action_btn);
    versionName = (TextView) itemView.findViewById(R.id.store_version_name);
    otherVersions = (TextView) itemView.findViewById(R.id.other_versions);
    latestAvailableLayout = itemView.findViewById(R.id.latest_available_layout);
    latestAvailableTrustedSeal = itemView.findViewById(R.id.latest_available_icon);
    notLatestAvailableText = itemView.findViewById(R.id.not_latest_available_text);
  }

  @Override public void unbindView() {
    super.unbindView();
    displayable.setInstallButton(null);
    displayable = null;
  }

  @Override public void bindView(AppViewInstallDisplayable displayable) {
    this.displayable = displayable;
    this.displayable.setInstallButton(actionButton);
    crashReport = CrashReport.getInstance();
    accountNavigator = ((ActivityResultNavigator) getContext()).getAccountNavigator();
    createStoreUserPrivacyEnabled =
        ((AptoideApplication) getContext().getApplicationContext()).isCreateStoreUserPrivacyEnabled();
    marketName = ((AptoideApplication) getContext().getApplicationContext()).getMarketName();
    sharedPreferences =
        ((AptoideApplication) getContext().getApplicationContext()).getDefaultSharedPreferences();
    final OkHttpClient httpClient =
        ((AptoideApplication) getContext().getApplicationContext()).getDefaultClient();
    final Converter.Factory converterFactory = WebService.getDefaultConverter();
    accountManager =
        ((AptoideApplication) getContext().getApplicationContext()).getAccountManager();
    installManager = ((AptoideApplication) getContext().getApplicationContext()).getInstallManager(
        InstallerFactory.ROLLBACK);
    BodyInterceptor<BaseBody> bodyInterceptor =
        ((AptoideApplication) getContext().getApplicationContext()).getAccountSettingsBodyInterceptorPoolV7();
    final TokenInvalidator tokenInvalidator =
        ((AptoideApplication) getContext().getApplicationContext()).getTokenInvalidator();
    downloadInstallEventConverter =
        new DownloadEventConverter(bodyInterceptor, httpClient, converterFactory, tokenInvalidator,
            BuildConfig.APPLICATION_ID, sharedPreferences,
            (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE),
            (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE),
            ((AptoideApplication) getContext().getApplicationContext()).getAptoideNavigationTracker());
    installConverter =
        new InstallEventConverter(bodyInterceptor, httpClient, converterFactory, tokenInvalidator,
            BuildConfig.APPLICATION_ID, sharedPreferences,
            (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE),
            (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE),
            ((AptoideApplication) getContext().getApplicationContext()).getAptoideNavigationTracker());
    analytics = Analytics.getInstance();
    downloadFactory = displayable.getDownloadFactory();
    socialRepository =
        new SocialRepository(accountManager, bodyInterceptor, converterFactory, httpClient,
            new TimelineAnalytics(analytics,
                AppEventsLogger.newLogger(getContext().getApplicationContext()), bodyInterceptor,
                httpClient, WebService.getDefaultConverter(), tokenInvalidator,
                BuildConfig.APPLICATION_ID, sharedPreferences,
                new NotificationAnalytics(httpClient, analytics),
                ((AptoideApplication) getContext().getApplicationContext()).getAptoideNavigationTracker()),
            tokenInvalidator, sharedPreferences);

    appViewNavigator = getAppViewNavigator();

    GetApp getApp = this.displayable.getPojo();
    GetAppMeta.App currentApp = getApp.getNodes()
        .getMeta()
        .getData();
    versionName.setText(currentApp.getFile()
        .getVername());

    compositeSubscription.add(RxView.clicks(otherVersions)
        .subscribe(__ -> {
          displayable.getAppViewAnalytics()
              .sendOtherVersionsEvent();
          appViewNavigator.navigateToOtherVersions(currentApp.getName(), currentApp.getIcon(),
              currentApp.getPackageName());
        }, err -> crashReport.log(err)));

    //setup the ui
    compositeSubscription.add(displayable.getInstallState()
        .first()
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(installationProgress -> updateUi(displayable, installationProgress, true, getApp))
        .subscribe(viewUpdated -> {
        }, throwable -> crashReport.log(throwable)));

    //listen ui events
    compositeSubscription.add(displayable.getInstallState()
        .skip(1)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(
            installationProgress -> updateUi(displayable, installationProgress, false, getApp))
        .subscribe(viewUpdated -> {
        }, throwable -> crashReport.log(throwable)));

    if (isThisTheLatestVersionAvailable(currentApp, getApp.getNodes()
        .getVersions())) {
      notLatestAvailableText.setVisibility(View.GONE);
      latestAvailableLayout.setVisibility(View.VISIBLE);
      if (isThisTheLatestTrustedVersionAvailable(currentApp, getApp.getNodes()
          .getVersions())) {
        latestAvailableTrustedSeal.setVisibility(View.VISIBLE);
      }
    } else {
      notLatestAvailableText.setVisibility(View.VISIBLE);
      latestAvailableLayout.setVisibility(View.GONE);
    }

    permissionService = ((PermissionService) getContext());
    permissionManager = new PermissionManager();
  }

  private void updateUi(AppViewInstallDisplayable displayable, Install install, boolean isSetup,
      GetApp getApp) {
    Install.InstallationStatus state = install.getState();
    switch (state) {
      case IN_QUEUE:
        updateInstallingUi(install, getApp.getNodes()
            .getMeta()
            .getData(), isSetup, true);

        break;
      case INSTALLING:
        updateInstallingUi(install, getApp.getNodes()
            .getMeta()
            .getData(), isSetup, !install.isIndeterminate());
        break;
      case INSTALLATION_TIMEOUT:
        if (isSetup) {
          updateUninstalledUi(displayable, getApp, isSetup, install.getType());
        } else {
          updateInstallingUi(install, getApp.getNodes()
              .getMeta()
              .getData(), isSetup, false);
        }
        break;
      case PAUSED:
        updatePausedUi(install, getApp, isSetup);
        break;
      case INSTALLED:
        //current installed version
        updateInstalledUi(install);
        break;
      case UNINSTALLED:
        //App not installed
        updateUninstalledUi(displayable, getApp, isSetup, install.getType());
        break;
      case GENERIC_ERROR:
        updateFailedUi(isSetup, displayable, install, getApp, "",
            getContext().getString(R.string.error_occured));
        break;
      case NOT_ENOUGH_SPACE_ERROR:
        updateFailedUi(isSetup, displayable, install, getApp,
            getContext().getString(R.string.out_of_space_dialog_title),
            getContext().getString(R.string.out_of_space_dialog_message));
        break;
    }
  }

  private void updateFailedUi(boolean isSetup, AppViewInstallDisplayable displayable,
      Install install, GetApp getApp, String errorTitle, String errorMessage) {
    if (isSetup) {
      updateUninstalledUi(displayable, getApp, isSetup, install.getType());
    } else {
      updatePausedUi(install, getApp, isSetup);
      showDialogError(errorTitle, errorMessage);
    }
  }

  @NonNull private void updateUninstalledUi(AppViewInstallDisplayable displayable, GetApp getApp,
      boolean isSetup, Install.InstallationType installationType) {
    GetAppMeta.App app = getApp.getNodes()
        .getMeta()
        .getData();
    setDownloadBarInvisible();
    switch (installationType) {
      case INSTALL:
        setupInstallOrBuyButton(displayable, getApp);
        compositeSubscription.add(displayable.getInstallAppRelay()
            .doOnNext(__ -> actionButton.performClick())
            .subscribe());
        break;
      case UPDATE:
        //update
        isUpdate = true;
        setupActionButton(R.string.appview_button_update, installOrUpgradeListener(app,
            getApp.getNodes()
                .getVersions(), displayable));
        break;
      case DOWNGRADE:
        //downgrade
        setupActionButton(R.string.appview_button_downgrade, downgradeListener(app));
        break;
    }
    setupDownloadControls(app, isSetup, installationType);
  }

  private void updateInstalledUi(Install install) {
    setDownloadBarInvisible();
    setupActionButton(R.string.appview_button_open,
        v -> AptoideUtils.SystemU.openApp(install.getPackageName(),
            getContext().getPackageManager(), getContext()));
  }

  private void updatePausedUi(Install install, GetApp app, boolean isSetup) {

    showProgress(install.getProgress(), install.isIndeterminate());
    actionResume.setVisibility(View.VISIBLE);
    actionPause.setVisibility(View.GONE);
    actionCancel.setVisibility(View.VISIBLE);
    setupDownloadControls(app.getNodes()
        .getMeta()
        .getData(), isSetup, install.getType());
  }

  private void updateInstallingUi(Install install, GetAppMeta.App app, boolean isSetup,
      boolean showControlButtons) {
    showProgress(install.getProgress(), install.isIndeterminate());
    if (showControlButtons) {
      actionResume.setVisibility(View.GONE);
      actionPause.setVisibility(View.VISIBLE);
      actionCancel.setVisibility(View.VISIBLE);
    } else {
      actionResume.setVisibility(View.GONE);
      actionPause.setVisibility(View.GONE);
      actionCancel.setVisibility(View.GONE);
    }
    setupDownloadControls(app, isSetup, install.getType());
  }

  private void showProgress(int progress, boolean isIndeterminate) {
    if (!isDownloadBarVisible()) {
      setDownloadBarVisible();
    }
    downloadProgress.setProgress(progress);
    downloadProgress.setIndeterminate(isIndeterminate);
    textProgress.setText(progress + "%");
  }

  private void setupActionButton(@StringRes int text, View.OnClickListener onClickListener) {
    actionButton.setText(text);
    actionButton.setOnClickListener(onClickListener);
  }

  private void setupInstallOrBuyButton(AppViewInstallDisplayable displayable, GetApp getApp) {
    GetAppMeta.App app = getApp.getNodes()
        .getMeta()
        .getData();

    //check if the app is paid
    if (app.isPaid() && !app.getPay()
        .isPaid()) {
      actionButton.setText(getContext().getString(R.string.appview_button_buy) + " (" + app.getPay()
          .getSymbol() + " " + app.getPay()
          .getPrice() + ")");
      actionButton.setOnClickListener(v -> buyApp(app));
      AppBoughtReceiver receiver = new AppBoughtReceiver() {
        @Override public void appBought(long appId, String path) {
          if (app.getId() == appId) {
            isUpdate = false;
            app.getFile()
                .setPath(path);
            app.getPay()
                .setPaid();
            setupActionButton(R.string.appview_button_install, installOrUpgradeListener(app,
                getApp.getNodes()
                    .getVersions(), displayable));
            actionButton.performClick();
          }
        }
      };
      getContext().registerReceiver(receiver, new IntentFilter(AppBoughtReceiver.APP_BOUGHT));
    } else {
      isUpdate = false;
      setupActionButton(R.string.appview_button_install, installOrUpgradeListener(app,
          getApp.getNodes()
              .getVersions(), displayable));
      if (displayable.isShouldInstall()) {
        actionButton.postDelayed(() -> {
          if (displayable.isVisible() && displayable.isShouldInstall()) {
            actionButton.performClick();
            displayable.setShouldInstall(false);
          }
        }, 1000);
      }
    }
  }

  private void buyApp(GetAppMeta.App app) {
    appViewNavigator.buyApp(app);
  }

  private View.OnClickListener downgradeListener(final GetAppMeta.App app) {
    return view -> {
      final Context context = view.getContext();
      final PermissionService permissionRequest = (PermissionService) getContext();

      permissionRequest.requestAccessToExternalFileSystem(() -> {

        showMessageOKCancel(getContext().getResources()
                .getString(R.string.downgrade_warning_dialog),
            new SimpleSubscriber<GenericDialogs.EResponse>() {

              @Override public void onNext(GenericDialogs.EResponse eResponse) {
                super.onNext(eResponse);
                if (eResponse == GenericDialogs.EResponse.YES) {

                  ShowMessage.asSnack(view, R.string.downgrading_msg);

                  DownloadFactory factory = new DownloadFactory(marketName);
                  Download appDownload = factory.create(app, DownloadAction.DOWNGRADE);
                  showRootInstallWarningPopup(context);
                  compositeSubscription.add(
                      new PermissionManager().requestDownloadAccess(permissionRequest)
                          .flatMap(success -> installManager.install(appDownload)
                              .toObservable()
                              .doOnSubscribe(() -> setupEvents(appDownload)))
                          .observeOn(AndroidSchedulers.mainThread())
                          .subscribe(progress -> {
                            // TODO: 12/07/2017 this code doesnt run
                            Logger.d(TAG, "Installing");
                          }, throwable -> crashReport.log(throwable)));
                  Analytics.Rollback.downgradeDialogContinue();
                } else {
                  Analytics.Rollback.downgradeDialogCancel();
                }
              }
            });
      }, () -> {
        ShowMessage.asSnack(view, R.string.needs_permission_to_fs);
      });
    };
  }

  private void setupEvents(Download download) {
    DownloadEvent report =
        downloadInstallEventConverter.create(download, DownloadEvent.Action.CLICK,
            DownloadEvent.AppContext.APPVIEW);

    analytics.save(report.getPackageName() + report.getVersionCode(), report);

    InstallEvent installEvent =
        installConverter.create(download, DownloadInstallBaseEvent.Action.CLICK,
            DownloadInstallBaseEvent.AppContext.APPVIEW);
    analytics.save(download.getPackageName() + download.getVersionCode(), installEvent);
  }

  private void showRootInstallWarningPopup(Context context) {
    if (installManager.showWarning()) {
      compositeSubscription.add(GenericDialogs.createGenericYesNoCancelMessage(context, null,
          AptoideUtils.StringU.getFormattedString(R.string.root_access_dialog,
              getContext().getResources()))
          .subscribe(eResponses -> {
            switch (eResponses) {
              case YES:
                installManager.rootInstallAllowed(true);
                break;
              case NO:
                installManager.rootInstallAllowed(false);
                break;
            }
          }));
    }
  }

  private void showMessageOKCancel(String message,
      SimpleSubscriber<GenericDialogs.EResponse> subscriber) {
    compositeSubscription.add(
        GenericDialogs.createGenericContinueCancelMessage(getContext(), "", message)
            .subscribe(subscriber));
  }

  public View.OnClickListener installOrUpgradeListener(GetAppMeta.App app,
      ListAppVersions appVersions, AppViewInstallDisplayable displayable) {

    final Context context = getContext();

    @StringRes final int installOrUpgradeMsg =
        this.isUpdate ? R.string.updating_msg : R.string.installing_msg;
    DownloadAction downloadAction = isUpdate ? DownloadAction.UPDATE : DownloadAction.INSTALL;
    final View.OnClickListener installHandler = v -> {
      ManagerPreferences.setNotLoggedInInstallClicks(
          ManagerPreferences.getNotLoggedInInstallClicks(sharedPreferences) + 1, sharedPreferences);
      if (installOrUpgradeMsg == R.string.installing_msg) {
        Analytics.ClickedOnInstallButton.clicked(app);
        displayable.installAppClicked();
      }

      showRootInstallWarningPopup(context);
      compositeSubscription.add(permissionManager.requestDownloadAccess(permissionService)
          .flatMap(success -> permissionManager.requestExternalStoragePermission(permissionService))
          .map(success -> new DownloadFactory(marketName).create(displayable.getPojo()
              .getNodes()
              .getMeta()
              .getData(), downloadAction))
          .flatMapCompletable(download -> {
            if (!displayable.getAppViewFragment()
                .isSuggestedShowing()) {
              displayable.getAppViewFragment()
                  .showSuggestedApps();
            }
            return installManager.install(download)
                .doOnSubscribe(subscription -> setupEvents(download))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnCompleted(() -> {
                  if (accountManager.isLoggedIn() && ManagerPreferences.isShowPreviewDialog(
                      sharedPreferences) && createStoreUserPrivacyEnabled) {
                    SharePreviewDialog sharePreviewDialog =
                        new SharePreviewDialog(displayable, accountManager, true,
                            SharePreviewDialog.SharePreviewOpenMode.SHARE,
                            displayable.getTimelineAnalytics(), sharedPreferences);
                    AlertDialog.Builder alertDialog =
                        sharePreviewDialog.getPreviewDialogBuilder(getContext());

                    sharePreviewDialog.showShareCardPreviewDialog(displayable.getPojo()
                            .getNodes()
                            .getMeta()
                            .getData()
                            .getPackageName(), displayable.getPojo()
                            .getNodes()
                            .getMeta()
                            .getData()
                            .getStore()
                            .getId(), "install", context, sharePreviewDialog, alertDialog,
                        socialRepository);
                  } else if (!accountManager.isLoggedIn()
                      && (ManagerPreferences.getNotLoggedInInstallClicks(sharedPreferences) == 2
                      || ManagerPreferences.getNotLoggedInInstallClicks(sharedPreferences) == 4)) {
                    accountNavigator.navigateToNotLoggedInViewForResult(
                        AppViewFragment.LOGIN_REQUEST_CODE, app);
                  }
                  ShowMessage.asSnack(v, installOrUpgradeMsg);
                });
          })
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(download -> Logger.v(TAG,
              String.format("download progress: %s%", download.getOverallProgress())), err -> {
            if (err instanceof SecurityException) {
              ShowMessage.asSnack(v, R.string.needs_permission_to_fs);
            }
            crashReport.log(err);
          }));
    };

    findTrustedVersion(app, appVersions);
    final boolean hasTrustedVersion = trustedVersion != null;

    final View.OnClickListener onSearchTrustedAppHandler = v -> {
      if (hasTrustedVersion) {
        appViewNavigator.navigateToAppView(trustedVersion.getId(), trustedVersion.getPackageName(),
            "");
        return;
      }
      appViewNavigator.navigateToSearch(app.getName(), true);
    };

    return v -> {
      final Malware.Rank rank = app.getFile()
          .getMalware()
          .getRank();
      if (!Malware.Rank.TRUSTED.equals(rank)) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View alertView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_install_warning, null);
        builder.setView(alertView);
        new InstallWarningDialog(rank, hasTrustedVersion, context, installHandler,
            onSearchTrustedAppHandler, marketName).getDialog()
            .show();
      } else {
        installHandler.onClick(v);
      }
    };
  }

  private void showDialogError(String title, String message) {
    GenericDialogs.createGenericOkMessage(getContext(), title, message)
        .subscribeOn(AndroidSchedulers.mainThread())
        .subscribe(eResponse -> {
        }, throwable -> crashReport.log(throwable));
  }

  private void setupDownloadControls(GetAppMeta.App app, boolean isSetup,
      Install.InstallationType installationType) {
    if (isSetup) {
      DownloadAction actionInstall;
      switch (installationType) {
        case INSTALLED:
          //in case of app is uninstalled inside the appview, the setup won't run again. The unique
          // possible action will be install
        case INSTALL:
          actionInstall = DownloadAction.INSTALL;
          break;
        case UPDATE:
          actionInstall = DownloadAction.UPDATE;
          break;
        case DOWNGRADE:
          actionInstall = DownloadAction.DOWNGRADE;
          break;
        default:
          actionInstall = DownloadAction.INSTALL;
      }
      String md5 = app.getMd5();
      Download download = downloadFactory.create(app, actionInstall);
      actionCancel.setOnClickListener(
          view -> installManager.removeInstallationFile(md5, download.getPackageName(),
              download.getVersionCode()));

      actionPause.setOnClickListener(view -> {
        installManager.stopInstallation(md5);
      });

      actionResume.setOnClickListener(view -> {
        PermissionManager permissionManager = new PermissionManager();
        compositeSubscription.add(permissionManager.requestDownloadAccess(permissionService)
            .flatMap(permissionGranted -> permissionManager.requestExternalStoragePermission(
                (PermissionService) getContext()))
            .flatMap(success -> installManager.install(download)
                .toObservable()
                .doOnSubscribe(() -> setupEvents(download)))
            .subscribe(downloadProgress -> Logger.d(TAG, "Installing"),
                err -> crashReport.log(err)));
      });
    }
  }

  private void setDownloadBarInvisible() {
    installAndLatestVersionLayout.setVisibility(View.VISIBLE);
    downloadProgressLayout.setVisibility(View.GONE);
  }

  private void setDownloadBarVisible() {
    installAndLatestVersionLayout.setVisibility(View.GONE);
    downloadProgressLayout.setVisibility(View.VISIBLE);
  }

  private boolean isDownloadBarVisible() {
    return installAndLatestVersionLayout.getVisibility() == View.GONE
        && downloadProgressLayout.getVisibility() == View.VISIBLE;
  }

  /**
   * Similar to {@link #isThisTheLatestVersionAvailable(GetAppMeta.App, ListAppVersions)
   * isThisTheLatestVersionAvailable} altough this returns true only if
   * the latest version is the same app that we are viewing and the current app is trusted.
   */
  private boolean isThisTheLatestTrustedVersionAvailable(GetAppMeta.App app,
      @Nullable ListAppVersions appVersions) {
    boolean canCompare = appVersions != null
        && appVersions.getList() != null
        && appVersions.getList() != null
        && !appVersions.getList()
        .isEmpty();
    if (canCompare) {
      boolean isLatestVersion = app.getFile()
          .getMd5sum()
          .equals(appVersions.getList()
              .get(0)
              .getFile()
              .getMd5sum());
      if (isLatestVersion) {
        return app.getFile()
            .getMalware()
            .getRank() == Malware.Rank.TRUSTED;
      }
    }
    return false;
  }

  /**
   * Checks if the current app that we are viewing is the latest version available.
   * <p>
   * This is done by comparing the current app md5sum with the first app md5sum in the list of
   * other
   * versions, since the other versions list is sorted using
   * several criterea (vercode, cpu, malware ranking, etc.).
   *
   * @return true if this is the latested version of this app, trusted or not.
   */
  private boolean isThisTheLatestVersionAvailable(GetAppMeta.App app,
      @Nullable ListAppVersions appVersions) {
    boolean canCompare = appVersions != null
        && appVersions.getList() != null
        && appVersions.getList() != null
        && !appVersions.getList()
        .isEmpty();
    if (canCompare) {
      return app.getFile()
          .getMd5sum()
          .equals(appVersions.getList()
              .get(0)
              .getFile()
              .getMd5sum());
    }
    return false;
  }

  private AppViewNavigator getAppViewNavigator() {
    return new AppViewNavigator(getFragmentNavigator(), getActivityNavigator());
  }

  private void findTrustedVersion(GetAppMeta.App app, ListAppVersions appVersions) {

    if (app.getFile() != null
        && app.getFile()
        .getMalware() != null
        && !Malware.Rank.TRUSTED.equals(app.getFile()
        .getMalware()
        .getRank())) {

      for (App version : appVersions.getList()) {
        if (app.getId() != version.getId()
            && version.getFile() != null
            && version.getFile()
            .getMalware() != null
            && Malware.Rank.TRUSTED.equals(version

            .getFile()
            .getMalware()
            .getRank())) {
          trustedVersion = version;
        }
      }
    }
  }
}
