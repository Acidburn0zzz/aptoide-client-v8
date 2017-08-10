package cm.aptoide.pt.search;

import cm.aptoide.pt.search.websocket.StoreAutoCompleteWebSocket;
import cm.aptoide.pt.search.websocket.WebSocketManager;

/**
 * Created by pedroribeiro on 30/01/17.
 */

public class StoreSearchSuggestionProvider extends SearchRecentSuggestionsProviderWrapper {

  @Override public String getSearchProvider() {
    return "cm.aptoide.pt.provider.StoreSearchSuggestionProvider";
  }

  @Override public WebSocketManager getWebSocket() {
    return new StoreAutoCompleteWebSocket();
  }
}
