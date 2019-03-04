package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.BaseActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;

/**
 * Created by Stephan on 29.01.2015.
 */
public class DonationDialog extends ActionDialog {
	private ExtendedHashMap mItems;
	private CharSequence[] mActions;

	private static String KEY_ITEMS = "items";

	public static DonationDialog newInstance(ExtendedHashMap items) {
		DonationDialog d = new DonationDialog();
		Bundle args = new Bundle();
		args.putSerializable(KEY_ITEMS, items);
		d.setArguments(args);
		return d;
	}

	protected void init() {
		mItems = (ExtendedHashMap) getArguments().getSerializable(KEY_ITEMS);
		int i = 0;
		mActions = new CharSequence[mItems.size()];

		for (String sku : DreamDroid.SKU_LIST) {
			String price = mItems.getString(sku);
			if (price == null)
				continue;
			mActions[i] = getString(R.string.donate_sum, price);
			i++;
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setRetainInstance(true);
		init();
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.donate)
				.setItems(mActions, (dialog, which) -> {
					BaseActivity ba = (BaseActivity) getActivity();
					ba.purchase(DreamDroid.SKU_LIST[which]);
					finishDialog(Statics.ACTION_NONE, null);
				});
		return builder.create();
	}
}
