package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;

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
	private int[] mActionIds;
	private CharSequence[] mActions;

	private static String KEY_ITEMS;

	public DonationDialog(ExtendedHashMap items) {
		super();
	}

	public static DonationDialog newInstance(ExtendedHashMap items){
		DonationDialog d = new DonationDialog(items);
		Bundle args = new Bundle();
		args.putParcelable(KEY_ITEMS, items);
		d.setArguments(args);
		return d;
	}

	protected void init(){
		mItems = getArguments().getParcelable(KEY_ITEMS);
		int i = 0;
		mActions = new CharSequence[mItems.size()];
		mActionIds = new int[mItems.size()];

		for (String sku : DreamDroid.SKU_LIST) {
			String price = mItems.getString(sku);
			mActions[i] = getString(R.string.donate_sum, price);
			mActionIds[i] = i;
			i++;
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setRetainInstance(true);
		init();
		MaterialDialog.Builder builder;
		builder = new MaterialDialog.Builder(getActivity());
		builder.title(getText(R.string.donate))
				.items(mActions)
				.itemsCallback(new MaterialDialog.ListCallback() {
					@Override
					public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
						BaseActivity ba = (BaseActivity) getActivity();
						ba.purchase(DreamDroid.SKU_LIST[i]);
						//ba.purchase("android.test.purchased");
						finishDialog(Statics.ACTION_DONATE, null);
					}
				});

		return builder.build();
	}
}
