package net.reichholf.dreamdroid.activities.abs;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.livefront.bridge.Bridge;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import net.reichholf.dreamdroid.BuildConfig;
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

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import de.duenndns.ssl.JULHandler;
import de.duenndns.ssl.MemorizingTrustManager;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.internal.tls.OkHostnameVerifier;

/**
 * Created by Stephan on 06.11.13.
 */
public class BaseActivity extends AppCompatActivity implements ActionDialog.DialogActionListener, SharedPreferences.OnSharedPreferenceChangeListener {
	public static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON = 0;
	public static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_SCREENSHOT = 1;
	public static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 2;
	public static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_BACKUP = 3;
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

	IabHelper.OnConsumeMultiFinishedListener mConsumeMultiFinishedListener = (purchases, results) -> Log.w(TAG, "Consuming finished!");

	IabHelper.QueryInventoryFinishedListener mQueryInventoryFinishedListener = new IabHelper.QueryInventoryFinishedListener() {
		@Override
		public void onQueryInventoryFinished(IabResult result, Inventory inv) {
			if (result.isSuccess()) {
				mInventory = inv;
				consumeAll(inv);
			}
		}
	};


    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    private static X509TrustManager systemDefaultTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
            return (X509TrustManager) trustManagers[0];
        } catch (GeneralSecurityException e) {
            throw new AssertionError(); // The system has no TLS. Just give up.
        }
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			// set location of the keystore
			JULHandler.initialize();
			JULHandler.setDebugLogSettings(() -> false);
			// register MemorizingTrustManager for HTTPS

			mTrustManager = new MemorizingTrustManager(this);

			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new X509TrustManager[]{mTrustManager},
					new java.security.SecureRandom());
			//HttpsURLConnection
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(
					mTrustManager.wrapHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()));
			HttpsURLConnection.setFollowRedirects(false);
			//Picasso w/ OkHttpClient
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
            clientBuilder
                    .authenticator((route, response) -> {
                        if (responseCount(response) >= 3) {
                            return null;
                        }
                        String username = response.request().url().username();
                        String password = response.request().url().password();
                        String cred = Credentials.basic(username, password);
                        return response.request().newBuilder().header("Authorization", cred).build();
                    })
                    .sslSocketFactory(sc.getSocketFactory(), systemDefaultTrustManager())
                    .hostnameVerifier(mTrustManager.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE));
            Picasso.Builder builder = new Picasso.Builder(getApplicationContext());
            builder.downloader(new OkHttp3Downloader(clientBuilder.build()));
			try {
				Picasso.setSingletonInstance(builder.build());
			} catch (IllegalStateException ignored) {}
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onCreate(savedInstanceState);
		Bridge.restoreInstanceState(this, savedInstanceState);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true)) {
			overridePendingTransition(R.animator.activity_open_translate, R.animator.activity_close_scale);
		}

		initIAB();
		initPermissions(false);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Bridge.saveInstanceState(this, outState);
	}



	private void initIAB() {
		if (!BuildConfig.FLAVOR.equals("google"))
			return;
		mInventory = null;
		mIabReady = false;
		mIabHelper = new IabHelper(this, DreamDroid.IAB_PUB_KEY);
		mIabHelper.enableDebugLogging(true);
		mIabHelper.startSetup(result -> {
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
		});
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
			overridePendingTransition(R.animator.activity_open_scale, R.animator.activity_close_translate);
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
		Bridge.clear(this);
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if (action == Statics.ACTION_LOCATION_RATIONALE_DONE)
			initPermissions(true);
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
