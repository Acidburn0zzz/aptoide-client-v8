/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 17/08/2016.
 */

package cm.aptoide.pt.v8engine.view.recycler.widget.implementations.appView;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import cm.aptoide.pt.model.v7.GetAppMeta;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.fragment.implementations.DescriptionFragment;
import cm.aptoide.pt.v8engine.interfaces.FragmentShower;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.appView.AppViewDescriptionDisplayable;
import cm.aptoide.pt.v8engine.view.recycler.widget.Displayables;
import cm.aptoide.pt.v8engine.view.recycler.widget.Widget;
import com.jakewharton.rxbinding.view.RxView;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by sithengineer on 10/05/16.
 */
@Displayables({ AppViewDescriptionDisplayable.class }) public class AppViewDescriptionWidget
    extends Widget<AppViewDescriptionDisplayable> {

  private TextView descriptionTextView;
  private Button readMoreBtn;
  private String storeName;
  private CompositeSubscription subscriptions;
  private GetAppMeta.Media media;
  private GetAppMeta.App app;

  public AppViewDescriptionWidget(View itemView) {
    super(itemView);
  }

  @Override protected void assignViews(View itemView) {
    descriptionTextView = (TextView) itemView.findViewById(R.id.description);
    readMoreBtn = (Button) itemView.findViewById(R.id.read_more_button);
  }

  @Override public void bindView(AppViewDescriptionDisplayable displayable) {
    this.app = displayable.getPojo().getNodes().getMeta().getData();
    this.media = app.getMedia();
    this.storeName = app.getStore().getName();
  }

  @Override public void onViewAttached() {
    if (subscriptions == null) {
      subscriptions = new CompositeSubscription();
      if (!TextUtils.isEmpty(media.getDescription())) {
        descriptionTextView.setText(AptoideUtils.HtmlU.parse(media.getDescription()));
        subscriptions.add(RxView.clicks(readMoreBtn).subscribe(click -> {
          ((FragmentShower) getContext()).pushFragmentV4(
              DescriptionFragment.newInstance(app.getId(), storeName));
        }));
      } else {
        // only show "default" description if the app doesn't have one
        descriptionTextView.setText(R.string.description_not_available);
        readMoreBtn.setVisibility(View.GONE);
      }
    }
  }

  @Override public void onViewDetached() {
    if (subscriptions != null) {
      subscriptions.unsubscribe();
      subscriptions = null;
    }
  }
}
