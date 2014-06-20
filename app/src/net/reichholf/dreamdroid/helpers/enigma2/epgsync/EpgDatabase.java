package net.reichholf.dreamdroid.helpers.enigma2.epgsync;

import android.content.Context;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;

/**
 * Created by Stephan on 18.04.2014.
 */
public class EpgDatabase {

	public void syncBouquet(Context context, String reference){
		SimpleHttpClient shc = SimpleHttpClient.getInstance(DreamDroid.getCurrentProfile());
		ServiceListRequestHandler slh = new ServiceListRequestHandler();
		ArrayList<NameValuePair> args = new ArrayList<NameValuePair>();
		args.add(new BasicNameValuePair("bRef", reference));

		String xml = slh.getList(shc, args);
		ArrayList<ExtendedHashMap> services = new ArrayList<ExtendedHashMap>();
		if(xml != null){
			slh.parseList(xml, services);
			for(ExtendedHashMap service : services){
				syncService(context, service.getString(Service.KEY_REFERENCE));
			}
		}
	}

	public void syncService(Context context, String reference){
		SimpleHttpClient shc = SimpleHttpClient.getInstance(DreamDroid.getCurrentProfile());
		EventListRequestHandler elh = new EventListRequestHandler();
		ArrayList<NameValuePair> args = new ArrayList<NameValuePair>();
		args.add(new BasicNameValuePair("sRef", reference));

		String xml = elh.getList(shc, args);
		ArrayList<ExtendedHashMap> events = new ArrayList<ExtendedHashMap>();
		if(xml != null){
			elh.parseList(xml, events);
			for(ExtendedHashMap event : events){
				updateEvent(context, event);
			}
		}
	}

	public void updateEvent(Context context, ExtendedHashMap event){

	}
}
