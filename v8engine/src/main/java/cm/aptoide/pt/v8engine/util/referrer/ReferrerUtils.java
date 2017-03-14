/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 24/06/2016.
 */

package cm.aptoide.pt.v8engine.util.referrer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cm.aptoide.pt.database.Database;
import cm.aptoide.pt.database.realm.StoredMinimalAd;
import cm.aptoide.pt.dataprovider.DataProvider;
import cm.aptoide.pt.dataprovider.model.MinimalAd;
import cm.aptoide.pt.dataprovider.util.AdMonitor;
import cm.aptoide.pt.dataprovider.util.DataproviderUtils;
import cm.aptoide.pt.dataprovider.util.referrer.SimpleTimedFuture;
import cm.aptoide.pt.dataprovider.ws.v2.aptwords.GetAdsRequest;
import cm.aptoide.pt.dataprovider.ws.v2.aptwords.RegisterAdRefererRequest;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.utils.AptoideUtils;
import io.realm.Realm;
import lombok.Cleanup;

/**
 * Created by neuro on 20-06-2016.
 */
public class ReferrerUtils extends cm.aptoide.pt.dataprovider.util.referrer.ReferrerUtils {

	public static void extractReferrer(MinimalAd minimalAd, final int retries, boolean broadcastReferrer) {

		String packageName = minimalAd.getPackageName();
		long networkId = minimalAd.getNetworkId();
		String clickUrl = minimalAd.getClickUrl();

		if (!AptoideUtils.ThreadU.isUiThread()) {
			throw new RuntimeException("ExtractReferrer must be run on UI thread!");
		}

		final Context context = DataProvider.getContext();

		try {
			Logger.d("ExtractReferrer", "Called for: " + clickUrl + " with packageName " + packageName);

			final String[] internalClickUrl = {clickUrl};
			final SimpleTimedFuture<String> clickUrlFuture = new SimpleTimedFuture<>();

			WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
			WindowManager.LayoutParams params;
			params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager
					.LayoutParams.TYPE_SYSTEM_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);

			params.gravity = Gravity.TOP | Gravity.LEFT;
			params.x = 0;
			params.y = 0;
			params.width = 0;
			params.height = 0;

			LinearLayout view = new LinearLayout(context);
			view.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));

			AptoideUtils.ThreadU.runOnIoThread(() -> {
				internalClickUrl[0] = DataproviderUtils.AdNetworksUtils.parseMacros(clickUrl);
				clickUrlFuture.set(internalClickUrl[0]);
				Logger.d("ExtractReferrer", "Parsed clickUrl: " + internalClickUrl[0]);
			});
			clickUrlFuture.get();
			WebView wv = new WebView(context);
			wv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
			view.addView(wv);
			wv.getSettings().setJavaScriptEnabled(true);
			wv.setWebViewClient(new WebViewClient() {

				public String referrer;
				Future<Void> future;

				@Override
				public boolean shouldOverrideUrlLoading(WebView view, String clickUrl) {

					if (clickUrl.startsWith("market://") || clickUrl.startsWith("https://play.google.com") ||
							clickUrl.startsWith("http://play.google.com")) {
						Logger.d("ExtractReferrer", "Clickurl landed on market");
						 referrer = getReferrer(clickUrl);

						//                        if (simpleFuture != null) {
						//                            simpleFuture.set(referrer);
						//                        }
						Logger.d("ExtractReferrer", "Referrer successfully extracted");

						if (broadcastReferrer) {
							broadcastReferrer(packageName, minimalAd.getAdId(), referrer);
						} else {
							@Cleanup
							Realm realm = Database.get();
							Database.save(new StoredMinimalAd(packageName, referrer, minimalAd.getCpiUrl(), minimalAd.getAdId()), realm);
						}

						future.cancel(false);
						postponeReferrerExtraction(minimalAd, 0, true);

						return true;
					}

					return false;
				}

				@Override
				public void onPageStarted(WebView view, String url, Bitmap favicon) {
					super.onPageStarted(view, url, favicon);

					if (future == null) {
						future = postponeReferrerExtraction(minimalAd, TIME_OUT, retries);
					}
				}

				private ScheduledFuture<Void> postponeReferrerExtraction(MinimalAd minimalAd, int delta, int retries) {
					return postponeReferrerExtraction(minimalAd, delta, false, retries);
				}

				private ScheduledFuture<Void> postponeReferrerExtraction(MinimalAd minimalAd, int delta, boolean success) {
					return postponeReferrerExtraction(minimalAd, delta, success, 0);
				}

				private ScheduledFuture<Void> postponeReferrerExtraction(MinimalAd minimalAd, int delta, final boolean success, final int retries) {
					Logger.d("ExtractReferrer", "Referrer postponed " + delta + " seconds.");

					Callable<Void> callable = () -> {
						Logger.d("ExtractReferrer", "Sending RegisterAdRefererRequest with value " + success);

						RegisterAdRefererRequest.of(minimalAd.getAdId(), minimalAd.getAppId(), minimalAd.getClickUrl(), success).execute();

						Log.d("ExtractReferrer", "Retries left: " + retries);

						if (!success) {
							excludedNetworks.add(packageName, networkId);

							try {

								if (retries > 0) {
									GetAdsRequest.ofSecondTry(packageName)
											.execute(getAdsResponse -> extractReferrer(minimalAd, retries - 1, broadcastReferrer));
								} else {
									// A lista de excluded networks deve ser limpa a cada "ronda"
									excludedNetworks.remove(packageName);
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
							// AdMonitor- Failed to extract referrer.
							AdMonitor.sendReferrerToAdMonitor(minimalAd.getAdId(),referrer, "extractReferrerFailed");
						} else {
							// AdMonitor- referrer successfully extracted.
							AdMonitor.sendReferrerToAdMonitor(minimalAd.getAdId(), referrer, "referrerExtracted");
							// A lista de excluded networks deve ser limpa a cada "ronda"
							excludedNetworks.remove(packageName);
						}

						return null;
					};

					return executorService.schedule(callable, delta, TimeUnit.SECONDS);
				}
			});

			wv.loadUrl(internalClickUrl[0]);
			// AdMonitor- Opened click_url
			AdMonitor.sendDataToAdMonitor(minimalAd.getAdId(), "openedClickUrl");


			windowManager.addView(view, params);
		} catch (Exception e) {
			// TODO: 09-06-2016 neuro
			//            Crashlytics.logException(e);
		}
	}

	private static String getReferrer(String uri) {
		List<NameValuePair> params = URLEncodedUtils.parse(URI.create(uri), "UTF-8");

		String referrer = null;
		for (NameValuePair param : params) {

			if (param.getName().equals("referrer")) {
				referrer = param.getValue();
			}
		}
		return referrer;
	}

	public static void broadcastReferrer(String packageName, long adId, String referrer) {
		Intent i = new Intent("com.android.vending.INSTALL_REFERRER");
		i.setPackage(packageName);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			i.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
		}
		i.putExtra("referrer", referrer);
		DataProvider.getContext().sendBroadcast(i);
		Logger.d("InstalledBroadcastReceiver", "Sent broadcast to " + packageName + " with referrer " + referrer);

		// AdMonitor- referrer broadcasted.
		AdMonitor.sendDataToAdMonitor(adId, "referrerBroadcasted");
	}
}
