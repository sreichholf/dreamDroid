package net.reichholf.dreamdroid.helpers;

import android.content.Intent;
import androidx.annotation.NonNull;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.epgsync.EpgDatabase;
import net.reichholf.dreamdroid.service.HttpIntentService;

/**
 * Created by Stephan on 04.06.2014.
 */
public class SyncService extends HttpIntentService {
    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        setupSSL();
        EpgDatabase epgDatabase = new EpgDatabase();
        DreamDroid.loadCurrentProfile(getApplicationContext());
        String bouquet = intent.getStringExtra(Event.KEY_SERVICE_REFERENCE);
        if(bouquet != null)
            epgDatabase.syncBouquet(getApplicationContext(), bouquet);
    }
}
