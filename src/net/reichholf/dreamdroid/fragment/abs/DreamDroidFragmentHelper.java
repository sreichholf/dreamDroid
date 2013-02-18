/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.helpers.Statics;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.actionbarsherlock.app.SherlockFragmentActivity;

public class DreamDroidFragmentHelper {
	public static void finish(DreamDroidFragment fragment, int resultCode, Intent data) {
		finish(fragment.getSherlockActivity(), fragment, resultCode, data);
	}

	public static void finish(DreamDroidListFragment fragment, int resultCode, Intent data) {
		finish(fragment.getSherlockActivity(), fragment, resultCode, data);
	}

	public static void finish(SherlockFragmentActivity activity, Fragment fragment, int resultCode, Intent data) {
		MultiPaneHandler mph = ((MutliPaneContent) fragment).getMultiPaneHandler();
		if (mph.isMultiPane()) {
			boolean explicitShow = false;
			FragmentManager fm = activity.getSupportFragmentManager();
			if (fm.getBackStackEntryCount() > 0) {
				fm.popBackStackImmediate();
			} else {
				explicitShow = true;
			}
			Fragment target = fragment.getTargetFragment();
			FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
			ft.remove(fragment);
			ft.commit();

			if (target != null) {
				if (resultCode != Statics.RESULT_NONE || data != null) {
					if (explicitShow)
						mph.showDetails(target);
					target.onActivityResult(fragment.getTargetRequestCode(), resultCode, data);
				}
			}
		} else {
			activity.setResult(resultCode, data);
			activity.finish();
		}
	}
}
