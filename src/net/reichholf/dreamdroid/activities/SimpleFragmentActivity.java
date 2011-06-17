/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;

/**
 * @author sre
 *
 */
public class SimpleFragmentActivity extends FragmentActivity implements ActivityCallbackHandler{
	private Fragment mFragment;
	private ActivityCallbackHandler mCallBackHandler;
		
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_layout); 
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		
		@SuppressWarnings("unchecked")
		Class c = (Class) getIntent().getExtras().get("fragment");
		Fragment f = null;
		try {
			f = (Fragment) c.newInstance();
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
	
	@Override
	public Dialog onCreateDialog(int id){
		Dialog dialog = mCallBackHandler.onCreateDialog(id);
		if(dialog == null){
			dialog = super.onCreateDialog(id);
		}
		
		return dialog;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event){
		if(mCallBackHandler.onKeyDown(keyCode, event)){
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event){
		if(mCallBackHandler.onKeyUp(keyCode, event)){
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}
}
