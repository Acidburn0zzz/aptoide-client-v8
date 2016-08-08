/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 20/07/2016.
 */

package cm.aptoide.pt.networkclient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.networkclient.interfaces.ErrorRequestListener;
import cm.aptoide.pt.networkclient.interfaces.SuccessRequestListener;
import okhttp3.OkHttpClient;
import retrofit2.CallAdapter;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Base class for webservices
 *
 * @param <T> Interface used to execute the request.
 * @param <U> Returning type of the request.
 */
public abstract class WebService<T, U> {

	protected static ObjectMapper objectMapper;
	private static Converter.Factory defaultConverterFactory;
	protected final Converter.Factory converterFactory;
	private final Class<T> clazz;

	private final String baseHost;

	private final OkHttpClient httpClient;
	private Retrofit retrofit;
	private Observable<T> service;

	protected WebService(Class<T> clazz, OkHttpClient httpClient, Converter.Factory converterFactory, String baseHost) {
		this.httpClient = httpClient;
		this.converterFactory = converterFactory;
		this.clazz = clazz;
		this.baseHost = baseHost;
	}

	public static Converter.Factory getDefaultConverter() {
		if (defaultConverterFactory == null) {
			objectMapper = new ObjectMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
			objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
			objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
			objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

			DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
			objectMapper.setDateFormat(df);
			defaultConverterFactory = JacksonConverterFactory.create(objectMapper);
		}
		return defaultConverterFactory;
	}

	protected Observable<T> getService() {
		return service == null ? createServiceObservable() : service;
	}

	private Observable<T> createServiceObservable() {
		return Observable.fromCallable(this::createService);
	}

	protected T createService() {
		return getRetrofit(httpClient, converterFactory, RxJavaCallAdapterFactory.create(), baseHost).create(clazz);
	}

	private Retrofit getRetrofit(OkHttpClient httpClient, Converter.Factory converterFactory, CallAdapter.Factory factory, String baseHost) {
		if (retrofit == null) {
			retrofit =  new Retrofit.Builder().baseUrl(baseHost)
					.client(httpClient)
					.addCallAdapterFactory(factory)
					.addConverterFactory(converterFactory)
					.build();
		}
		return retrofit;
	}

	protected abstract Observable<U> loadDataFromNetwork(T t, boolean bypassCache);

	private Observable<U> prepareAndLoad(T t, boolean bypassCache) {
		onLoadDataFromNetwork();
		return loadDataFromNetwork(t, bypassCache);
	}

	protected void onLoadDataFromNetwork() {}

	public Observable<U> observe(boolean bypassCache) {
		return getService().flatMap(response -> prepareAndLoad(response, bypassCache))
				.subscribeOn(Schedulers.io());
	}

	public Observable<U> observe() {
		return observe(false);
	}

	public void execute(SuccessRequestListener<U> successRequestListener) {
		execute(successRequestListener, false);
	}

	public void execute(SuccessRequestListener<U> successRequestListener, boolean bypassCache) {
		execute(successRequestListener, defaultErrorRequestListener(), bypassCache);
	}

	public void execute(SuccessRequestListener<U> successRequestListener, ErrorRequestListener errorRequestListener) {
		execute(successRequestListener, errorRequestListener, false);
	}

	public void execute(SuccessRequestListener<U> successRequestListener, ErrorRequestListener errorRequestListener, boolean bypassCache) {
		observe(bypassCache)
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(successRequestListener::call, errorRequestListener::onError);
	}

	protected ErrorRequestListener defaultErrorRequestListener() {

		return (Throwable e) -> {
			// TODO: Implementar
			Logger.e(ErrorRequestListener.class.getName(), "Erro por implementar");
			e.printStackTrace();
		};
	}

	protected boolean isNoNetworkException(Throwable throwable) {
		return throwable instanceof UnknownHostException;
	}
}
