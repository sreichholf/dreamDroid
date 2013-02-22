/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

/**
 * @author sre
 * 
 */
public class MultiChoiceDialog extends DialogFragment {
	private int mTitleId;
	private CharSequence[] mItems;
	private boolean[] mCheckedItems;
	private OnMultiChoiceClickListener mMultiChoiceClickListener;
	private Dialog.OnClickListener mPositiveListener;
	private int mPositiveStringId;
	private Dialog.OnClickListener mNegativeListener;
	private int mNegativeStringId;

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

	public static MultiChoiceDialog newInstance(int titleId, CharSequence[] items, boolean[] checkedItems,
			OnMultiChoiceClickListener multiChoiceClickListener) {
		return newInstance(titleId, items, checkedItems, multiChoiceClickListener, null, null, 0, 0);
	}

	public static MultiChoiceDialog newInstance(int titleId, CharSequence[] items, boolean[] checkedItems,
			OnMultiChoiceClickListener multiChoiceClickListener, Dialog.OnClickListener positiveListener,
			Dialog.OnClickListener negativeListener, int positiveStringId, int negativeStringId) {

		MultiChoiceDialog fragment = new MultiChoiceDialog(titleId, items, checkedItems, multiChoiceClickListener,
				positiveListener, negativeListener, positiveStringId, negativeStringId);
		// Bundle args = new Bundle();
		// args.putInt(KEY_TITLE_ID, titleId);
		// args.putCharSequenceArray(KEY_ITEMS, items);
		// args.putBooleanArray(KEY_CHECKED_ITEMS, checkedItems);
		// args.putInt(KEY_POSITIVE_STRING_ID, positiveStringId);
		// args.putInt(KEY_NEGATIVE_STRING_ID, negativeStringId);
		// MultiChoiceDialog fragment = new MultiChoiceDialog();
		// fragment.registerListeners(multiChoiceClickListener,
		// positiveListener, negativeListener);
		return fragment;
	}

	private static final String KEY_TITLE_ID = "titleId";
	private static final String KEY_ITEMS = "items";
	private static final String KEY_CHECKED_ITEMS = "checkedItems";
	private static final String KEY_POSITIVE_STRING_ID = "positiveStringId";
	private static final String KEY_NEGATIVE_STRING_ID = "negativeStringId";

	private MultiChoiceDialog(int titleId, CharSequence[] items, boolean[] checkedItems,
			OnMultiChoiceClickListener multiChoiceClickListener, Dialog.OnClickListener positiveListener,
			Dialog.OnClickListener negativeListener, int positiveStringId, int negativeStringId) {
		mTitleId = titleId;
		mItems = items;
		mCheckedItems = checkedItems;
		mMultiChoiceClickListener = multiChoiceClickListener;
		mPositiveListener = positiveListener;
		mNegativeListener = negativeListener;
		mPositiveStringId = positiveStringId;
		mNegativeStringId = negativeStringId;
	}

	private MultiChoiceDialog() {
		Bundle args = getArguments();
		mTitleId = args.getInt(KEY_TITLE_ID);
		mItems = args.getCharSequenceArray(KEY_ITEMS);
		mCheckedItems = args.getBooleanArray(KEY_CHECKED_ITEMS);
		mPositiveStringId = args.getInt(KEY_POSITIVE_STRING_ID);
		mNegativeStringId = args.getInt(KEY_NEGATIVE_STRING_ID);
	}

	public void registerListeners(OnMultiChoiceClickListener multiChoiceClickListener,
			Dialog.OnClickListener positiveListener, Dialog.OnClickListener negativeListener) {
		mPositiveListener = positiveListener;
		mNegativeListener = negativeListener;
		mMultiChoiceClickListener = multiChoiceClickListener;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			boolean[] checked = savedInstanceState.getBooleanArray("checkedItems");
			if (checked != null)
				mCheckedItems = checked;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
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