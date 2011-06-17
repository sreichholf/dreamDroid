/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.NavigationFragment;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

/**
 * @author sre
 *
 */
public class FragmentMainActivity extends FragmentActivity{	
	private boolean mMultiPane;
	
	private FragmentManager mFragmentManager;
	private ListFragment mNavigationFragment;
	private ActivityCallbackHandler mCallBackHandler;
	
	public void onCreate(Bundle savedInstanceState){		
		super.onCreate(savedInstanceState);	
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		checkLayout();
		
		mFragmentManager = getSupportFragmentManager();
		FragmentTransaction ft = mFragmentManager.beginTransaction();
		mNavigationFragment = new NavigationFragment();
		
		if(mMultiPane){
			setContentView(R.layout.dualpane);
			ft.add(R.id.navigation_view, mNavigationFragment);
			ft.commit();
		} else {
			setContentView(R.layout.list_or_empty);
			ft.add(R.id.content, mNavigationFragment);
			ft.commit();
		}	
	}
	
	private void checkLayout(){
		mMultiPane = false;
		
		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay(); 
		int width = display.getWidth();
		int height = display.getHeight();
		if(height >= 1024 || width >= 1024){
			mMultiPane = true;
		}		
	}
	
	/**
	 * @param fragment
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	@SuppressWarnings("rawtypes")
	public void showDetails(Class fragmentClass){
		if(mMultiPane){
			Fragment fragment = null;
			try {
				fragment = (Fragment) fragmentClass.newInstance();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(fragment != null){
				mCallBackHandler = (ActivityCallbackHandler) fragment;
				
				FragmentTransaction ft = mFragmentManager.beginTransaction();
				ft.replace(R.id.detail_view, fragment);
				ft.addToBackStack(null);
				ft.commit();
			} //TODO Error Handling
		} else {
			Intent intent = new Intent(this, SimpleFragmentActivity.class);
			intent.putExtra("fragment", fragmentClass);
			startActivity(intent);
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id){
		Dialog dialog = mCallBackHandler.onCreateDialog(id);
		if(dialog == null){
			dialog = super.onCreateDialog(id);
		}
		
		return dialog;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event){
		if(mCallBackHandler != null){
			if(mCallBackHandler.onKeyDown(keyCode, event)){
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event){
		if(mCallBackHandler != null){
			if(mCallBackHandler.onKeyUp(keyCode, event)){
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}
}
