package net.reichholf.dreamdroid.activities.abs;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.UrlConnectionDownloader;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.PiconSyncService;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.util.IabException;
import net.reichholf.dreamdroid.util.IabHelper;
import net.reichholf.dreamdroid.util.IabResult;
import net.reichholf.dreamdroid.util.Inventory;
import net.reichholf.dreamdroid.util.Purchase;
import net.reichholf.dreamdroid.util.SkuDetails;

import org.piwik.sdk.DownloadTracker;
import org.piwik.sdk.PiwikApplication;
import org.piwik.sdk.TrackHelper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.JULHandler;
import de.duenndns.ssl.MemorizingTrustManager;

/**
 * Created by Stephan on 06.11.13.
 */
public class BaseActivity extends AppCompatActivity implements ActionDialog.DialogActionListener, SharedPreferences.OnSharedPreferenceChangeListener {
	public static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON = 0;
	public static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_SCREENSHOT = 1;
	public static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 2;
	private static String TAG = BaseActivity.class.getSimpleName();

	private MemorizingTrustManager mTrustManager;
	private IabHelper mIabHelper;
	private Inventory mInventory;
	private boolean mIabReady;

	static {
		MemorizingTrustManager.setKeyStoreFile("private", "sslkeys.bks");
	}

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
			JULHandler.initialize();
			JULHandler.setDebugLogSettings(new JULHandler.DebugLogSettings() {
				@Override
				public boolean isDebugLogEnabled() {
					return false;
				}
			});
			// register MemorizingTrustManager for HTTPS

