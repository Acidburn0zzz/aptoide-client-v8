/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 02/09/2016.
 */

package cm.aptoide.pt.v8engine.view.recycler.widget.implementations.appView;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import cm.aptoide.pt.actions.PermissionManager;
import cm.aptoide.pt.actions.PermissionRequest;
import cm.aptoide.pt.database.accessors.DeprecatedDatabase;
import cm.aptoide.pt.database.realm.Download;
import cm.aptoide.pt.database.realm.Installed;
import cm.aptoide.pt.database.realm.Update;
import cm.aptoide.pt.dataprovider.model.MinimalAd;
import cm.aptoide.pt.dataprovider.util.DataproviderUtils;
import cm.aptoide.pt.downloadmanager.AptoideDownloadManager;
import cm.aptoide.pt.downloadmanager.DownloadNotFoundException;
import cm.aptoide.pt.downloadmanager.DownloadServiceHelper;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.model.v7.GetApp;
import cm.aptoide.pt.model.v7.GetAppMeta;
import cm.aptoide.pt.model.v7.Malware;
import cm.aptoide.pt.model.v7.listapp.App;
import cm.aptoide.pt.model.v7.listapp.ListAppVersions;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.utils.ShowMessage;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.analytics.Analytics;
import cm.aptoide.pt.v8engine.dialog.InstallWarningDialog;
import cm.aptoide.pt.v8engine.fragment.implementations.AppViewFragment;
import cm.aptoide.pt.v8engine.fragment.implementations.OtherVersionsFragment;
import cm.aptoide.pt.v8engine.fragment.implementations.SearchFragment;
import cm.aptoide.pt.v8engine.interfaces.AppMenuOptions;
import cm.aptoide.pt.v8engine.interfaces.FragmentShower;
import cm.aptoide.pt.v8engine.receivers.AppBoughtReceiver;
import cm.aptoide.pt.v8engine.util.DownloadFactory;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.appView.AppViewInstallDisplayable;
import cm.aptoide.pt.v8engine.view.recycler.widget.Displayables;
import cm.aptoide.pt.v8engine.view.recycler.widget.Widget;
import io.realm.Realm;
import lombok.Cleanup;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by sithengineer on 06/05/16.
 */
@Displayables({AppViewInstallDisplayable.class})
public class AppViewInstallWidget extends Widget<AppViewInstallDisplayable> {

	private static final String TAG = AppViewInstallWidget.class.getSimpleName();

	private RelativeLayout downloadProgressLayout;
	private RelativeLayout installAndLatestVersionLayout;

	//
	// downloading views
	//
	private CheckBox shareInTimeline; // FIXME: 27/07/16 sithengineer what does this flag do ??
	private ProgressBar downloadProgress;
	private TextView textProgress;
	private ImageView actionResume;
	private ImageView actionPause;
	private ImageView actionCancel;

	// get app, upgrade and downgrade button
	private Button actionButton;

	// app info
	private TextView versionName;
	private View latestAvailableText;
	private View latestAvailableTrustedSeal;
	private TextView otherVersions;
	private MinimalAd minimalAd;

	private App trustedVersion;
	private DownloadServiceHelper downloadServiceHelper;
	private PermissionRequest permissionRequest;
	private boolean setupDownloadControlsRunned = false;
	private boolean resumeButtonWasClicked = false;

	//private Subscription subscribe;
	//private long appID;


	public AppViewInstallWidget(View itemView) {
		super(itemView);
	}

	@Override
	protected void assignViews(View itemView) {
		downloadProgressLayout = (RelativeLayout) itemView.findViewById(R.id.download_progress_layout);
		installAndLatestVersionLayout = (RelativeLayout) itemView.findViewById(R.id.install_and_latest_version_layout);
		shareInTimeline = (CheckBox) itemView.findViewById(R.id.share_in_timeline);
		downloadProgress = (ProgressBar) itemView.findViewById(R.id.download_progress);
		textProgress = (TextView) itemView.findViewById(R.id.text_progress);
		actionPause = (ImageView) itemView.findViewById(R.id.ic_action_pause);
		actionResume = (ImageView) itemView.findViewById(R.id.ic_action_resume);
		actionCancel = (ImageView) itemView.findViewById(R.id.ic_action_cancel);
		actionButton = (Button) itemView.findViewById(R.id.action_btn);
		versionName = (TextView) itemView.findViewById(R.id.store_version_name);
		otherVersions = (TextView) itemView.findViewById(R.id.other_versions);
		latestAvailableText = itemView.findViewById(R.id.latest_available_text);
		latestAvailableTrustedSeal = itemView.findViewById(R.id.latest_available_icon);
	}

