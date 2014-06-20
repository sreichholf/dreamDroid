package net.reichholf.dreamdroid.helpers;

import android.content.Intent;
import android.os.Handler;

import net.reichholf.dreamdroid.helpers.enigma2.epgsync.EpgDatabase;
import net.reichholf.dreamdroid.service.HttpIntentService;

/**
 * Created by Stephan on 04.06.2014.
 */
public class SyncService extends HttpIntentService {
	public SyncService() {
		super(SyncService.class.getCanonicalName());

	}

	@Override
	public void onCreate() {
		mHandler = new Handler();
		super.onCreate();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		setupSSL();
		EpgDatabase epgDatabase = new EpgDatabase();
	}
}
