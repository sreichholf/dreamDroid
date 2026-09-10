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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Signal;
import net.reichholf.dreamdroid.enigma.SignalLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.ui.signal.SignalScreenKt;
import net.reichholf.dreamdroid.ui.signal.SignalUiState;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Live tuner signal meter. Compose Material 3 UI; typed {@link Signal}; HalfGauge via AndroidView.
 * Polls via coroutine + {@code EnigmaClient.getSignal()} (generation-guarded).
 */
public class SignalFragment extends BaseHttpFragment {
	private static final String TAG = SignalFragment.class.getSimpleName();

	private static int sMaxSnrDb = 20;
	private static int sMinSnrDb = 5;
	private static int sMaxDelay = 1000;
	private static int sMinDelay = 150;

	private boolean mIsUpdating = false;
	private double mSnrDb = sMinSnrDb;
	private long mStartTime;
	/** Bumped on each new fetch / stop so stale load callbacks are ignored. */
	private int mSignalGeneration = 0;

	@Nullable
	private Job mLoadJob;

	private SignalUiState mUiState;

	@NonNull
	private Handler mHandler = new Handler();
	@NonNull
	private Runnable mPlaySoundTask = new Runnable() {
		public void run() {
			Double freq = (1650 * mSnrDb * mSnrDb) / 1000 + 200;
			playSound(freq);

			Double delay = sMinDelay * (Math.pow(sMaxSnrDb, 3) / Math.pow(mSnrDb, 3));
			delay = delay > sMaxDelay ? sMaxDelay : delay;
			mHandler.postDelayed(this, delay.longValue());
		}
	};

	@NonNull
	private Runnable mUpdateTask = new Runnable() {
		public void run() {
			if (!mIsUpdating)
				reload();
			mHandler.postDelayed(this, 25);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.signal_meter));
		mUiState = new SignalUiState();
	}

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
	public void onDestroyView() {
		cancelLoad();
		super.onDestroyView();
	}

	private void cancelLoad() {
		if (mLoadJob != null) {
			mLoadJob.cancel(null);
			mLoadJob = null;
		}
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.signal, container, false);
		ComposeView compose = view.findViewById(R.id.compose_signal);
		SignalScreenKt.bindSignalScreen(
				compose,
				mUiState,
				enabled -> {
					if (enabled) {
						startPolling();
					} else {
						stopPolling();
					}
					return kotlin.Unit.INSTANCE;
				},
				acoustic -> {
					if (acoustic) {
						if (mUiState.getEnabled()) {
							mHandler.removeCallbacks(mPlaySoundTask);
							mHandler.post(mPlaySoundTask);
						}
					} else {
						mHandler.removeCallbacks(mPlaySoundTask);
					}
					return kotlin.Unit.INSTANCE;
				}
		);
		return view;
	}

	private void applySignal(@NonNull Signal signal) {
		long stopTime = System.currentTimeMillis();
		long time = stopTime - mStartTime;
		Log.w(TAG, "request & parsing took: " + time + "ms");

		mUiState.apply(signal, sMinSnrDb);
		mSnrDb = mUiState.getSnrDb();
	}

	@Override
	protected void reload() {
		mStartTime = System.currentTimeMillis();
		if (!"".equals(getBaseTitle().trim()))
			setCurrentTitle(getBaseTitle() + " - " + getString(R.string.loading));

		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		loadSignal();
	}

	private void loadSignal() {
		if (!isAdded() || getView() == null || mIsUpdating) {
			return;
		}
		mIsUpdating = true;
		final int generation = ++mSignalGeneration;
		cancelLoad();
		mLoadJob = SignalLoadKt.launchSignalLoad(this, (success, signal, errorText) -> {
			if (generation != mSignalGeneration) {
				return Unit.INSTANCE;
			}
			handleSignalReady(success, signal, errorText);
			return Unit.INSTANCE;
		});
	}

	private void handleSignalReady(boolean success, @Nullable Signal signal, @Nullable String errorText) {
		mIsUpdating = false;
		if (!isAdded()) {
			return;
		}
		restoreTitle();
		if (!mUiState.getEnabled()) {
			return;
		}
		if (!success || signal == null) {
			mUiState.setEnabled(false);
			stopPolling();
			if (errorText != null && !errorText.isEmpty()) {
				showToast(errorText);
			}
			return;
		}
		applySignal(signal);
		reload();
	}

	private void restoreTitle() {
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
	}

	private void startPolling() {
		mIsUpdating = false;
		if (mUiState.getEnabled()) {
			reload();
			if (mUiState.getAcousticFeedback()) {
				mHandler.removeCallbacks(mPlaySoundTask);
				mHandler.post(mPlaySoundTask);
			}
		}
	}

	private void stopPolling() {
		mHandler.removeCallbacks(mPlaySoundTask);
		mHandler.removeCallbacks(mUpdateTask);
		mSignalGeneration++;
		cancelLoad();
		mIsUpdating = false;
		mUiState.clearMeter();
		restoreTitle();
	}

	void playSound(double freqOfTone) {
		double duration = 0.075; // seconds
		int sampleRate = 44100; // a number

		double dnumSamples = duration * sampleRate;
		dnumSamples = Math.ceil(dnumSamples);
		int numSamples = (int) dnumSamples;
		double sample[] = new double[numSamples];
		byte generatedSnd[] = new byte[2 * numSamples];

		AudioTrack audioTrack = null; // Get audio track
		try {
			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT, numSamples * 2, AudioTrack.MODE_STATIC);
			Log.w("SignalFragment!!", Integer.toString(sampleRate));
		} catch (Exception e) {
			return;
		}

		for (int i = 0; i < numSamples; ++i) { // Fill the sample array
			sample[i] = Math.sin(freqOfTone * 2 * Math.PI * i / (sampleRate));
		}

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.
		int idx = 0;
		int i = 0;

		int ramp = numSamples / 2; // Amplitude ramp as a percent of sample
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

		try {
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
