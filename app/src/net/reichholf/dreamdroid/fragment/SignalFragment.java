/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Signal;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SignalRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.codeandmagic.android.gauge.GaugeView;

public class SignalFragment extends BaseHttpFragment {
	private static final String TAG = SignalFragment.class.getSimpleName();

	private static int sMaxSnrDb = 17;
	private static int sMinSnrDb = 7;

	GaugeView mSnr;
	CheckBox mSound;
	ToggleButton mEnabled;
	TextView mSnrdb;
	TextView mBer;
	TextView mAgc;

	private boolean mIsUpdating = false;
	private double mSnrDb = sMinSnrDb;
	private long mStartTime;

	private Handler mHandler = new Handler();
	private Runnable mPlaySoundTask = new Runnable() {
		public void run() {
			Double freq = (1650 * mSnrDb * mSnrDb) / 1000 + 200;
			playSound(freq);

			Double delay = 100 * (Math.pow(sMaxSnrDb, 3) / Math.pow(mSnrDb, 3));
			delay = delay > 2000 ? 2000 : delay;
			mHandler.postDelayed(this, delay.longValue());
		}
	};

	private Runnable mUpdateTask = new Runnable() {
		public void run() {
			if (!mIsUpdating)
				reload();
			mHandler.postDelayed(this, 50);
		}
	};

	@Override
	public void onResume() {
		super.onResume();
		startPolling();
	}

	@Override
	public void onPause() {
		super.onPause();
		stopPolling();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.signal, container, false);

		mSnr = (GaugeView) view.findViewById(R.id.gauge_view1);

		mEnabled = (ToggleButton) view.findViewById(R.id.toggle_enabled);
		mEnabled.setChecked(true);
		mEnabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					startPolling();
				} else {
					stopPolling();
				}
			}
		});

		mSound = (CheckBox) view.findViewById(R.id.check_accoustic_feedback);
		mSound.setChecked(false);
		mSound.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					if (mEnabled.isChecked()) {
						mHandler.removeCallbacks(mPlaySoundTask);
						mHandler.post(mPlaySoundTask);
					}
				} else {
					mHandler.removeCallbacks(mPlaySoundTask);
				}
			}
		});

		mSnrdb = (TextView) view.findViewById(R.id.text_snrdb);
		mBer = (TextView) view.findViewById(R.id.text_ber);
		mAgc = (TextView) view.findViewById(R.id.text_agc);
		return view;
	}

	@Override
	public Loader<LoaderResult<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		AsyncSimpleLoader loader = new AsyncSimpleLoader(getAppCompatActivity(), new SignalRequestHandler(), args);
		mIsUpdating = true;
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ExtendedHashMap>> loader, LoaderResult<ExtendedHashMap> result) {
		if (result.isError()) {
			mEnabled.setChecked(false);
		}
		super.onLoadFinished(loader, result);
	}

	@Override
	public void applyData(int loaderId, ExtendedHashMap content) {
		long stopTime = System.currentTimeMillis();
		long time = stopTime - mStartTime;
		Log.w(TAG, "requets & parsing took: " + time + "ms");

		if (!mEnabled.isChecked())
			return;
		String _snr = content.getString(Signal.KEY_SNR).replace("%", "").trim();

		int snr = 0;
		try {
			snr = Integer.parseInt(_snr);
		} catch (NumberFormatException ex) {
		}

		try {
			mSnrDb = Double.parseDouble(content.getString(Signal.KEY_SNRDB, "7").replaceAll("(?i)dB", "").trim());
		} catch (NumberFormatException ex) {
			mSnrDb = sMinSnrDb;
		}

		mSnr.setTargetValue(snr);
		mSnrdb.setText(content.getString(Signal.KEY_SNRDB, "-").trim());
		mBer.setText(content.getString(Signal.KEY_BER, "-").trim());
		mAgc.setText(content.getString(Signal.KEY_AGC, "-").trim());

		mIsUpdating = false;
		reload();
	}

	protected void reload() {
		mStartTime = System.currentTimeMillis();
		if (!"".equals(getBaseTitle().trim()))
			setCurrentTitle(getBaseTitle() + " - " + getString(R.string.loading));

		getAppCompatActivity().setTitle(getCurrentTitle());
		getLoaderManager().restartLoader(0, getLoaderBundle(HttpFragmentHelper.LOADER_DEFAULT_ID), this);
	}

	private void startPolling() {
		mIsUpdating = false;
		if (mEnabled.isChecked()) {
			reload();
			// mHandler.removeCallbacks(mUpdateTask);
			// mHandler.post(mUpdateTask);
			if (mSound.isChecked()) {
				mHandler.removeCallbacks(mPlaySoundTask);
				mHandler.post(mPlaySoundTask);
			}
		}
	}

	private void stopPolling() {
		mHandler.removeCallbacks(mPlaySoundTask);
		mHandler.removeCallbacks(mUpdateTask);
		mSnr.setTargetValue(0);
		mSnrdb.setText("-");
		mBer.setText("-");
		mAgc.setText("-");
	}

	void playSound(double freqOfTone) {
		double duration = 0.1; // seconds
		int sampleRate = 8000; // a number

		double dnumSamples = duration * sampleRate;
		dnumSamples = Math.ceil(dnumSamples);
		int numSamples = (int) dnumSamples;
		double sample[] = new double[numSamples];
		byte generatedSnd[] = new byte[2 * numSamples];

		for (int i = 0; i < numSamples; ++i) { // Fill the sample array
			sample[i] = Math.sin(freqOfTone * 2 * Math.PI * i / (sampleRate));
		}

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.
		int idx = 0;
		int i = 0;

		int ramp = numSamples / 20; // Amplitude ramp as a percent of sample
		// count

		for (i = 0; i < numSamples; ++i) { // Ramp amplitude up (to avoid
			// clicks)
			if (i < ramp) {
				double dVal = sample[i];
				// Ramp up to maximum
				final short val = (short) ((dVal * 32767 * i / ramp));
				// in 16 bit wav PCM, first byte is the low order byte
				generatedSnd[idx++] = (byte) (val & 0x00ff);
				generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
			} else if (i < numSamples - ramp) {
				// Max amplitude for most of the samples
				double dVal = sample[i];
				// scale to maximum amplitude
				final short val = (short) ((dVal * 32767));
				// in 16 bit wav PCM, first byte is the low order byte
				generatedSnd[idx++] = (byte) (val & 0x00ff);
				generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
			} else {
				double dVal = sample[i];
				// Ramp down to zero
				final short val = (short) ((dVal * 32767 * (numSamples - i) / ramp));
				// in 16 bit wav PCM, first byte is the low order byte
				generatedSnd[idx++] = (byte) (val & 0x00ff);
				generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
			}
		}

		AudioTrack audioTrack = null; // Get audio track
		try {
			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT, numSamples * 2, AudioTrack.MODE_STATIC);
			// Load the track
			audioTrack.write(generatedSnd, 0, generatedSnd.length);
			audioTrack.play(); // Play the track
		} catch (Exception e) {
		}

		int x = 0;
		do { // Montior playback to find when done
			if (audioTrack != null)
				x = audioTrack.getPlaybackHeadPosition();
			else
				x = numSamples;
		} while (x < numSamples);

		if (audioTrack != null)
			audioTrack.release(); // Track play done. Release track.
	}
}
