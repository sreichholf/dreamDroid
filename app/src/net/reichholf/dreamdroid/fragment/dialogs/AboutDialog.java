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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
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
		String text = String.format("%s\n\n%s\n\n%s", DreamDroid.VERSION_STRING, getString(R.string.license),
				getString(R.string.source_code_link));

		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
		builder.title(R.string.about)
				.content(text)
				.neutralText(R.string.donate)
		.callback(new MaterialDialog.ButtonCallback() {
			@Override
			public void onNeutral(MaterialDialog dialog) {
				ExtendedHashMap skus = ((BaseActivity) getActivity()).getIabItems();
				DonationDialog d = DonationDialog.newInstance(skus);
				((MultiPaneHandler)getActivity()).showDialogFragment(d, "donate_dialog");
			}
		});

		MaterialDialog dialog = builder.build();
		Linkify.addLinks(dialog.getContentView(), Linkify.EMAIL_ADDRESSES|Linkify.WEB_URLS);

		return dialog;
	}
}
