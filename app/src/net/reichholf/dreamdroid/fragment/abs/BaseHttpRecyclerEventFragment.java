package net.reichholf.dreamdroid.fragment.abs;

import android.app.ProgressDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;


/**
 * @author sreichholf
 */
public abstract class BaseHttpRecyclerEventFragment extends BaseHttpRecyclerFragment implements
        ActionDialog.DialogActionListener {

    private static final String KEY_REFERENCE = "reference";
    private static final String KEY_NAME = "name";
    private static final String KEY_CURRENT_ITEM = "currentItem";

    @Nullable
    public String mReference;
    @Nullable
    public String mName;
    public ExtendedHashMap mCurrentItem;

    protected ProgressDialog mProgress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mReference = savedInstanceState.getString(KEY_REFERENCE);
            mName = savedInstanceState.getString(KEY_NAME);
            @SuppressWarnings("deprecation")
            ExtendedHashMap item = (ExtendedHashMap) savedInstanceState.getSerializable(KEY_CURRENT_ITEM);
            mCurrentItem = item;
        }
        if (mCurrentItem == null) {
            mCurrentItem = new ExtendedHashMap();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(KEY_REFERENCE, mReference);
        outState.putString(KEY_NAME, mName);
        outState.putSerializable(KEY_CURRENT_ITEM, mCurrentItem);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onItemClick(RecyclerView parent, View view, int position, long id) {
        mCurrentItem = mMapList.get(position);
        EpgDetailBottomSheet epgDetailBottomSheet = EpgDetailBottomSheet.newInstance(mCurrentItem);
        getMultiPaneHandler().showDialogFragment(epgDetailBottomSheet, "epg_detail_dialog");
    }

    /**
     * @param event
     */
    protected void setTimerById(@NonNull ExtendedHashMap event) {
        if (mProgress != null) {
            if (mProgress.isShowing()) {
                mProgress.dismiss();
            }
        }

        mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.saving), true);
        execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
    }

    @Override
    public void onSimpleResult(boolean success, ExtendedHashMap result) {
        if (mProgress != null) {
            if (mProgress.isShowing()) {
                mProgress.dismiss();
            }
        }
        super.onSimpleResult(success, result);
    }

    /**
     * @param event
     */
    protected void setTimerByEventData(@NonNull ExtendedHashMap event) {
        Timer.editUsingEvent(getMultiPaneHandler(), event, this);
    }

    public void onDialogAction(int action, Object details, String dialogTag) {
        switch (action) {
            case Statics.ACTION_SET_TIMER:
                setTimerById(mCurrentItem);
                break;
            case Statics.ACTION_EDIT_TIMER:
                setTimerByEventData(mCurrentItem);
                break;
            case Statics.ACTION_FIND_SIMILAR:
                mHttpHelper.findSimilarEvents(mCurrentItem);
                break;
            case Statics.ACTION_IMDB:
                IntentFactory.queryIMDb(getAppCompatActivity(), mCurrentItem);
                break;
        }
    }
}
