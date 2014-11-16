/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.helpers.BundleHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.ContextThemeWrapper;

/**
 * @author sre
 * 
 */
public class MultiChoiceDialog extends DialogFragment {
	private static final String KEY_TITLE_ID = "titleId";
	private static final String KEY_ITEMS = "items";
	private static final String KEY_CHECKED_ITEMS = "checkedItems";
	private static final String KEY_POSITIVE_STRING_ID = "positiveStringId";
	private static final String KEY_NEGATIVE_STRING_ID = "negativeStringId";

	private int mTitleId;
	private CharSequence[] mItems;
	private boolean[] mCheckedItems;
	private OnMultiChoiceClickListener mMultiChoiceClickListener;
	private Dialog.OnClickListener mPositiveListener;
	private int mPositiveStringId;
	private Dialog.OnClickListener mNegativeListener;
	private int mNegativeStringId;

	public interface MultiChoiceDialogListener {
		public void onMultiChoiceDialogChange(String dialogTag, DialogInterface dialog, int which, boolean isChecked);

		public void onMultiChoiceDialogFinish(String dialogTag, int result);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onDestroyView() {
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		outState.putBooleanArray("checkedItems", mCheckedItems);
		super.onSaveInstanceState(outState);
	}

	public static MultiChoiceDialog newInstance(int titleId, CharSequence[] items, boolean[] checkedItems) {
		return MultiChoiceDialog.newInstance(titleId, items, checkedItems, -1, -1);
	}

	public static MultiChoiceDialog newInstance(int titleId, CharSequence[] items, boolean[] checkedItems,
			int positiveStringId, int negativeStringId) {

		MultiChoiceDialog fragment = new MultiChoiceDialog();
		Bundle args = new Bundle();
		args.putInt(KEY_TITLE_ID, titleId);
		args.putStringArrayList(KEY_ITEMS, BundleHelper.toStringArrayList(items));
		args.putBooleanArray(KEY_CHECKED_ITEMS, checkedItems);
		args.putInt(KEY_POSITIVE_STRING_ID, positiveStringId);
		args.putInt(KEY_NEGATIVE_STRING_ID, negativeStringId);
		fragment.setArguments(args);
		return fragment;
	}

	public void init() {
		Bundle args = getArguments();
		mTitleId = args.getInt(KEY_TITLE_ID);
		mItems = BundleHelper.toCharSequenceArray(args.getStringArrayList((KEY_ITEMS)));
		mCheckedItems = args.getBooleanArray(KEY_CHECKED_ITEMS);
		mPositiveStringId = args.getInt(KEY_POSITIVE_STRING_ID);
		mNegativeStringId = args.getInt(KEY_NEGATIVE_STRING_ID);

		if (mPositiveStringId > 0 && mNegativeStringId > 0) {
			mPositiveListener = new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					((MultiChoiceDialogListener) getActivity()).onMultiChoiceDialogFinish(getTag(), Activity.RESULT_OK);
					dismiss();
				}
			};

			mNegativeListener = new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					((MultiChoiceDialogListener) getActivity()).onMultiChoiceDialogFinish(getTag(),
							Activity.RESULT_CANCELED);
					dismiss();
				}
			};
		}

		mMultiChoiceClickListener = new OnMultiChoiceClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
				((MultiChoiceDialogListener) getActivity()).onMultiChoiceDialogChange(getTag(), dialog, which,
						isChecked);
			}
		};
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		init();
		if (savedInstanceState != null) {
			boolean[] checked = savedInstanceState.getBooleanArray("checkedItems");
			if (checked != null)
				mCheckedItems = checked;
		}
		AlertDialog.Builder builder;
		if(Build.VERSION.SDK_INT >= 21)
			builder = new AlertDialog.Builder(getActivity(), DreamDroid.getDialogTheme(getActivity()));
		else
			builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getText(mTitleId));
		builder.setMultiChoiceItems(mItems, mCheckedItems, mMultiChoiceClickListener);

		if (mPositiveListener != null) {
			builder.setPositiveButton(mPositiveStringId, mPositiveListener);
		}
		if (mNegativeListener != null) {
			builder.setNegativeButton(mNegativeStringId, mNegativeListener);
		}

		return builder.create();
	}
}