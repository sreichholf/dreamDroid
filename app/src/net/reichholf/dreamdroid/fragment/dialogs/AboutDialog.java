/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.BaseActivity;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

/**
 * @author sre
 */
public class AboutDialog extends AbstractDialog {
	public static AboutDialog newInstance() {
		return new AboutDialog();
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
		builder.customView(R.layout.about, true)
				.title(R.string.about);
		MaterialDialog dialog = builder.build();
		View v = dialog.getCustomView();

		TextView aboutText = (TextView) v.findViewById(R.id.TextViewAbout);
		String text = String.format("%s\n\n%s\n\n%s", DreamDroid.VERSION_STRING, getString(R.string.license),
				getString(R.string.source_code_link));
		aboutText.setText(text);

		Button buttonDonate = (Button) v.findViewById(R.id.ButtonDonate);
		buttonDonate.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
//				Uri uriUrl = Uri
//						.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=stephan%40reichholf%2enet&item_name=dreamDroid&lc=EN&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donate_SM%2egif%3aNonHosted");
//				Intent i = new Intent(Intent.ACTION_VIEW, uriUrl);
//				startActivity(i);


				ExtendedHashMap skus = ((BaseActivity) getActivity()).getIabItems();
				DonationDialog d = DonationDialog.newInstance(skus);
				((MultiPaneHandler)getActivity()).showDialogFragment(d, "donate_dialog");
			}


		});
		return dialog;
	}
}
