package cm.aptoide.pt.comments;

import cm.aptoide.pt.dataprovider.model.v7.Review;
import lombok.Data;

@Data public final class ReviewWithAppName {
  private final String appName;
  private final Review review;

  public ReviewWithAppName(String appName, Review review) {
    this.appName = appName;
    this.review = review;
  }
}
