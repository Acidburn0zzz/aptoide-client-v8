/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 08/07/2016.
 */

package cm.aptoide.pt.v8engine.view.timeline.displayable;

import android.content.Context;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import cm.aptoide.pt.model.v7.listapp.App;
import cm.aptoide.pt.model.v7.timeline.Recommendation;
import cm.aptoide.pt.preferences.Application;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.timeline.SocialRepository;
import cm.aptoide.pt.v8engine.timeline.TimelineAnalytics;
import cm.aptoide.pt.v8engine.util.DateCalculator;
import cm.aptoide.pt.v8engine.view.recycler.displayable.Displayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.SpannableFactory;
import cm.aptoide.pt.v8engine.view.timeline.ShareCardCallback;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;

/**
 * Created by marcelobenites on 7/8/16.
 */
public class RecommendationDisplayable extends CardDisplayable {

  public static final String CARD_TYPE_NAME = "RECOMMENDATION";
  @Getter private int avatarResource;
  @Getter private int titleResource;
  @Getter private long appId;
  @Getter private String packageName;
  @Getter private String appName;
  @Getter private String appIcon;
  @Getter private String abUrl;

  private List<String> similarAppsNames;
  private List<String> similarPackageNames;
  private Date date;
  private Date timestamp;
  private DateCalculator dateCalculator;
  private SpannableFactory spannableFactory;
  private TimelineAnalytics timelineAnalytics;
  private SocialRepository socialRepository;
  @Getter private float appRating;

  public RecommendationDisplayable() {
  }

  public RecommendationDisplayable(Recommendation recommendation, int avatarResource,
      int titleResource, long appId, String packageName, String appName, String appIcon,
      String abUrl, List<String> similarAppsNames, List<String> similarPackageNames, Date date,
      Date timestamp, DateCalculator dateCalculator, SpannableFactory spannableFactory,
      TimelineAnalytics timelineAnalytics, SocialRepository socialRepository) {
    super(recommendation);
    this.avatarResource = avatarResource;
    this.titleResource = titleResource;
    this.appId = appId;
    this.packageName = packageName;
    this.appName = appName;
    this.appIcon = appIcon;
    this.abUrl = abUrl;
    this.similarAppsNames = similarAppsNames;
    this.similarPackageNames = similarPackageNames;
    this.date = date;
    this.timestamp = timestamp;
    this.dateCalculator = dateCalculator;
    this.spannableFactory = spannableFactory;
    this.timelineAnalytics = timelineAnalytics;
    this.socialRepository = socialRepository;
    this.appRating = recommendation.getRecommendedApp().getStats().getRating().getAvg();
  }

  public static Displayable from(Recommendation recommendation, DateCalculator dateCalculator,
      SpannableFactory spannableFactory, TimelineAnalytics timelineAnalytics,
      SocialRepository socialRepository) {
    final List<String> similarAppsNames = new ArrayList<>();
    final List<String> similarPackageNames = new ArrayList<>();

    for (App similarApp : recommendation.getSimilarApps()) {
      similarAppsNames.add(similarApp.getName());
      similarPackageNames.add(similarApp.getPackageName());
    }

    String abTestingURL = null;

    if (recommendation.getAb() != null
        && recommendation.getAb().getConversion() != null
        && recommendation.getAb().getConversion().getUrl() != null) {
      abTestingURL = recommendation.getAb().getConversion().getUrl();
    }

    return new RecommendationDisplayable(recommendation, Application.getConfiguration().getIcon(),
        R.string.displayable_social_timeline_recommendation_atptoide_team_recommends,
        recommendation.getRecommendedApp().getId(),
        recommendation.getRecommendedApp().getPackageName(),
        recommendation.getRecommendedApp().getName(), recommendation.getRecommendedApp().getIcon(),
        abTestingURL, similarAppsNames, similarPackageNames,
        recommendation.getRecommendedApp().getUpdated(), recommendation.getTimestamp(),
        dateCalculator, spannableFactory, timelineAnalytics, socialRepository);
  }

  public Spannable getStyledTitle(Context context) {
    String aptoide = Application.getConfiguration().getMarketName();
    return spannableFactory.createColorSpan(getTitle(),
        ContextCompat.getColor(context, R.color.appstimeline_recommends_title), aptoide);
  }

  public String getTitle() {
    return AptoideUtils.StringU.getFormattedString(titleResource,
        Application.getConfiguration().getMarketName());
  }

  public Spannable getSimilarAppsText(Context context) {
    StringBuilder similarAppsText = new StringBuilder(
        context.getString(R.string.displayable_social_timeline_recommendation_similar_to,
            similarAppsNames.get(0)));
    for (int i = 1; i < similarAppsNames.size() - 1; i++) {
      similarAppsText.append(", ");
      similarAppsText.append(similarAppsNames.get(i));
    }
    if (similarAppsNames.size() > 1) {
      similarAppsText.append(" ");
      similarAppsText.append(
          context.getString(R.string.displayable_social_timeline_recommendation_similar_and));
      similarAppsText.append(" ");
      similarAppsText.append(similarAppsNames.get(similarAppsNames.size() - 1));
    }
    return spannableFactory.createStyleSpan(similarAppsText.toString(), Typeface.BOLD,
        similarAppsNames.toArray(new String[similarAppsNames.size()]));
  }

  public Spannable getAppText(Context context) {
    return spannableFactory.createColorSpan(
        context.getString(R.string.displayable_social_timeline_article_get_app_button, ""),
        ContextCompat.getColor(context, R.color.appstimeline_grey), "");
  }

  public String getTimeSinceLastUpdate(Context context) {
    return dateCalculator.getTimeSinceDate(context, date);
  }

  public String getTimeSinceRecommendation(Context context) {
    return dateCalculator.getTimeSinceDate(context, timestamp);
  }

  @Override public int getViewLayout() {
    return R.layout.displayable_social_timeline_recommendation;
  }

  public void sendRecommendedOpenAppEvent() {
    timelineAnalytics.sendRecommendedOpenAppEvent(CARD_TYPE_NAME, TimelineAnalytics.SOURCE_APTOIDE,
        getSimilarAppPackageName(), getPackageName());
  }

  public String getSimilarAppPackageName() {
    if (similarPackageNames.size() != 0) {
      return similarPackageNames.get(0);
    }
    return "";
  }

  public String getSimilarAppName() {
    if (similarAppsNames.size() != 0) {
      return similarAppsNames.get(0);
    }
    return "";
  }

  @Override
  public void share(Context context, boolean privacyResult, ShareCardCallback shareCardCallback) {
    socialRepository.share(getTimelineCard(), context, privacyResult, shareCardCallback);
  }

  @Override public void share(Context context, ShareCardCallback shareCardCallback) {
    socialRepository.share(getTimelineCard(), context, shareCardCallback);
  }

  @Override public void like(Context context, String cardType, int rating) {
    socialRepository.like(getTimelineCard().getCardId(), cardType, "", rating);
  }

  @Override public void like(Context context, String cardId, String cardType, int rating) {
    socialRepository.like(cardId, cardType, "", rating);
  }
}
