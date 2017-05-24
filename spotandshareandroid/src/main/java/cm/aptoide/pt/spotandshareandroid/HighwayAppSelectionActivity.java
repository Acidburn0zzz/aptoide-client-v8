package cm.aptoide.pt.spotandshareandroid;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.Toast;
import java.util.List;

public class HighwayAppSelectionActivity extends ActivityView implements HighwayAppSelectionView {

  private boolean isHotspot;
  private HighwayAppSelectionCustomAdapter adapter;
  private FloatingActionButton sendButton;
  private GridView gridView;
  private ProgressBar progressBar;
  private HighwayAppSelectionPresenter presenter;
  private ApplicationProvider applicationProvider;
  private ApplicationSender applicationSender;
  private Toolbar mToolbar;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_app_selection);
    gridView = (GridView) findViewById(R.id.HighwayGridView);
    progressBar = (ProgressBar) findViewById(R.id.appSelectionProgressBar);
    mToolbar = (Toolbar) findViewById(R.id.shareAppsToolbar);

    gridView.setSelector(new ColorDrawable(Color.BLACK));

    sendButton = (FloatingActionButton) findViewById(R.id.sendButton);

    isHotspot = getIntent().getBooleanExtra("isAHotspot", false);
    setUpToolbar();

    applicationProvider = new ApplicationProvider(this);
    applicationSender = ApplicationSender.getInstance(this, isHotspot);

    presenter =
        new HighwayAppSelectionPresenter(applicationProvider, applicationSender, this, isHotspot);
    attachPresenter(presenter);
  }

  private void setUpToolbar() {
    if (mToolbar != null) {
      setSupportActionBar(mToolbar);
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setHomeButtonEnabled(true);
      getSupportActionBar().setDisplayShowTitleEnabled(true);
      getSupportActionBar().setTitle(getResources().getString(R.string.spot_share));
    }
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    int i = item.getItemId();
    //    todo add check for the right button
    finish();

    return super.onOptionsItemSelected(item);
  }

  @Override public void onBackPressed() {
    super.onBackPressed();
  }

  @Override protected void onPause() {
    super.onPause();
  }

  @Override protected void onResume() {
    super.onResume();
  }

  @Override protected void onDestroy() {
    adapter.removeListener();
    presenter.onDestroy();
    super.onDestroy();
  }

  @Override public void setUpSendListener() {
    sendButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        presenter.clickedSendButton();
      }
    });
  }

  @Override public void showNoAppsSelectedToast() {
    Toast.makeText(HighwayAppSelectionActivity.this, HighwayAppSelectionActivity.this.getResources()
        .getString(R.string.noSelectedAppsToSend), Toast.LENGTH_SHORT)
        .show();
  }

  @Override public void enableGridView(boolean enable) {
    progressBar.setVisibility(View.GONE);
    gridView.setVisibility(View.VISIBLE);
  }

  @Override public void generateAdapter(boolean isHotspot, List<AppViewModel> itemList) {
    if (isHotspot) {
      adapter = new HighwayAppSelectionCustomAdapter(this, gridView.getContext(), itemList, true);
    } else {
      adapter = new HighwayAppSelectionCustomAdapter(this, gridView.getContext(), itemList, false);
    }

    gridView.setDrawSelectorOnTop(false);

    gridView.setAdapter(adapter);
  }

  @Override public void setAppSelectionListener(AppSelectionListener listener) {
    adapter.setListener(listener);
  }

  @Override public void removeAppSelectionListener() {
    adapter.removeListener();
  }

  @Override public void notifyChanges() {
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  @Override public void goBackToTransferRecord() {
    finish();
  }
}
