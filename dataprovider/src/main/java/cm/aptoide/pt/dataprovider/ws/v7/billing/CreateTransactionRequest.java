package cm.aptoide.pt.dataprovider.ws.v7.billing;

import android.content.SharedPreferences;
import cm.aptoide.pt.dataprovider.BuildConfig;
import cm.aptoide.pt.dataprovider.interfaces.TokenInvalidator;
import cm.aptoide.pt.dataprovider.model.v7.BaseV7Response;
import cm.aptoide.pt.dataprovider.ws.BodyInterceptor;
import cm.aptoide.pt.dataprovider.ws.v7.BaseBody;
import cm.aptoide.pt.dataprovider.ws.v7.V7;
import cm.aptoide.pt.preferences.toolbox.ToolboxManager;
import okhttp3.OkHttpClient;
import retrofit2.Converter;
import rx.Observable;

public class CreateTransactionRequest
    extends V7<CreateTransactionRequest.ResponseBody, CreateTransactionRequest.RequestBody> {

  private CreateTransactionRequest(RequestBody body, String baseHost, OkHttpClient httpClient,
      Converter.Factory converterFactory, BodyInterceptor bodyInterceptor,
      TokenInvalidator tokenInvalidator) {
    super(body, baseHost, httpClient, converterFactory, bodyInterceptor, tokenInvalidator);
  }

  public static String getHost(SharedPreferences sharedPreferences) {
    return (ToolboxManager.isToolboxEnableHttpScheme(sharedPreferences) ? "http"
        : BuildConfig.APTOIDE_WEB_SERVICES_SCHEME)
        + "://"
        + BuildConfig.APTOIDE_WEB_SERVICES_WRITE_V7_HOST
        + "/api/7/";
  }

  public static CreateTransactionRequest of(long productId, long paymentMethodId,
      BodyInterceptor<BaseBody> bodyInterceptor, OkHttpClient httpClient,
      Converter.Factory converterFactory, TokenInvalidator tokenInvalidator,
      SharedPreferences sharedPreferences) {
    final RequestBody body = new RequestBody();
    body.setProductId(productId);
    body.setAuthorizationId(paymentMethodId);
    return new CreateTransactionRequest(body, getHost(sharedPreferences), httpClient,
        converterFactory, bodyInterceptor, tokenInvalidator);
  }

  @Override protected Observable<ResponseBody> loadDataFromNetwork(Interfaces interfaces,
      boolean bypassCache) {
    return interfaces.createBillingTransaction(body, bypassCache);
  }

  public static class RequestBody extends BaseBody {

    private long productId;
    private long authorizationId;

    public long getProductId() {
      return productId;
    }

    public void setProductId(long productId) {
      this.productId = productId;
    }

    public long getAuthorizationId() {
      return authorizationId;
    }

    public void setAuthorizationId(long authorizationId) {
      this.authorizationId = authorizationId;
    }

  }

  public static class ResponseBody extends BaseV7Response {

    private GetTransactionRequest.ResponseBody.Transaction data;

    public GetTransactionRequest.ResponseBody.Transaction getData() {
      return data;
    }

    public void setData(GetTransactionRequest.ResponseBody.Transaction data) {
      this.data = data;
    }
  }
}
