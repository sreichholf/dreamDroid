package net.reichholf.dreamdroid.asynctask;

import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AsyncTaskExecutorService<Params, Progress, Result> {

	private ExecutorService executor;
	private Handler handler;

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
		// Override this method whereever you want to perform task before background execution get started
	}

	protected abstract Result doInBackground(Params params);

	protected abstract void onPostExecute(Result result);

	protected void onProgressUpdate(@NotNull Progress value) {
		// Override this method whereever you want update a progress result
	}

	// used for push progress resport to UI
	public void publishProgress(@NotNull Progress value) {
		getHandler().post(() -> onProgressUpdate(value));
	}

	public void execute() {
		execute(null);
	}

	public void execute(Params params) {
		getHandler().post(() -> {
			onPreExecute();
			executor.execute(() -> {
				Result result = doInBackground(params);
				getHandler().post(() -> onPostExecute(result));
			});
		});
	}

	public void cancel(boolean ignored) {
		shutDown();
	}
	public void shutDown() {
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	public boolean isCancelled() {
		return executor == null || executor.isTerminated() || executor.isShutdown();
	}
}