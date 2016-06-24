package cm.aptoide.pt.v8engine.fragment.implementations;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.trello.rxlifecycle.FragmentEvent;

import java.util.Date;
import java.util.List;

import cm.aptoide.pt.dataprovider.ws.v7.GetUserTimelineRequest;
import cm.aptoide.pt.model.v7.listapp.App;
import cm.aptoide.pt.model.v7.listapp.File;
import cm.aptoide.pt.model.v7.timeline.AppUpdateTimelineItem;
import cm.aptoide.pt.model.v7.timeline.Article;
import cm.aptoide.pt.model.v7.timeline.Feature;
import cm.aptoide.pt.model.v7.timeline.GetUserTimeline;
import cm.aptoide.pt.model.v7.timeline.StoreLatestApps;
import cm.aptoide.pt.model.v7.timeline.TimelineItem;
import cm.aptoide.pt.v8engine.fragment.GridRecyclerSwipeFragment;
import cm.aptoide.pt.v8engine.view.recycler.displayable.Displayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.SpannableFactory;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.AppUpdateDisplayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.ArticleDisplayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.DateCalculator;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.FeatureDisplayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.StoreLatestAppsDisplayable;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by marcelobenites on 6/17/16.
 */
public class SocialTimelineFragment extends GridRecyclerSwipeFragment {

	private SpannableFactory spannableFactory;
	private DateCalculator dateCalculator;

	public static SocialTimelineFragment newInstance() {
		SocialTimelineFragment fragment = new SocialTimelineFragment();
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		dateCalculator = new DateCalculator();
		spannableFactory = new SpannableFactory();
	}

	@Override
	public void load(boolean refresh) {
		GetUserTimelineRequest.of().observe(refresh)
				.<GetUserTimeline>compose(bindUntilEvent(FragmentEvent.PAUSE))
				.flatMapIterable(getUserTimeline -> getUserTimeline.getDatalist().getList())
				.filter(timelineItem -> timelineItem != null)
				.map(timelineItem -> timelineItem.getData())
				.filter(item -> (item instanceof Article || item instanceof Feature || item instanceof
						StoreLatestApps || item instanceof App))
				.map(item -> itemToDisplayable(item, dateCalculator, spannableFactory))
				.toList()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(
						displayables -> setDisplayables((List<? extends Displayable>) displayables),
						throwable -> finishLoading((Throwable) throwable)
				);
	}

	@NonNull
	private Displayable itemToDisplayable(Object item, DateCalculator dateCalculator, SpannableFactory spannableFactory) {

		if (item instanceof Article) {
			return new ArticleDisplayable((Article) item, dateCalculator, spannableFactory);
		} else if (item instanceof Feature) {
			return new FeatureDisplayable((Feature) item, dateCalculator);
		} else if (item instanceof StoreLatestApps) {
			return new StoreLatestAppsDisplayable((StoreLatestApps) item, dateCalculator);
		} else if (item instanceof App) {
			return new AppUpdateDisplayable((App) item, spannableFactory);
		}
		throw new IllegalArgumentException("Only articles, features, store latest apps and app updates supported.");
	}
}
