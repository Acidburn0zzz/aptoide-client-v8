/*
 * Copyright (c) 2017.
 * Modified by Marcelo Benites on 18/01/2017.
 */

package cm.aptoide.pt.v8engine.presenter;

import cm.aptoide.pt.v8engine.InstallationProgress;
import java.util.List;

/**
 * Created by marcelobenites on 18/01/17.
 */

public interface MainView extends View {

  void showWizard();

  void showHome();

  boolean showDeepLink();

  void showInstallationError(List<InstallationProgress> installationProgresses);

  void dismissInstallationError();
}
