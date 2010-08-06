/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import java.io.Serializable;

import android.database.Cursor;

/**
 * @author sre
 *
 */
public class Profile implements Serializable{	
	/**
	 * 
	 */
	private static final long serialVersionUID = 8176949133234868302L;
	
	private String mProfile;
	private String mHost;
	private String mUser;
	private String mPass;
	private boolean mLogin;
	private boolean mSsl;
	private int mId;
	private int mPort;
	
	public Profile(Cursor c){
		set(c);
	}
	
	public Profile(){
		mId = -1;
	}
	
	public Profile(String profile, String host, int port, boolean login, String user, String pass, boolean ssl){
		set(profile, host, port, login, user, pass, ssl);
	}
	
	public Profile(int id, String profile, String host, int port, boolean login, String user, String pass, boolean ssl){
		set(profile, host, port, login, user, pass, ssl);
	}
	
	/**
	 * @param profile
	 * @param host
	 * @param port
	 * @param login
	 * @param user
	 * @param pass
	 * @param ssl
	 */
	public void set(String profile, String host, int port, boolean login, String user, String pass, boolean ssl){
		mId = -1;
		mProfile = profile;
		mHost = host;
		mPort = port;
		mLogin = login;
		mUser = user;
		mPass = pass;
		mSsl = ssl;
	}
	
	/**
	 * @param id
	 * @param profile
	 * @param host
	 * @param port
	 * @param login
	 * @param user
	 * @param pass
	 * @param ssl
	 */
	public void set(int id, String profile, String host, int port, boolean login, String user, String pass, boolean ssl){
		mId = id;
		mProfile = profile;
		mHost = host;
		mPort = port;
		mLogin = login;
		mUser = user;
		mPass = pass;
		mSsl = ssl;
	}
	
	public void set(Cursor c){
		mProfile = c.getString(c.getColumnIndex(DreamDroid.KEY_PROFILE));
		mHost = c.getString(c.getColumnIndex(DreamDroid.KEY_HOST));
		mUser = c.getString(c.getColumnIndex(DreamDroid.KEY_USER));
		mPass = c.getString(c.getColumnIndex(DreamDroid.KEY_PASS));
		
		mId = c.getInt(c.getColumnIndex(DreamDroid.KEY_ID));
		mPort = c.getInt(c.getColumnIndex(DreamDroid.KEY_PORT));
		
		int login = c.getInt(c.getColumnIndex(DreamDroid.KEY_LOGIN));
		if(login == 1){
			mLogin = true;
		} else {
			mLogin = false;
		}
		
		int ssl = c.getInt(c.getColumnIndex(DreamDroid.KEY_SSL));
		if(ssl == 1){
			mSsl = true;
		} else {
			mSsl = false;
		}
		
	}
	
	/**
	 * @param mProfile the mProfile to set
	 */
	public void setProfile(String profile) {
		this.mProfile = profile;
	}

	/**
	 * @param mHost the mHost to set
	 */
	public void setHost(String host) {
		this.mHost =host;
	}

	/**
	 * @param mUser the mUser to set
	 */
	public void setUser(String user) {
		this.mUser = user;
	}

	/**
	 * @param mPass the mPass to set
	 */
	public void setPass(String pass) {
		this.mPass = pass;
	}

	/**
	 * @param mLogin the mLogin to set
	 */
	public void setLogin(boolean login) {
		this.mLogin = login;
	}

	/**
	 * @param mSsl the mSsl to set
	 */
	public void setSsl(boolean ssl) {
		this.mSsl = ssl;
	}

	/**
	 * @param mId the mId to set
	 */
	public void setId(int id) {
		this.mId = id;
	}

	/**
	 * @param mPort the mPort to set
	 */
	public void setPort(int port) {
		this.mPort = port;
	}
	
	public void setPort(String port){
		mPort = new Integer(port);
	}

	/**
	 * @return the mProfile
	 */
	public String getProfile() {
		return mProfile;
	}

	/**
	 * @return the mHost
	 */
	public String getHost() {
		return mHost;
	}

	/**
	 * @return the mUser
	 */
	public String getUser() {
		return mUser;
	}

	/**
	 * @return the mPass
	 */
	public String getPass() {
		return mPass;
	}

	/**
	 * @return the mLogin
	 */
	public boolean isLogin() {
		return mLogin;
	}

	/**
	 * @return the mSsl
	 */
	public boolean isSsl() {
		return mSsl;
	}

	/**
	 * @return the mId
	 */
	public int getId() {
		return mId;
	}

	/**
	 * @return the mPort
	 */
	public int getPort() {
		return mPort;
	}
	
	public String getPortString(){
		return (new Integer(mPort).toString());
	}
}
