/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 06/07/2016.
 */

package cm.aptoide.pt.dataprovider.ws.v7;

/**
 * Created by neuro on 07-06-2016.
 */
public interface OffsetInterface<T> {

	Integer getOffset();

	T setOffset(Integer offset);
}
