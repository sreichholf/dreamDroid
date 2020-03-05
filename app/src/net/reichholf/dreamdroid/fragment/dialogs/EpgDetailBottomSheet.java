/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;

/**
 * @author sre
 */
public class EpgDetailBottomSheet extends BottomSheetActionDialog {
    private ExtendedHashMap mCurrentItem;

    public static EpgDetailBottomSheet newInstance(ExtendedHashMap epg) {
        return newInstance(epg, false);
    }

    public static EpgDetailBottomSheet newInstance(ExtendedHashMap epg, boolean showNext) {

        Bundle args = new Bundle();
        args.putSerializable(Event.class.getSimpleName(), epg);
        args.putBoolean("showNext", showNext);
        EpgDetailBottomSheet fragment = new EpgDetailBottomSheet();
        fragment.setArguments(args);
        return fragment;
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
        super.onSaveInstanceState(outState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Dialog dialog;

        Bundle args = getArguments();
        mCurrentItem = (ExtendedHashMap) args.getSerializable(Event.class.getSimpleName());
        final boolean isNext = args.getBoolean("showNext", false);
        String servicename = "";
        String title = "N/A";
        String date = null;
        String prefix = "";
        if (mCurrentItem != null) {
            if (isNext)
                prefix = Event.PREFIX_NEXT;
            servicename = mCurrentItem.getString(Event.KEY_SERVICE_NAME);
            title = mCurrentItem.getString(prefix + Event.KEY_EVENT_TITLE);
            date = mCurrentItem.getString(prefix + Event.KEY_EVENT_START_READABLE);
        }
        if (!"N/A".equals(title) && date != null) {
            dialog = super.onCreateDialog(savedInstanceState);
            date = date.concat(" (" + mCurrentItem.getString(prefix + Event.KEY_EVENT_DURATION_READABLE) + " "
                    + getText(R.string.minutes_short) + ")");
            String descShort = mCurrentItem.getString(prefix + Event.KEY_EVENT_DESCRIPTION, "");
            String descEx = mCurrentItem.getString(prefix + Event.KEY_EVENT_DESCRIPTION_EXTENDED);

            View view = View.inflate(getContext(), R.layout.epg_item_dialog, null);

            Toolbar tb = view.findViewById(R.id.toolbar_epg_detail);
            tb.setTitle(title);

            TextView textServiceName = view.findViewById(R.id.service);
            textServiceName.setText(servicename);

            TextView textShort = view.findViewById(R.id.description);
            if ("".equals(descShort))
                textShort.setVisibility(View.GONE);
            else
                textShort.setText(descShort);
            TextView textTime = view.findViewById(R.id.date);
            textTime.setText(date);

            TextView textDescEx = view.findViewById(R.id.description_extended);
            textDescEx.setText(descEx);

            Button buttonSetTimer = view.findViewById(R.id.ButtonSetTimer);
            buttonSetTimer.setOnClickListener(v -> finishDialog(Statics.ACTION_SET_TIMER, isNext));

            Button buttonEditTimer = view.findViewById(R.id.ButtonEditTimer);
            buttonEditTimer.setOnClickListener(v -> finishDialog(Statics.ACTION_EDIT_TIMER, isNext));

            Button buttonIMDb = view.findViewById(R.id.ButtonImdb);
            buttonIMDb.setOnClickListener(v -> finishDialog(Statics.ACTION_IMDB, isNext));

            Button buttonSimilar = view.findViewById(R.id.ButtonSimilar);
            buttonSimilar.setOnClickListener(v -> finishDialog(Statics.ACTION_FIND_SIMILAR, isNext));
            dialog.setContentView(view);

            BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(((View) view.getParent()));
            if (bottomSheetBehavior != null) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        } else {
            dialog = new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.not_available)
                    .setMessage(R.string.no_epg_available)
                    .setPositiveButton(R.string.close, (dialog1, which) -> dismiss()).create();

        }
        return dialog;
    }
}
