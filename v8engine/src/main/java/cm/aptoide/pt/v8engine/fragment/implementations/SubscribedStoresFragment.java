/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 02/09/2016.
 */

package cm.aptoide.pt.v8engine.fragment.implementations;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.crashreports.CrashReports;
import cm.aptoide.pt.database.realm.Store;
import cm.aptoide.pt.dataprovider.DataProvider;
import cm.aptoide.pt.dataprovider.repository.IdsRepositoryImpl;
import cm.aptoide.pt.dataprovider.util.DataproviderUtils;
import cm.aptoide.pt.dataprovider.ws.v7.BaseRequestWithStore;
import cm.aptoide.pt.dataprovider.ws.v7.MyStoreRequest;
import cm.aptoide.pt.dataprovider.ws.v7.WSWidgetsUtils;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.model.v7.GetStoreWidgets;
import cm.aptoide.pt.model.v7.MyStore;
import cm.aptoide.pt.model.v7.Type;
import cm.aptoide.pt.model.v7.store.ListStores;
import cm.aptoide.pt.preferences.secure.SecurePreferencesImplementation;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.V8Engine;
import cm.aptoide.pt.v8engine.fragment.GridRecyclerSwipeFragment;
import cm.aptoide.pt.v8engine.repository.RepositoryFactory;
import cm.aptoide.pt.v8engine.repository.StoreRepository;
import cm.aptoide.pt.v8engine.util.StoreUtils;
import cm.aptoide.pt.v8engine.view.recycler.displayable.Displayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.DisplayablesFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;

import static cm.aptoide.pt.model.v7.store.Store.Appearance;

/**
 * Created by neuro on 11-05-2016.
 */
public class SubscribedStoresFragment extends GridRecyclerSwipeFragment {

  private static final String TAG = SubscribedStoresFragment.class.getName();
  private Subscription subscription;
  private StoreRepository storeRepository;

  public static SubscribedStoresFragment newInstance() {
    return new SubscribedStoresFragment();
  }

  @Override public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    storeRepository = RepositoryFactory.getRepositoryFor(Store.class);
  }

  @Override public void load(boolean create, boolean refresh, Bundle savedInstanceState) {
    if (subscription != null && !subscription.isUnsubscribed()) {
      subscription.unsubscribe();
    }
    subscription = loadStores(!refresh).compose(bindUntilEvent(LifecycleEvent.DESTROY_VIEW))
        .subscribe(displayables -> setDisplayables(displayables), err -> {
          Logger.e(TAG, err);
          CrashReports.logException(err);
        });
  }

  private Observable<List<Displayable>> loadStores(boolean refresh) {
    return loadLocalSubscribedStores().flatMap(stores -> loadDataFromNetwork(refresh).flatMap(
        myStore -> joinLocalAndRemoteSubscribedStores(myStore.getWidgets(), stores))
        .map(widgets -> DisplayablesFactory.parse(widgets,
            V8Engine.getConfiguration().getDefaultTheme()))).onErrorReturn(throwable -> {
      CrashReports.logException(throwable);
      Logger.e(TAG, "loadStores: " + throwable);
      return Collections.emptyList();
    });
  }

  @NonNull private Observable<MyStore> loadDataFromNetwork(boolean refresh) {
    return MyStoreRequest.of().observe(refresh).observeOn(Schedulers.io()).map(myStore -> {
      List<GetStoreWidgets.WSWidget> list = myStore.getWidgets().getDatalist().getList();
      // Load sub nodes
      CountDownLatch countDownLatch = new CountDownLatch(list.size());
      Observable.from(list)
          .observeOn(Schedulers.io())
          .forEach(wsWidget -> WSWidgetsUtils.loadInnerNodes(wsWidget,
              wsWidget.getView() != null ? StoreUtils.getStoreCredentialsFromUrl(wsWidget.getView())
                  : new BaseRequestWithStore.StoreCredentials(), countDownLatch, refresh,
              throwable -> countDownLatch.countDown(), AptoideAccountManager.getAccessToken(),
              AptoideAccountManager.getUserEmail(),
              new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(),
                  DataProvider.getContext()).getAptoideClientUUID(),
              DataproviderUtils.AdNetworksUtils.isGooglePlayServicesAvailable(
                  V8Engine.getContext()), DataProvider.getConfiguration().getPartnerId(),
              !TextUtils.isEmpty(AptoideAccountManager.getUserData().getUserRepo())));

      try {
        countDownLatch.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return myStore;
    });
  }

  @NonNull
  private Observable<GetStoreWidgets> joinLocalAndRemoteSubscribedStores(GetStoreWidgets wsWidgets,
      List<cm.aptoide.pt.model.v7.store.Store> stores) {
    return Observable.from(wsWidgets.getDatalist().getList())
        .filter(wsWidget -> wsWidget.getType() == Type.MY_STORES_SUBSCRIBED)
        .flatMap(wsWidget -> Observable.just(
            ((ListStores) wsWidget.getViewObject()).getDatalist().getList()).map(storeList -> {
          List<cm.aptoide.pt.model.v7.store.Store> list = new ArrayList<>(storeList);
          Collections.copy(list, storeList);
          list.addAll(stores);
          Collections.sort(list, (store, t1) -> store.getName().compareTo(t1.getName()));
          while (list.size() > wsWidgets.getDatalist().getCount()) {
            list.remove(list.size() - 1);
          }
          ((ListStores) wsWidget.getViewObject()).getDatalist().setList(list);
          return wsWidgets;
        }));
  }

  private Observable<List<cm.aptoide.pt.model.v7.store.Store>> loadLocalSubscribedStores() {
    return storeRepository.getAll().flatMap(stores -> Observable.from(stores).map(store -> {
      cm.aptoide.pt.model.v7.store.Store nwStore = new cm.aptoide.pt.model.v7.store.Store();
      nwStore.setName(store.getStoreName());
      nwStore.setId(store.getStoreId());
      nwStore.setAppearance(new Appearance().setTheme(store.getTheme()));
      return nwStore;
    }).toList());
  }

  @Override public int getContentViewId() {
    return R.layout.my_stores_layout_fragment;
  }

  @Override public void bindViews(View view) {
    super.bindViews(view);
  }
}