	@Override
	public void bindView(AppViewInstallDisplayable displayable) {
		//displayable.setOnResumeAction(() -> onViewAttached());
		//displayable.setOnPauseAction(() -> onViewDetached());

		downloadServiceHelper = new DownloadServiceHelper(AptoideDownloadManager.getInstance(), new PermissionManager());
		minimalAd = displayable.getMinimalAd();
		GetApp getApp = displayable.getPojo();
		//appID = getApp.getNodes().getMeta().getData().getId();
		GetAppMeta.App currentApp = getApp.getNodes().getMeta().getData();
		final FragmentShower fragmentShower = ((FragmentShower) getContext());

		versionName.setText(currentApp.getFile().getVername());
		otherVersions.setOnClickListener(v -> {
			OtherVersionsFragment fragment = OtherVersionsFragment.newInstance(currentApp.getName(), currentApp.getIcon(), currentApp.getPackageName());
			fragmentShower.pushFragmentV4(fragment);
		});

		String packageName = currentApp.getPackageName();

		@Cleanup Realm realm = DeprecatedDatabase.get();
		Installed installed = DeprecatedDatabase.InstalledQ.get(packageName, realm);
		Update update = DeprecatedDatabase.UpdatesQ.get(packageName, realm);

		//check if the app is installed or has an update
		if (update != null) {
			// app installed and has a pending update. setup update buttons
			((AppMenuOptions) fragmentShower.getLastV4()).setUnInstallMenuOptionVisible(null);
			actionButton.setText(R.string.update);
			actionButton.setOnClickListener(installOrUpgradeListener(true, currentApp, getApp.getNodes().getVersions(), displayable));

			// setup un-install button as visible in fragment menu
			((AppMenuOptions) fragmentShower.getLastV4()).setUnInstallMenuOptionVisible(() -> {
				displayable.uninstall(getContext(), currentApp).subscribe();
			});
		} else if (update == null && installed != null) {

			// app installed and does not have a pending update. we can show open or downgrade buttons here.
			// it is a downgrade if the appview version is inferior to the installed version
			// it is a open if the appview version is equal to the installed version

			if (currentApp.getFile().getVercode() < installed.getVersionCode()) {
				actionButton.setText(R.string.downgrade);
				actionButton.setOnClickListener(downgradeListener(currentApp, displayable));
			} else {
				actionButton.setText(R.string.open);
				actionButton.setOnClickListener(v -> AptoideUtils.SystemU.openApp(currentApp.getPackageName()));
			}

			// setup un-install button as visible in fragment menu
			((AppMenuOptions) fragmentShower.getLastV4()).setUnInstallMenuOptionVisible(() -> {
				displayable.uninstall(getContext(), currentApp).subscribe();
			});
		} else {
			// app not installed
			setupInstallOrBuyButton(displayable, getApp);

			// setup un-install button as invisible in fragment menu
			((AppMenuOptions) fragmentShower.getLastV4()).setUnInstallMenuOptionVisible(null);
		}

		checkOnGoingDownload(getApp, displayable);

		if (isThisTheLatestVersionAvailable(currentApp, getApp.getNodes().getVersions())) {
			latestAvailableText.setVisibility(View.VISIBLE);
		}

		if (isThisTheLatestTrustedVersionAvailable(currentApp, getApp.getNodes().getVersions())) {
			latestAvailableTrustedSeal.setVisibility(View.VISIBLE);
		}

		ContextWrapper ctx = (ContextWrapper) versionName.getContext();
		permissionRequest = ((PermissionRequest) ctx.getBaseContext());
	}

	@Override
	public void onViewAttached() {
		/*subscribe = AptoideDownloadManager.getInstance().getDownloads()
				.map(downloads -> {
					for (int i = 0; i < downloads.size(); i++) {
						if (downloads.get(i).getAppId() == appID && (downloads.get(i).getOverallDownloadStatus()
								== Download.PROGRESS
								|| downloads.get(i).getOverallDownloadStatus() == Download.PAUSED)) {

							return true;
						}
					}
					return false;
				})
				.distinctUntilChanged()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(showControllers -> {
				}, throwable -> throwable.printStackTrace());*/
	}

