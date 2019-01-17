package net.reichholf.dreamdroid.helpers;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

public class AndroidUtils {
	public static boolean isLeanback(Context ctx) {
		return (ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION)
				|| ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK));
	}

	public static void setTextOrHide(TextView textView, String text) {
		if (text.isEmpty())
			textView.setVisibility(View.GONE);
		else
			textView.setText(text);
	}
}
