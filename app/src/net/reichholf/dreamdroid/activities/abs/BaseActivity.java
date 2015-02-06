package net.reichholf.dreamdroid.activities.abs;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.util.IabException;
import net.reichholf.dreamdroid.util.IabHelper;
import net.reichholf.dreamdroid.util.IabResult;
import net.reichholf.dreamdroid.util.Inventory;
import net.reichholf.dreamdroid.util.Purchase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
	private Inventory mInventory;
	private boolean mIabReady;

	IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		@Override
		public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
			String text = null;
			int response = result.getResponse();
			if (response == IabHelper.BILLING_RESPONSE_RESULT_OK) {
				Log.i(TAG, String.format("Purchase finished! %s", result.getMessage()));
				mIabHelper.queryInventoryAsync(true, mQueryInventoryFinishedListener);
				text = getString(R.string.donation_thanks);
			} else if (response != IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED) {
				Log.i(TAG, String.format("Purchase FAILED! %s", result.getMessage()));
				text = getString(R.string.donation_error, response);
			}
			Toast t = Toast.makeText(BaseActivity.this, text, Toast.LENGTH_LONG);
			t.show();
		}
	};

	IabHelper.OnConsumeMultiFinishedListener mConsumeMultiFinishedListener = new IabHelper.OnConsumeMultiFinishedListener() {
		@Override
		public void onConsumeMultiFinished(List<Purchase> purchases, List<IabResult> results) {
			Log.w(TAG, "Consuming finished!");
		}
	};

	IabHelper.QueryInventoryFinishedListener mQueryInventoryFinishedListener = new IabHelper.QueryInventoryFinishedListener() {
		@Override
		public void onQueryInventoryFinished(IabResult result, Inventory inv) {
			if (result.isSuccess()) {
				mInventory = inv;
				consumeAll(inv);
			}
		}
	};


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
				DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true)) {
			overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale);
		}

		initIAB();
	}

	private void initIAB() {
		mInventory = null;
		mIabReady = false;
		mIabHelper = new IabHelper(this, DreamDroid.IAB_PUB_KEY);
		mIabHelper.enableDebugLogging(true);
		mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			public void onIabSetupFinished(IabResult result) {
				if (!result.isSuccess()) {
					// Oh noes, there was a problem.
					Log.d(TAG, "Problem setting up In-app Billing: " + result);
					mIabReady = true;
				}
				Log.w(TAG, "In-app Billing is ready!");
				mIabReady = true;
				ArrayList<String> skuList = new ArrayList<>(Arrays.asList(DreamDroid.SKU_LIST));
				mIabHelper.queryInventoryAsync(true, skuList, mQueryInventoryFinishedListener);
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
		if (!mIabHelper.handleActivityResult(requestCode, resultCode, data)) {
			super.onActivityResult(requestCode, resultCode, data);
		} else {
			Log.i(TAG, "onActivityResult handled by IABUtil.");
		}
	}

	public ExtendedHashMap getIabItems() {
		ExtendedHashMap result = new ExtendedHashMap();
		if (!mIabReady)
			return result;

		ArrayList<String> skuList = new ArrayList<>(Arrays.asList(DreamDroid.SKU_LIST));
		if(mInventory == null) {
			try {
				mInventory = mIabHelper.queryInventory(true, skuList);
			} catch (IabException e) {
				Log.e(TAG, "FAILED TO GET INVENTORY!");
				e.printStackTrace();
			}
		}
		if (mInventory == null)
			return result;

		for (String sku : skuList) {
			String price = mInventory.getSkuDetails(sku).getPrice();
			result.put(sku, price);
			Log.d(TAG, getString(R.string.donate_sum, price));
		}
		return result;
	}

	public void purchase(String sku) {
		mIabHelper.launchPurchaseFlow(this, sku, Statics.REQUEST_DONATE, mPurchaseFinishedListener);
	}

	public void consumeAll(Inventory inventory) {
		ArrayList<Purchase> purchases = new ArrayList<>();
		for (String sku : DreamDroid.SKU_LIST) {
			if (inventory.hasPurchase(sku)) {
				purchases.add(inventory.getPurchase(sku));
				Log.i(TAG, String.format("Consuming %s", sku));
			}
		}
//		if (inventory.hasPurchase("android.test.purchased")) {
//			purchases.add(inventory.getPurchase("android.test.purchased"));
//			Log.i(TAG, "Consuming android.test.purchased");
//		}
		mIabHelper.consumeAsync(purchases, mConsumeMultiFinishedListener);
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
