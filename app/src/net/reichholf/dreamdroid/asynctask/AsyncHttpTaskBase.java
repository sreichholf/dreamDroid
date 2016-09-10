package net.reichholf.dreamdroid.asynctask;

import android.content.Context;
import android.os.AsyncTask;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;

import java.lang.ref.WeakReference;

/**
 * Created by Stephan on 27.12.2015.
 */
public abstract class AsyncHttpTaskBase<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
	protected WeakReference<AsyncHttpTaskBaseHandler> mTaskHandler;
	private SimpleHttpClient mShc;
	public AsyncHttpTaskBase(AsyncHttpTaskBaseHandler taskHandler) {
		mTaskHandler = new WeakReference<>(taskHandler);
	}

	protected SimpleHttpClient getHttpClient() {
		if (mShc == null)
			mShc = SimpleHttpClient.getInstance();
		return mShc;
	}

	protected String getErrorText() {
		AsyncHttpTaskBaseHandler resultHandler = mTaskHandler.get();
		if (resultHandler == null)
			return null;
		String errorText;
		if (getHttpClient().hasError())
			errorText = resultHandler.getString(R.string.get_content_error) + "\n" + getHttpClient().getErrorText(resultHandler.getContext());
		else
			errorText = resultHandler.getString(R.string.get_content_error);
		return errorText;
	}

	public interface AsyncHttpTaskBaseHandler {
		String getString(int resId);
		Context getContext();
	}
}
