package cm.aptoide.pt.view.account.user;

import cm.aptoide.pt.view.navigator.FragmentNavigator;

class ManageUserNavigator {

  private final FragmentNavigator navigator;

  public ManageUserNavigator(FragmentNavigator navigator) {
    this.navigator = navigator;
  }

  public void toProfileStepOne() {
    navigator.cleanBackStack();
    navigator.navigateTo(ProfileStepOneFragment.newInstance());
  }

  public void goToHome() {
    navigator.navigateToHomeCleaningBackStack();
  }

  public void goBack() {
    navigator.popBackStack();
  }
}
