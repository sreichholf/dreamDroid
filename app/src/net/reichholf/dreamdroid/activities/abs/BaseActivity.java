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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.livefront.bridge.Bridge;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.helpers.PiconSyncService;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
	public static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_BACKUP = 3;
	private static String TAG = BaseActivity.class.getSimpleName();

	private MemorizingTrustManager mTrustManager;

	static {
		MemorizingTrustManager.setKeyStoreFile("private", "sslkeys.bks");
	}

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
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Bridge.saveInstanceState(this, outState);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
		super.onActivityResult(requestCode, resultCode, data);
		List<Fragment> fragments = getSupportFragmentManager().getFragments();
		if(fragments == null)
			return;
		for (Fragment fragment : fragments) {
			if (fragment == null)
				continue;

			fragment.onActivityResult(requestCode, resultCode, data);
		}
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
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		Bridge.clear(this);
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		switch (requestCode) {
			case REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON:
				if (granted)
					callPiconSyncIntent();
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
	}

	public Context getContext() {
		return this;
	}
}
