package net.reichholf.dreamdroid.activities.abs;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.util.IabException;
import net.reichholf.dreamdroid.util.IabHelper;
import net.reichholf.dreamdroid.util.IabResult;
import net.reichholf.dreamdroid.util.Inventory;
import net.reichholf.dreamdroid.util.Purchase;

import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;

/**
 * Created by Stephan on 06.11.13.
 */
public class BaseActivity extends ActionBarActivity {
	private static String TAG = BaseActivity.class.getSimpleName();

	private MemorizingTrustManager mTrustManager;
	private IabHelper mIabHelper;
	private boolean mIabready;
	private IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			// set location of the keystore
			MemorizingTrustManager.setKeyStoreFile("private", "sslkeys.bks");
			// register MemorizingTrustManager for HTTPS
			SSLContext sc = SSLContext.getInstance("TLS");
			mTrustManager = new MemorizingTrustManager(this);
			sc.init(null, new X509TrustManager[]{mTrustManager},
					new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onCreate(savedInstanceState);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true))
			overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale);
		//Bind billing service
		mIabready = false;
		mIabHelper = new IabHelper(this, DreamDroid.IAB_PUB_KEY);
		mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			public void onIabSetupFinished(IabResult result) {
				if (!result.isSuccess()) {
					// Oh noes, there was a problem.
					Log.d(TAG, "Problem setting up In-app Billing: " + result);
					mIabready = true;
				}
				Log.w(TAG, "In-app Billing is ready!");
				mIabready = true;
				getIabItems();
			}
		});
		mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener(){
			@Override
			public void onIabPurchaseFinished(IabResult result, Purchase info) {
				Log.w(TAG, result.getMessage());
			}
		};
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.w(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
		if (!mIabHelper.handleActivityResult(requestCode, resultCode, data)) {
			super.onActivityResult(requestCode, resultCode, data);
		} else {
			Log.w(TAG, "onActivityResult handled by IABUtil.");
		}
	}


	public ExtendedHashMap getIabItems(){
		ExtendedHashMap result = new ExtendedHashMap();
		if(!mIabready)
			return result;

		ArrayList<String> skuList =  new ArrayList<>(Arrays.asList(DreamDroid.SKU_LIST));
		Inventory inventory = null;
		try {
			inventory = mIabHelper.queryInventory(true, skuList);
		} catch (IabException e) {
			Log.e(TAG, "FAILED TO GET PRICE!");
			e.printStackTrace();
		}

		if( inventory == null )
			return result;

		for(String sku : skuList){
			String price = inventory.getSkuDetails(sku).getPrice();
			result.put(sku, price);
			Log.w(TAG, getString(R.string.donate_sum, price));
		}
		return result;
	}

	public void purchase(String sku){
		mIabHelper.launchPurchaseFlow(this, DreamDroid.SKU_DONATE_1, 10001, mPurchaseFinishedListener);
	}



	@Override
	public void onPause() {
		mTrustManager.unbindDisplayActivity(this);
		super.onPause();
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true))
			overridePendingTransition(R.anim.activity_open_scale, R.anim.activity_close_translate);
	}

	@Override
	public void onResume() {
		mTrustManager.bindDisplayActivity(this);
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mIabHelper != null) mIabHelper.dispose();
		mIabHelper = null;
	}

	public void setSupportProgressBarIndeterminateVisibility(boolean visible) {
	}
}
