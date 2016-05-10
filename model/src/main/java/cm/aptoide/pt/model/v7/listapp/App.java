/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 10/05/2016.
 */

package cm.aptoide.pt.model.v7.listapp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import cm.aptoide.pt.model.v7.store.Store;
import lombok.Data;

/**
 * Created by neuro on 22-04-2016.
 */
@Data
public class App {

	private long id;
	private String name;
	@JsonProperty("package") private String packageName;
	private long size;
	private String icon;
	private String graphic;
	private String added;
	private String modified;
	private String updated;
	private String uptype;
	private Store store;
	private File file;
	private Stats stats;

	@Data
	public static class Stats {

		private int apps;         // used on Store items
		private int subscribers;  // used both on App items and Store items
		private int downloads;    // used on listApps, Store items and listAppsVersions
		private Rating rating;       // used on App items and listAppsVersions

		@Data
		public static class Rating {

			private float avg;
			private int total;
			private List<Vote> votes;

			@Data
			public static class Vote {

				private int value;
				private int count;
			}
		}
	}
}
