package cm.aptoide.pt.spotandshareapp;

import android.graphics.drawable.Drawable;
import cm.aptoide.pt.spotandshare.socket.entities.Friend;
import cm.aptoide.pt.spotandshareandroid.transfermanager.Transfer;

/**
 * Created by filipe on 10-07-2017.
 */

public class TransferAppModel {

  private String appName;
  private String packageName;
  private String filePath;
  private Drawable appIcon;

  private Transfer.State transferState;
  private boolean installed;
  private Friend senderFriend;
  private int hashcode;

  public TransferAppModel(String appName, String packageName, String filePath, Drawable appIcon,
      Transfer.State transferState, Friend senderFriend, int hashcode) {
    this.appName = appName;
    this.packageName = packageName;
    this.filePath = filePath;
    this.appIcon = appIcon;
    this.transferState = transferState;
    this.senderFriend = senderFriend;
    this.hashcode = hashcode;
  }

  public String getAppName() {
    return appName;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getFilePath() {
    return filePath;
  }

  public Drawable getAppIcon() {
    return appIcon;
  }

  public Transfer.State getTransferState() {
    return transferState;
  }

  public Friend getFriend() {
    return senderFriend;
  }

  /**
   * Returns true if the user installed this app, by pressing the install button on the transfer
   * record view.
   *
   * @return boolean
   */
  public boolean isInstalled() {
    return installed;
  }

  public void setInstalledApp(boolean installed) {
    this.installed = installed;
  }

  public int getHashcode() {
    return hashcode;
  }
}