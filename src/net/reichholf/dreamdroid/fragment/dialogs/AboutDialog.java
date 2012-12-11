/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * @author sre
 * 
 */
public class AboutDialog extends AbstractDialog {
	public static AboutDialog newInstance() {
		return new AboutDialog();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Dialog dialog = new Dialog(getActivity());
		dialog.setContentView(R.layout.about);
		dialog.setTitle(R.string.about);

		TextView aboutText = (TextView) dialog.findViewById(R.id.TextViewAbout);
		CharSequence text = DreamDroid.VERSION_STRING + "\n\n" + getText(R.string.license) + "\n\n"
				+ getText(R.string.source_code_link);
		aboutText.setText(text);

		Button buttonDonate = (Button) dialog.findViewById(R.id.ButtonDonate);
		buttonDonate.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				Uri uriUrl = Uri
						.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=stephan%40reichholf%2enet&item_name=dreamDroid&lc=EN&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donate_SM%2egif%3aNonHosted");
				Intent i = new Intent(Intent.ACTION_VIEW, uriUrl);
				startActivity(i);
			}
		});
		return dialog;
	}
}
