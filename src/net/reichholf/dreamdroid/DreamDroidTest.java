/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */


package net.reichholf.dreamdroid;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.R;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity;
import net.reichholf.dreamdroid.activities.MovieListActivity;
import net.reichholf.dreamdroid.activities.TimerListActivity;
import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.*;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
//import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2DeviceInfoHandler;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2EventHandler;
//import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2ServiceListHandler;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2MovieListHandler;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2TimerHandler;
import net.reichholf.dreamdroid.helpers.enigma2.*;

public class DreamDroidTest extends AbstractHttpListActivity {
	
	public static String[] MAIN_ITEMS = new String[] { "1", "2", "3" };	
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	
		
		mList = new ArrayList<ExtendedHashMap>();
		ExtendedHashMap map = new ExtendedHashMap();
		
		map.put("item", "Timer");
		map.put("desc", "Show, Add and edit Timers");
		mList.add(map.clone());

		map.clear();
		map.put("item", "Movies");
		map.put("desc", "A list of recorded Movies");
		mList.add(map.clone());
		
		mAdapter = new SimpleAdapter(this, mList, android.R.layout.two_line_list_item, new String[]{"item", "desc"}, new int[]{R.id.text1, R.id.text2});
		setListAdapter(mAdapter);
		
//		httpTest();

		Intent intent = new Intent(this, MovieListActivity.class);		
		this.startActivity(intent);
		intent = new Intent(this, TimerListActivity.class);
		this.startActivity(intent);
		
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id){
//		ExtendedHashMap map = list.get((int) id);
//		System.out.print("break");
	}
	
	public void httpTest() {
		SimpleHttpClient shc = new SimpleHttpClient("192.168.178.22", "80", false);
		List<NameValuePair> parameters = new ArrayList<NameValuePair>();
		
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());
		
		parameters.clear();
		
		if( shc.fetchPageContent(URIStore.TIMER_LIST, parameters)){
			long starttime = System.currentTimeMillis();
			
			
			
			ArrayList<ExtendedHashMap> timerlist = new ArrayList<ExtendedHashMap>();
			
			E2TimerHandler th = new E2TimerHandler(timerlist);
			sdp.getParser().setHandler(th);
			
			String input = shc.getPageContentString();
			
			sdp.parse(input);
			
			long runtime = (System.currentTimeMillis() - starttime);
			
			System.out.println(">>>>>>>>> Servicelist Took " + runtime
					+ " millis");
		}
		
		parameters.clear();
		
		if( shc.fetchPageContent(URIStore.MOVIES, parameters) ){
			long starttime = System.currentTimeMillis();
			
			ArrayList<ExtendedHashMap> movielist = new ArrayList<ExtendedHashMap>();
			E2MovieListHandler mlh = new E2MovieListHandler(movielist);
			sdp.getParser().setHandler(mlh);

			String input = shc.getPageContentString();
			
			sdp.parse(input);
			
			mList.clear();
			mList.addAll( movielist );
			mAdapter.notifyDataSetChanged();
			
			long runtime = (System.currentTimeMillis() - starttime);
			System.out.println(movielist);
			System.out.println(">>>>>>>>> Movielist Took " + runtime
					+ " millis");			
		}

//// All TV Services		
//		parameters.clear();
//		parameters.add(new BasicNameValuePair("sRef", RefStore.TV_ALL));
//
//		if (shc.fetchPageContent(URIStore.SERVICES, parameters)) {
//			// init some stuff
//			long starttime = System.currentTimeMillis();
//
//			ArrayList<ExtendedHashMap> servicelist = new ArrayList<ExtendedHashMap>();
//			E2ServiceListHandler slh = new E2ServiceListHandler(servicelist);
//			sdp.getParser().setHandler( slh  );
//			
//			String services = shc.getPageContentString();
//
//			// Parse
//			sdp.parse(services);
//
//			long runtime = (System.currentTimeMillis() - starttime);
//
//			System.out.println(">>>>>>>>> Servicelist Took " + runtime
//					+ " millis");
//
//		}
//
//// Service EPG for ARD		
		parameters.clear();
		parameters.add(new BasicNameValuePair("sRef",
				"1:0:1:6DCA:44D:1:FFFF0162:0:0:0:"));

		if (shc.fetchPageContent(URIStore.EPG_SERVICE, parameters)) {
			ArrayList<ExtendedHashMap> epglist = new ArrayList<ExtendedHashMap>();
			sdp.getParser().setHandler(new E2EventHandler(epglist));

			String epg = shc.getPageContentString();

			long starttime = System.currentTimeMillis();
			sdp.parse(epg);
			long runtime = (System.currentTimeMillis() - starttime);

			System.out.println(">>>>>>>>> EPG Took " + runtime + " millis");
			System.out.println("<<<<<<<< STOP");

		}
	}

	// if(shc.fetchPageContent("http://dm7025/web/deviceinfo")){
	// String deviceinfo = shc.getPageContentString();
	//	    		
	// HashMap<String,Object> deviceInfo = new HashMap<String,Object>();
	//	
	// E2DeviceInfoHandler e2dih = new E2DeviceInfoHandler(deviceInfo);
	//	
	// GenericSaxParser parser = new GenericSaxParser(e2dih);
	// SaxDataProvider dp = new SaxDataProvider(parser);
	// dp.parse(deviceinfo);
	//	
	// System.out.println("<<<<<<<< STOP");
	//	
	// }
//	}
}