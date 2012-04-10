/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.view.KeyEvent;
import android.view.Window;

/**
 * @author sre
 *
 */
public class SimpleFragmentActivity extends FragmentActivity implements MultiPaneHandler{
	public static final int MENU_HOME = 89283794;
	
	private Fragment mFragment;
	private ActivityCallbackHandler mCallBackHandler;
		
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setProgressBarIndeterminateVisibility(false);
		
		if(savedInstanceState != null){
			mFragment = getSupportFragmentManager().getFragment(savedInstanceState, "fragment");
		}
		initViews();
	}
	
	private void initViews(){
		setContentView(R.layout.simple_layout); 
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		
		if(mFragment == null){
			Fragment f = null;
			@SuppressWarnings("unchecked")
			Class<Fragment> c = (Class<Fragment>) getIntent().getExtras().get("fragmentClass");
			Bundle args = new Bundle();
			try {
				f = (Fragment) c.newInstance();
				args.putAll(getIntent().getExtras());
				f.setArguments(args);
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			mFragment = f;
		}
		mCallBackHandler = (ActivityCallbackHandler) mFragment;
		ft.replace(R.id.content, mFragment);
		ft.commit();
	}
		
	@Override
	public void onSaveInstanceState(Bundle outState) {
		getSupportFragmentManager().putFragment(outState, "fragment", mFragment);
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		return true;
	}
	
	@Override
	public void showDetails(Fragment fragment){
		showDetails(fragment, true);
	}
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(java.lang.Class)
	 */
	@Override
	public void showDetails(Class<? extends Fragment> fragmentClass){
		showDetails(fragmentClass, SimpleFragmentActivity.class);
	}
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(java.lang.Class, java.lang.Class)
	 */
	@Override
	public void showDetails(Class<? extends Fragment> fragmentClass, Class<? extends MultiPaneHandler> handlerClass){
		try {
			Fragment fragment = (Fragment) fragmentClass.newInstance();
			showDetails(fragment, handlerClass);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android.support.v4.app.Fragment, boolean)
	 */
	@Override
	public void showDetails(Fragment fragment, boolean addToBackStack){	
		showDetails(fragment, SimpleFragmentActivity.class, addToBackStack);
	}

	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android.support.v4.app.Fragment, java.lang.Class)
	 */
	@Override
	public void showDetails(Fragment fragment, Class<? extends MultiPaneHandler> handlerClass) {
		showDetails(fragment, handlerClass, true);
	}

	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android.support.v4.app.Fragment, java.lang.Class, boolean)
	 */
	@Override
	public void showDetails(Fragment fragment, Class<? extends MultiPaneHandler> cls, boolean addToBackStack) {
		Intent intent = new Intent(this, cls);
		intent.putExtra("fragmentClass", fragment.getClass());
		intent.putExtras(fragment.getArguments());
		
		if(fragment.getTargetRequestCode() > 0){
			startActivityForResult(intent, fragment.getTargetRequestCode());
		} else {
			startActivity(intent);
		}
	}
	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id){
		Dialog dialog = null;
		if(mCallBackHandler != null){
			dialog = mCallBackHandler.onCreateDialog(id);
		}
		
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
	
	@Override
	public boolean isMultiPane(){
		return false;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		mFragment.onActivityResult(requestCode, resultCode, data);
	}
}
