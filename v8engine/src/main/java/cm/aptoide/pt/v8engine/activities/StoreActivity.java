/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 10/05/2016.
 */

package cm.aptoide.pt.v8engine.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;

import com.astuetz.PagerSlidingTabStrip;

import cm.aptoide.pt.dataprovider.ws.v7.store.GetStoreRequest;
import cm.aptoide.pt.dataprovider.ws.v7.store.StoreContext;
import cm.aptoide.pt.model.v7.store.GetStore;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.StorePagerAdapter;
import cm.aptoide.pt.v8engine.V8Engine;
import cm.aptoide.pt.v8engine.activity.AptoideBaseLoaderActivity;

/**
 * Created by neuro on 05-05-2016.
 */
@Deprecated
public class StoreActivity extends AptoideBaseLoaderActivity {

	private String storeName;
	private StoreContext storeContext;
	private Toolbar mToolbar;
	private ViewPager mViewPager;
	private GetStore getStore;

	public static Intent newIntent(String storeName) {
		return newIntent(storeName, StoreContext.store);
	}

	public static Intent newIntent(String storeName, StoreContext storeContext) {
		Intent intent = new Intent(V8Engine.getContext(), StoreActivity.class);
		intent.putExtra(Extras.STORE_NAME, storeName);
		intent.putExtra(Extras.STORE_CONTEXT, storeContext);
		return intent;
	}

	@Override
	protected void loadExtras(Bundle extras) {
		storeName = extras.getString(Extras.STORE_NAME);
		storeContext = (StoreContext) extras.get(Extras.STORE_CONTEXT);
	}

	@Override
	protected void setupViews() {
	}

	@Override
	protected void setupToolbar() {
		if (mToolbar != null) {
			setSupportActionBar(mToolbar);
			getSupportActionBar().setTitle(storeName);
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			mToolbar.setLogo(R.drawable.ic_store);
		}
	}

	@Override
	protected int getContentViewId() {
		return R.layout.store_activity;
	}

	@Override
	protected String getAnalyticsScreenName() {
		// // TODO: 06-05-2016 neuro analytics
		return null;
	}

	@Override
	protected void bindViews() {
		super.bindViews();

		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		mViewPager = (ViewPager) findViewById(R.id.pager);
	}

	@Override
	protected int getViewToShowAfterLoadingId() {
		return R.id.app_bar_layout;
	}

	@Override
	public void load(boolean refresh) {
		if (refresh) {
			GetStoreRequest.of(storeName, storeContext).execute((getStore) -> {
				this.getStore = getStore;
				setupViewPager(getStore);
			});
		} else {
			setupViewPager(getStore);
		}
	}

	private void setupViewPager(GetStore getStore) {
		final PagerAdapter pagerAdapter = new StorePagerAdapter(getSupportFragmentManager(),
				getStore);
		mViewPager.setAdapter(pagerAdapter);

		PagerSlidingTabStrip pagerSlidingTabStrip = (PagerSlidingTabStrip) getView().findViewById
				(R.id.tabs);
		if (pagerSlidingTabStrip != null) {
			pagerSlidingTabStrip.setViewPager(mViewPager);
		}

		finishLoading();
	}

	private static class Extras {

		public static final String STORE_NAME = "storeName";
		public static final String STORE_CONTEXT = "storeContext";
	}
}
