/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.evernote.android.state.State;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.livefront.bridge.Bridge;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.BundleHelper;

import java.util.ArrayList;

/**
 * @author sre
 */
public class MultiChoiceDialog extends DialogFragment {
    private static final String KEY_TITLE_ID = "titleId";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_CHECKED_ITEMS = "checkedItems";
    private static final String KEY_POSITIVE_STRING_ID = "positiveStringId";

    @State
    public boolean[] mCheckedItems;

    private int mTitleId;
    private CharSequence[] mItems;
    private int mPositiveStringId;

    public interface MultiChoiceDialogListener {
        void onMultiChoiceDialogSelection(String dialogTag, DialogInterface dialog, Integer[] selected);

        void onMultiChoiceDialogFinish(String dialogTag, int result);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);
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
        Bridge.saveInstanceState(this, outState);
        super.onSaveInstanceState(outState);
    }

    public static MultiChoiceDialog newInstance(int titleId, CharSequence[] items, boolean[] checkedItems) {
        return MultiChoiceDialog.newInstance(titleId, items, checkedItems, R.string.ok, -1);
    }

    public static MultiChoiceDialog newInstance(int titleId, CharSequence[] items, boolean[] checkedItems,
                                                int positiveStringId, int negativeStringId) {

        MultiChoiceDialog fragment = new MultiChoiceDialog();
        Bundle args = new Bundle();
        args.putInt(KEY_TITLE_ID, titleId);
        args.putStringArrayList(KEY_ITEMS, BundleHelper.toStringArrayList(items));
        args.putBooleanArray(KEY_CHECKED_ITEMS, checkedItems);
        args.putInt(KEY_POSITIVE_STRING_ID, positiveStringId);
        fragment.setArguments(args);
        return fragment;
    }

    public void init() {
        Bundle args = getArguments();
        mTitleId = args.getInt(KEY_TITLE_ID);
        mItems = BundleHelper.toCharSequenceArray(args.getStringArrayList((KEY_ITEMS)));
        mCheckedItems = args.getBooleanArray(KEY_CHECKED_ITEMS);
        mPositiveStringId = args.getInt(KEY_POSITIVE_STRING_ID);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        init();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setTitle(mTitleId)
                .setMultiChoiceItems(mItems, mCheckedItems, (dialog, which, isChecked) -> mCheckedItems[which] = isChecked)
                .setPositiveButton(mPositiveStringId, (dialog, which) -> {
                    ArrayList<Integer> selectedList = new ArrayList<>();
                    for (int i = 0; i < mCheckedItems.length; ++i) {
                        if (mCheckedItems[i])
                            selectedList.add(i);
                    }
                    Integer[] selected = new Integer[selectedList.size()];
                    selectedList.toArray(selected);
                    ((MultiChoiceDialogListener) getActivity()).onMultiChoiceDialogSelection(getTag(), dialog, selected);
                    ((MultiChoiceDialogListener) getActivity()).onMultiChoiceDialogFinish(getTag(), Activity.RESULT_OK);
                    dialog.dismiss();
                });
        return builder.create();
    }
}