package net.reichholf.dreamdroid.asynctask;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;

public class GetLocationsAndTagsTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	public GetLocationsAndTagsTask(AsyncHttpTaskBaseHandler taskHandler) {
		super(taskHandler);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		GetLocationsAndTagsTaskHandler taskHandler = (GetLocationsAndTagsTaskHandler) mTaskHandler.get();
		if (taskHandler == null)
			return false;
		if (DreamDroid.getLocations().size() == 0) {
			if (isCancelled())
				return false;
			publishProgress(taskHandler.getString(R.string.locations) + " - " + taskHandler.getString(R.string.fetching_data));
			DreamDroid.loadLocations(getHttpClient());
		}

		if (DreamDroid.getTags().size() == 0) {
			if (isCancelled())
				return false;
			publishProgress(taskHandler.getString(R.string.tags) + " - " + taskHandler.getString(R.string.fetching_data));
			DreamDroid.loadTags(getHttpClient());
		}

		return true;
	}

	@Override
	protected void onProgressUpdate(String... progress) {
		GetLocationsAndTagsTaskHandler taskHandler = (GetLocationsAndTagsTaskHandler) mTaskHandler.get();

		if (isCancelled() || taskHandler == null)
			return;
		taskHandler.onGetLocationsAndTagsProgress(taskHandler.getString(R.string.loading), progress[0]);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetLocationsAndTagsTaskHandler taskHandler = (GetLocationsAndTagsTaskHandler) mTaskHandler.get();
		if (isCancelled() || taskHandler == null)
			return;
		taskHandler.onLocationsAndTagsReady();
	}

	public interface GetLocationsAndTagsTaskHandler extends AsyncHttpTaskBaseHandler {
		void onGetLocationsAndTagsProgress(String title, String progress);

		void onLocationsAndTagsReady();
	}
}