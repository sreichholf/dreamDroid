package net.reichholf.dreamdroid.asynctask;

import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AsyncTaskExecutorService<Params, Progress, Result> {

	private ExecutorService executor;
	private Handler handler;
	private Future future;

	protected AsyncTaskExecutorService() {
		executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});

	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public Handler getHandler() {
		if (handler == null) {
			synchronized (AsyncTaskExecutorService.class) {
				handler = new Handler(Looper.getMainLooper());
			}
		}
		return handler;
	}

	protected void onPreExecute() {
	}

	protected abstract Result doInBackground(Params params);

	protected abstract void onPostExecute(Result result);

	protected void onProgressUpdate(@NotNull Progress value) {
	}

	public void publishProgress(@NotNull Progress value) {
		getHandler().post(() -> onProgressUpdate(value));
	}

	public void execute() {
		execute(null);
	}

	public void execute(Params params) {
		getHandler().post(() -> {
			onPreExecute();
			future = executor.submit(() -> {
				Result result = doInBackground(params);
				getHandler().post(() -> onPostExecute(result));
			});
		});
	}

	public void cancel(boolean mayInterruptIfRunning) {
		if (future != null && !future.isDone())
			future.cancel(mayInterruptIfRunning);
	}

	public boolean isCancelled() {
		return future.isCancelled() || executor == null || executor.isTerminated() || executor.isShutdown();
	}
}