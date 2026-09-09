package net.reichholf.dreamdroid.asynctask;

import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public abstract class AsyncTaskExecutorService<Params, Progress, Result> {

	private ExecutorService executor;
	private Handler handler;
	private Future<?> future;
	private volatile boolean cancelled;

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
		cancelled = false;
		getHandler().post(() -> {
			if (cancelled) {
				shutdownExecutor();
				return;
			}
			onPreExecute();
			if (cancelled) {
				shutdownExecutor();
				return;
			}
			try {
				future = executor.submit(() -> {
					Result result = doInBackground(params);
					getHandler().post(() -> {
						try {
							if (!cancelled) {
								onPostExecute(result);
							}
						} finally {
							shutdownExecutor();
						}
					});
				});
			} catch (RejectedExecutionException ignored) {
				shutdownExecutor();
			}
		});
	}

	public void cancel(boolean mayInterruptIfRunning) {
		cancelled = true;
		if (future != null && !future.isDone()) {
			future.cancel(mayInterruptIfRunning);
		}
		// If execute() has not submitted yet, its posted callback will see
		// cancelled and shut down. If it already submitted, shut down now.
		if (future != null) {
			shutdownExecutor();
		}
	}

	public boolean isCancelled() {
		if (cancelled) {
			return true;
		}
		if (future != null && future.isCancelled()) {
			return true;
		}
		return executor == null || executor.isTerminated() || executor.isShutdown();
	}

	private void shutdownExecutor() {
		if (executor != null && !executor.isShutdown()) {
			executor.shutdownNow();
		}
	}
}
