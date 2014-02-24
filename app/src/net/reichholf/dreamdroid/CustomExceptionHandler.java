/*
 * Roughly based on http://androidblogger.blogspot.com/2010/03/crash-reporter-for-android-slight.html
 */

package net.reichholf.dreamdroid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.GregorianCalendar;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

/**
 * @author sre
 * 
 */
public class CustomExceptionHandler implements UncaughtExceptionHandler {
	private String mVersionName;
	private String mPackageName;
	private String mFilePath;
	private String mPhoneModel;
	private String mAndroidVersion;
	private String mBoard;
	private String mBrand;
	private String mDevice;
	private String mDisplay;
	private String mFingerPrint;
	private String mHost;
	private String mId;
	private String mModel;
	private String mProduct;
	private String mTags;
	private String mType;
	private String mUser;
	private long mTime;

	private Thread.UncaughtExceptionHandler mPreviousHandler;
	private Context mCurContext;
	
	/**
	 * @param context
	 */
	public static void register(Context context){
		new CustomExceptionHandler(context);
	}
	
	/**
	 * @param context
	 */
	private CustomExceptionHandler(Context context) {
		mPreviousHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(this);
		mCurContext = context;
	}

	/**
	 * @return
	 */
	public long getAvailableInternalMemorySize() {
		File path = Environment.getDataDirectory();
		StatFs stat = new StatFs(path.getPath());
		long blockSize = stat.getBlockSize();
		long availableBlocks = stat.getAvailableBlocks();
		return availableBlocks * blockSize;
	}

	/**
	 * @return
	 */
	public long getTotalInternalMemorySize() {
		File path = Environment.getDataDirectory();
		StatFs stat = new StatFs(path.getPath());
		long blockSize = stat.getBlockSize();
		long totalBlocks = stat.getBlockCount();
		return totalBlocks * blockSize;
	}

	/**
	 * @param context
	 */
	void collectMetaData(Context context) {
		try {
			PackageManager pm = context.getPackageManager();
			PackageInfo pi;
			pi = pm.getPackageInfo(context.getPackageName(), 0);
			mVersionName = pi.versionName;
			mPackageName = pi.packageName;

			mPhoneModel = android.os.Build.MODEL;
			mAndroidVersion = android.os.Build.VERSION.RELEASE;
			mBoard = android.os.Build.BOARD;
			mBrand = android.os.Build.BRAND;
			mDevice = android.os.Build.DEVICE;
			mDisplay = android.os.Build.DISPLAY;
			mFingerPrint = android.os.Build.FINGERPRINT;
			mHost = android.os.Build.HOST;
			mId = android.os.Build.ID;
			mModel = android.os.Build.MODEL;
			mProduct = android.os.Build.PRODUCT;
			mTags = android.os.Build.TAGS;
			mTime = android.os.Build.TIME;
			mType = android.os.Build.TYPE;
			mUser = android.os.Build.USER;

		} catch (Exception e) {
			Log.e(DreamDroid.LOG_TAG, e.toString());
		}
	}

	/**
	 * @return
	 */
	public String getMetaDataString() {
		collectMetaData(mCurContext);

		String retVal = "Version : " + mVersionName;
		retVal += "\nPackage : " + mPackageName;
		retVal += "\nFilePath : " + mFilePath;
		retVal += "\nPhone Model : " + mPhoneModel;
		retVal += "\nAndroid Version : " + mAndroidVersion;
		retVal += "\nBoard : " + mBoard;
		retVal += "\nBrand : " + mBrand;
		retVal += "\nDevice : " + mDevice;
		retVal += "\nDisplay : " + mDisplay;
		retVal += "\nFinger Print : " + mFingerPrint;
		retVal += "\nHost : " + mHost;
		retVal += "mId : " + mId;
		retVal += "\nModel : " + mModel;
		retVal += "\nProduct : " + mProduct;
		retVal += "\nTags : " + mTags;
		retVal += "\nTime : " + mTime;
		retVal += "\nType : " + mType;
		retVal += "\nUser : " + mUser;
		retVal += "\nTotal Internal memory : " + getTotalInternalMemorySize();
		retVal += "\nAvailable Internal memory : " + getAvailableInternalMemorySize();

		return retVal;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang
	 * .Thread, java.lang.Throwable)
	 */
	public void uncaughtException(Thread t, Throwable e) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		String stacktrace = result.toString();
		Log.e(DreamDroid.LOG_TAG, stacktrace);

		String report = "";
		report += "Error Report collected on : " + GregorianCalendar.getInstance().getTime().toString();
		report += "\n\nInformations :";
		report += "\n==============\n";
		
		report += getMetaDataString();

		report += "\n\nStack : \n";
		report += "======= \n";
		report += stacktrace;
		report += "\nCause : \n";
		report += "======= \n";

		// If the exception was thrown in a background thread inside
		// AsyncTask, then the actual exception can be found with getCause
		Throwable cause = e.getCause();
		while (cause != null) {
			cause.printStackTrace(printWriter);
			report += result.toString();
			Log.e(DreamDroid.LOG_TAG, result.toString());
			cause = cause.getCause();
		}
		printWriter.close();
		report += "****  End of current Report ***";
		writeToLogFile(report);
		mPreviousHandler.uncaughtException(t, e);
	}

	/**
	 * @param logString
	 */
	private void writeToLogFile(String logString) {
		try {
			long timestamp = GregorianCalendar.getInstance().getTimeInMillis();

			File sdRoot = Environment.getExternalStorageDirectory();
			String fileName = "dreamDroid-" + timestamp + ".trace";
			File file = new File(sdRoot, fileName);

			FileOutputStream out = new FileOutputStream(file);
			out.write(logString.getBytes());
			out.close();
		} catch (IOException e) {
			Log.e(DreamDroid.LOG_TAG, e.toString());
		}
	}
}