	@Override
	public void onViewDetached() {
		actionButton.setOnClickListener(null);
		actionPause.setOnClickListener(null);
		actionCancel.setOnClickListener(null);
		//subscribe.unsubscribe();
	}

	public void checkOnGoingDownload(GetApp getApp, AppViewInstallDisplayable displayable) {
		GetAppMeta.App app = getApp.getNodes().getMeta().getData();
		downloadServiceHelper.getDownload(app.getId()).firstOrDefault(null).subscribe(download -> {
			int downloadStatus = download.getOverallDownloadStatus();
			if ((downloadStatus == Download.PROGRESS || downloadStatus == Download.IN_QUEUE || downloadStatus == Download.PENDING ||
					downloadStatus == Download.PAUSED)) {
				setDownloadBarVisible(true);
				setupDownloadControls(app, download, displayable);
				downloadServiceHelper.getDownload(app.getId()).subscribe(onGoingDownload -> {
					manageDownload(onGoingDownload, displayable, app);
				}, err -> {
					Logger.e(TAG, err);
				});
			}
		}, err -> {
			if(!(err instanceof DownloadNotFoundException)) {
				Logger.e(TAG, err);
			}
			// ignore because download does not exist
		});

		// FIXME: 22/08/16 sithengineer clean the following commented out code
		//		downloadServiceHelper.getAllDownloads().firstOrDefault(Collections.emptyList()).subscribe(downloads -> {
		//			for (Download download : downloads) {
		//				int downloadStatus = download.getOverallDownloadStatus();
		//				if ((downloadStatus == Download.PROGRESS || downloadStatus == Download.IN_QUEUE || downloadStatus == Download.PENDING ||
		// downloadStatus ==
		//						Download.PAUSED) && download
		//						.getAppId() == app.getId()) {
		//					setDownloadBarVisible(true);
		//					setupDownloadControls(app, download, displayable);
		//					downloadServiceHelper.getDownload(app.getId()).subscribe(onGoingDownload -> {
		//						manageDownload(onGoingDownload, displayable, app);
		//					}, err -> {
		//						Logger.e(TAG, err);
		//					});
		//					return;
		//				}
		//			}
		//		}, err -> {
		//			Logger.e(TAG, err);
		//		});
	}

	private void setupInstallOrBuyButton(AppViewInstallDisplayable displayable, GetApp getApp) {
		GetAppMeta.App app = getApp.getNodes().getMeta().getData();

		//check if the app is paid
		if (!app.getPayment().isPaid()) {
			// TODO: 05/08/16 sithengineer replace that for placeholders in resources as soon as we are able to add new strings for translation
			actionButton.setText(getContext().getString(R.string.buy) + " (" + app.getPayment().getPrice()+ ")");
			actionButton.setOnClickListener(v -> displayable.buyApp(getContext(), app));
			AppBoughtReceiver receiver = new AppBoughtReceiver() {
				@Override
				public void appBought(long appId) {
					if (app.getId() == appId) {
						actionButton.setText(R.string.install);
						actionButton.setOnClickListener(installOrUpgradeListener(false, app, getApp.getNodes().getVersions(), displayable));
						actionButton.performClick();
					}
				}
			};
			getContext().registerReceiver(receiver, new IntentFilter(AppBoughtReceiver.APP_BOUGHT));
		} else {
			actionButton.setText(R.string.install);
			actionButton.setOnClickListener(installOrUpgradeListener(false, app, getApp.getNodes().getVersions(), displayable));
			if (displayable.isShouldInstall()) {
				actionButton.postDelayed(() -> {
					if (displayable.isVisible()) {
						actionButton.performClick();
					}
				}, 1000);
			}
		}
	}

