/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.loader;

import androidx.annotation.Nullable;

/**
 * @author sre
 * 
 */
public class LoaderResult<T> {
	@Nullable
	private T mResult;
	@Nullable
	private String mErrorText;
	private boolean mError;

	public LoaderResult() {
		mError = true;
		mErrorText = null;
		mResult = null;
	}

	public void set(T result) {
		mResult = result;
		mError = false;
		mErrorText = null;
	}

	public void set(String errorText) {
		mResult = null;
		mError = true;
		mErrorText = errorText;
	}

	@Nullable
	public T getResult() {
		return mResult;
	}

	@Nullable
	public String getErrorText() {
		return mErrorText;
	}

	public boolean isError() {
		return mError;
	}
}