			mTrustManager = new MemorizingTrustManager(this);

			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new X509TrustManager[]{mTrustManager},
					new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(
					mTrustManager.wrapHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()));
			HttpsURLConnection.setFollowRedirects(false);
			Picasso.Builder builder = new Picasso.Builder(getApplicationContext());
			builder.downloader(new UrlConnectionDownloader(getApplicationContext()){
				@Override
				protected HttpURLConnection openConnection(Uri path) throws IOException {
					HttpURLConnection connection = super.openConnection(path);
					String userinfo = path.getUserInfo();
					if(!userinfo.isEmpty()) {
						connection.setRequestProperty("Authorization", "Basic " +
								Base64.encodeToString(userinfo.getBytes(), Base64.NO_WRAP));
					}
					return connection;
				}
			});
			try {
				Picasso.setSingletonInstance(builder.build());
			} catch (IllegalStateException ignored) {}
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onCreate(savedInstanceState);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true)) {
			overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale);
		}

		initIAB();
		initPiwik();
		initPermissions(false);
	}

	private void initIAB() {
		if (getApplicationContext().getPackageName().endsWith("amazon"))
			return;
		mInventory = null;
		mIabReady = false;
		mIabHelper = new IabHelper(this, DreamDroid.IAB_PUB_KEY);
		mIabHelper.enableDebugLogging(true);
		mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			public void onIabSetupFinished(IabResult result) {
				if (!result.isSuccess()) {
					// Oh noes, there was a problem.
					Log.d(TAG, "Problem setting up In-app Billing: " + result);
					String text = result.getMessage();
					Toast toast = Toast.makeText(BaseActivity.this, text, Toast.LENGTH_LONG);
					toast.show();
					mIabReady = false;
					return;
				}
				Log.w(TAG, "In-app Billing is ready!");
				mIabReady = true;
				ArrayList<String> skuList = new ArrayList<>(Arrays.asList(DreamDroid.SKU_LIST));
				mIabHelper.queryInventoryAsync(true, skuList, mQueryInventoryFinishedListener);
			}
		});
	}

	public void showPrivacyStatement() {
		PositiveNegativeDialog dialog = PositiveNegativeDialog.newInstance(getString(R.string.privacy_statement_title), R.string.privacy_statement, android.R.string.yes, Statics.ACTION_STATISTICS_AGREED, android.R.string.no, Statics.ACTION_STATISTICS_DENIED);
		dialog.show(getSupportFragmentManager(), "privacy_statement_dialog");
	}

	private void initPiwik() {
		if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(DreamDroid.PREFS_KEY_PRIVACY_STATEMENT_SHOWN, false)) {
			showPrivacyStatement();
			return;
		}

		if (!DreamDroid.isTrackingEnabled(this))
			return;
		// do not send http requests
		PiwikApplication papp = (PiwikApplication) getApplication();
		papp.getPiwik().setDryRun(false);
		TrackHelper.track().download().identifier(DownloadTracker.Extra.APK_CHECKSUM).with(papp.getTracker());
	}

	private void initPermissions(boolean rationaleShown) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		sp.registerOnSharedPreferenceChangeListener(this);
		int currentTheme = Integer.parseInt(sp.getString("theme_type", "0"));
		if (currentTheme != 0)
			return;
		 if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION) && !rationaleShown) {
				PositiveNegativeDialog rationale = PositiveNegativeDialog.newInstance(getString(R.string.location_rationale_title), R.string.location_rationale, R.string.ok, Statics.ACTION_LOCATION_RATIONALE_DONE);
				FragmentManager fm = getSupportFragmentManager();
				rationale.show(fm, "location_rationale");
				return;
			}

			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
					REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
		if (mIabHelper == null) {
			Log.i(TAG, "IABUtil not yet initialized.");
		} else if (!mIabHelper.handleActivityResult(requestCode, resultCode, data)) {
			super.onActivityResult(requestCode, resultCode, data);
		} else {
			Log.i(TAG, "onActivityResult handled by IABUtil.");
		}

		List<Fragment> fragments = getSupportFragmentManager().getFragments();
		if(fragments == null)
			return;
		for (Fragment fragment : fragments) {
			if (fragment == null)
				continue;

			fragment.onActivityResult(requestCode, resultCode, data);
		}
	}

	public ExtendedHashMap getIabItems() {
		ExtendedHashMap result = new ExtendedHashMap();
		if (!mIabReady) {
			initIAB();
			return result;
		}

		ArrayList<String> skuList = new ArrayList<>(Arrays.asList(DreamDroid.SKU_LIST));
		if (mInventory == null) {
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
			SkuDetails details = mInventory.getSkuDetails(sku);
			if (details == null) {
				Log.w(TAG, String.format("Missing SKU Details for %s", sku));
				continue;
			}

			String price = details.getPrice();
			result.put(sku, price);
			Log.d(TAG, getString(R.string.donate_sum, price));
		}
		return result;
	}

	public void purchase(String sku) {
		mIabHelper.launchPurchaseFlow(this, sku, Statics.REQUEST_DONATE, mPurchaseFinishedListener);
	}

	public void consumeAll(Inventory inventory) {
		if (inventory == null || mIabHelper == null)
			return;
		ArrayList<Purchase> purchases = new ArrayList<>();
		for (String sku : DreamDroid.SKU_LIST) {
			if (inventory.hasPurchase(sku)) {
				purchases.add(inventory.getPurchase(sku));
				Log.i(TAG, String.format("Consuming %s", sku));
			}
		}
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
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if (action == Statics.ACTION_STATISTICS_AGREED || action == Statics.ACTION_STATISTICS_DENIED) {
			boolean enabled = action == Statics.ACTION_STATISTICS_AGREED;
			SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
			prefs.putBoolean(DreamDroid.PREFS_KEY_ALLOW_TRACKING, enabled);
			prefs.putBoolean(DreamDroid.PREFS_KEY_PRIVACY_STATEMENT_SHOWN, true);
			prefs.commit();
			initPiwik();
		} else if (action == Statics.ACTION_LOCATION_RATIONALE_DONE) {
			initPermissions(true);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		switch (requestCode) {
			case REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON:
				if (granted)
					callPiconSyncIntent();
				break;
			case REQUEST_PERMISSION_ACCESS_COARSE_LOCATION:
				if (granted)
					recreate();
				break;
			default:
				Fragment details = getSupportFragmentManager().findFragmentById(R.id.detail_view);
				if (details != null) {
					details.onRequestPermissionsResult(requestCode, permissions, grantResults);
				}
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	public void startPiconSync() {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
			callPiconSyncIntent();
		else
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON);
	}

	protected void callPiconSyncIntent() {
		if (isSyncServiceRunning()) {
			Toast.makeText(this, R.string.picon_sync_running, Toast.LENGTH_LONG).show();
			return;
		}
		Intent piconSyncIntent = new Intent(this, PiconSyncService.class);
		startService(piconSyncIntent);
		Toast.makeText(this, R.string.picon_sync_started, Toast.LENGTH_LONG).show();
	}

	private boolean isSyncServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (PiconSyncService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (DreamDroid.PREFS_KEY_THEME_TYPE.equals(key))
			initPermissions(false);
	}

	public Context getContext() {
		return this;
	}
}