	private View.OnClickListener downgradeListener(final GetAppMeta.App app, AppViewInstallDisplayable displayable) {
		return view -> {
			final Context context = view.getContext();
			ContextWrapper contextWrapper = (ContextWrapper) context;
			final PermissionRequest permissionRequest = ((PermissionRequest) contextWrapper.getBaseContext());

			permissionRequest.requestAccessToExternalFileSystem(() -> {
				ShowMessage.asSnack(view, R.string.downgrading_msg);

				DownloadFactory factory = new DownloadFactory();
				Download appDownload = factory.create(app);
				downloadServiceHelper.startDownload(permissionRequest, appDownload).subscribe(download -> {
					if (download.getOverallDownloadStatus() == Download.COMPLETED) {
						//final String packageName = app.getPackageName();
						//final FileToDownload downloadedFile = download.getFilesToDownload().get(0);
						displayable.downgrade(getContext(), app).subscribe();
					}
				});
			}, () -> {
				ShowMessage.asSnack(view, R.string.needs_permission_to_fs);
			});
		};
	}

	public View.OnClickListener installOrUpgradeListener(boolean isUpdate, GetAppMeta.App app, ListAppVersions appVersions, AppViewInstallDisplayable
			displayable) {


		final Context context = getContext();

		@StringRes
		final int installOrUpgradeMsg = isUpdate ? R.string.updating_msg : R.string.installing_msg;
		final View.OnClickListener installHandler = v -> {

			if (minimalAd != null && minimalAd.getCpdUrl() != null) {
				DataproviderUtils.AdNetworksUtils.knockCpd(minimalAd);
			}

			if(installOrUpgradeMsg == R.string.installing_msg) {
				Analytics.ClickedOnInstallButton.clicked(app);
			}

			DownloadFactory factory = new DownloadFactory();
			Download appDownload = factory.create(app);

			downloadServiceHelper.startDownload(permissionRequest, appDownload).subscribe(download -> {
				manageDownload(download, displayable, app);
				if(!setupDownloadControlsRunned) {
					// TODO: 09/09/16 refactor this
					ShowMessage.asSnack(v, installOrUpgradeMsg);
					setupDownloadControls(app, appDownload, displayable);
				}
			}, err -> {
				if (err instanceof SecurityException) {
					ShowMessage.asSnack(v, R.string.needs_permission_to_fs);
				}

				Logger.e(TAG, err);
			});

		};

		findTrustedVersion(app, appVersions);
		final boolean hasTrustedVersion = trustedVersion != null;

		final View.OnClickListener onSearchHandler = v -> {
			Fragment fragment;
			if (hasTrustedVersion) {
				// go to app view of the trusted version
				fragment = AppViewFragment.newInstance(trustedVersion.getId());
			} else {
				// search for a trusted version
				fragment = SearchFragment.newInstance(app.getName(), true);
			}
			((FragmentShower) context).pushFragmentV4(fragment);
		};

		return v -> {
			final Malware.Rank rank = app.getFile().getMalware().getRank();
			if (!Malware.Rank.TRUSTED.equals(rank)) {
				AlertDialog.Builder builder = new AlertDialog.Builder(context);
				View alertView = LayoutInflater.from(context).inflate(R.layout.dialog_install_warning, null);
				builder.setView(alertView);
				new InstallWarningDialog(rank, hasTrustedVersion, context, installHandler, onSearchHandler).getDialog().show();
			} else {
				installHandler.onClick(v);
			}
		};
	}

	private void manageDownload(Download download, AppViewInstallDisplayable displayable, GetAppMeta.App app) {

		Context ctx = getContext();

		switch (download.getOverallDownloadStatus()) {

			case Download.PAUSED: {
				actionResume.setVisibility(View.VISIBLE);
				actionPause.setVisibility(View.GONE);
				break;
			}

			case Download.IN_QUEUE:
			case Download.PROGRESS: {
				downloadProgress.setProgress(download.getOverallProgress());
				//textProgress.setText(download.getOverallProgress() + "% - " + AptoideUtils.StringU.formatBits((long) download.getSpeed()) +
				// "/s");
				textProgress.setText(download.getOverallProgress() + "%");
				break;
			}

			case Download.ERROR: {
				setDownloadBarVisible(false);
				break;
			}

			case Download.COMPLETED: {
				Analytics.DownloadComplete.downloadComplete(app);

				setDownloadBarVisible(false);

				displayable.install(ctx, app).observeOn(AndroidSchedulers.mainThread()).subscribe(success -> {
					if (actionButton.getVisibility() == View.VISIBLE) {
						actionButton.setText(R.string.open);
						// FIXME: 20/07/16 sithengineer refactor this ugly code
						if (displayable.isVisible()) {
							((AppMenuOptions) ((FragmentShower) ctx).getLastV4()).setUnInstallMenuOptionVisible(() -> {
								displayable.uninstall(ctx, app).subscribe();
							});
						}
					}
				});
				break;
			}
		}
	}

