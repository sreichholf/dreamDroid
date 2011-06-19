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
import android.view.KeyEvent;
import android.view.Menu;

/**
 * @author sre
 *
 */
public class SimpleFragmentActivity extends FragmentActivity implements MultiPaneHandler{
	public static final int MENU_HOME = 89283794;
	
	private Fragment mFragment;
	private ActivityCallbackHandler mCallBackHandler;
		
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_layout); 
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		
		Fragment f = null;
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
		mCallBackHandler = (ActivityCallbackHandler) f;
		ft.add(R.id.content, mFragment);
		ft.commit();
	}
	
	@SuppressWarnings("rawtypes")
	public void showDetails(Class fragmentClass, boolean addToBackStack){		
		Intent intent = new Intent(this, SimpleFragmentActivity.class);
		intent.putExtra("fragmentClass", fragmentClass);
		startActivity(intent);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_HOME, 99, getText(R.string.home)).setIcon(android.R.drawable.ic_menu_view);
		return true;
	}
	
	@Override
	public void showDetails(Fragment fragment){
		showDetails(fragment, true);
	}

	public void showDetails(Fragment fragment, boolean addToBackStack){	
		mCallBackHandler = (ActivityCallbackHandler) fragment;		
		Intent intent = new Intent(this, SimpleFragmentActivity.class);
		intent.putExtra("fragmentClass", fragment.getClass());
		intent.putExtras(fragment.getArguments());
		startActivity(intent);
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
