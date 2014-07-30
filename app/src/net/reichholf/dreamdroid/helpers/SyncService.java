package net.reichholf.dreamdroid.helpers;

import android.content.Intent;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
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
    protected void onHandleIntent(Intent intent) {
        setupSSL();
        EpgDatabase epgDatabase = new EpgDatabase();
        DreamDroid.loadCurrentProfile(getApplicationContext());
        String bouquet = intent.getStringExtra(Event.KEY_SERVICE_REFERENCE);
        if(bouquet != null)
            epgDatabase.syncBouquet(getApplicationContext(), bouquet);
    }
}