	private void setupDownloadControls(GetAppMeta.App app, Download download, AppViewInstallDisplayable displayable) {
		setupDownloadControlsRunned = true;
		long appId = app.getId();

		actionCancel.setOnClickListener(view -> {
			downloadServiceHelper.removeDownload(appId);
			setDownloadBarVisible(false);
		});

		actionPause.setOnClickListener(view -> {
			downloadServiceHelper.pauseDownload(appId);
			actionResume.setVisibility(View.VISIBLE);
			actionPause.setVisibility(View.GONE);
			resumeButtonWasClicked=false;
		});

		actionResume.setOnClickListener(view -> {
			downloadServiceHelper.startDownload(permissionRequest, download)
					.subscribe(onGoingDownload -> {
								manageDownload(onGoingDownload, displayable, app);
								if(!resumeButtonWasClicked) {
									// TODO: 09/09/16 refactor me
									actionResume.setVisibility(View.GONE);
									actionPause.setVisibility(View.VISIBLE);
									resumeButtonWasClicked = true;
								}}
							, err -> {
								Logger.e(TAG, err);
							});
		});

		setDownloadBarVisible(true);
		switch (download.getOverallDownloadStatus()) {
			case Download.PAUSED:
				downloadProgress.setProgress(download.getOverallProgress());
				textProgress.setText(download.getOverallProgress() + "%");
				actionResume.setVisibility(View.VISIBLE);
				actionPause.setVisibility(View.GONE);
			default:
				actionResume.setVisibility(View.GONE);
				actionPause.setVisibility(View.VISIBLE);
		}
	}

	private void setDownloadBarVisible(boolean visible) {
		installAndLatestVersionLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
		downloadProgressLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	/**
	 * Similar to {@link #isThisTheLatestVersionAvailable(GetAppMeta.App, ListAppVersions) isThisTheLatestVersionAvailable} altough this returns true only if
	 * the latest version is the same app that we are viewing and the current app is trusted.
	 *
	 * @param app
	 * @param appVersions
	 *
	 * @return
	 */
	private boolean isThisTheLatestTrustedVersionAvailable(GetAppMeta.App app, @Nullable ListAppVersions appVersions) {
		boolean canCompare = appVersions != null && appVersions.getList() != null && appVersions.getList() != null && !appVersions.getList().isEmpty();
		if (canCompare) {
			boolean isLatestVersion = app.getFile().getMd5sum().equals(appVersions.getList().get(0).getFile().getMd5sum());
			if (isLatestVersion) {
				return app.getFile().getMalware().getRank()== Malware.Rank.TRUSTED;
			}
		}
		return false;
	}

	/**
	 * Checks if the current app that we are viewing is the latest version available.
	 * <p>
	 * This is done by comparing the current app md5sum with the first app md5sum in the list of other versions, since the other versions list is sorted using
	 * several criterea (vercode, cpu, malware ranking, etc.).
	 *
	 * @param app
	 * @param appVersions
	 *
	 * @return true if this is the latested version of this app, trusted or not.
	 */
	private boolean isThisTheLatestVersionAvailable(GetAppMeta.App app, @Nullable ListAppVersions appVersions) {
		boolean canCompare = appVersions != null && appVersions.getList() != null && appVersions.getList() != null && !appVersions.getList().isEmpty();
		if (canCompare) {
			return app.getFile().getMd5sum().equals(appVersions.getList().get(0).getFile().getMd5sum());
		}
		return false;
	}

	private void findTrustedVersion(GetAppMeta.App app, ListAppVersions appVersions) {

		if (app.getFile() != null && app.getFile().getMalware() != null && !Malware.Rank.TRUSTED.equals(app.getFile().getMalware().getRank())) {

			for (App version : appVersions.getList()) {
				if (app.getId() != version.getId() && version.getFile() != null && version.getFile().getMalware() != null && Malware.Rank.TRUSTED.equals
						(version

								.getFile().getMalware().getRank())) {
					trustedVersion = version;
				}
			}
		}
	}
}
