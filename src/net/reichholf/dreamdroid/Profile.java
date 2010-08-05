/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import android.database.Cursor;

/**
 * @author sre
 *
 */
public class Profile {	
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
		
		mLogin = new Boolean(c.getString(c.getColumnIndex(DreamDroid.KEY_LOGIN)));
		mSsl = new Boolean(c.getString(c.getColumnIndex(DreamDroid.KEY_SSL)));
	}
	
	/**
	 * @param mProfile the mProfile to set
	 */
	public void setProfile(String mProfile) {
		this.mProfile = mProfile;
	}

	/**
	 * @param mHost the mHost to set
	 */
	public void setHost(String mHost) {
		this.mHost = mHost;
	}

	/**
	 * @param mUser the mUser to set
	 */
	public void setUser(String mUser) {
		this.mUser = mUser;
	}

	/**
	 * @param mPass the mPass to set
	 */
	public void setPass(String mPass) {
		this.mPass = mPass;
	}

	/**
	 * @param mLogin the mLogin to set
	 */
	public void setLogin(boolean mLogin) {
		this.mLogin = mLogin;
	}

	/**
	 * @param mSsl the mSsl to set
	 */
	public void setSsl(boolean mSsl) {
		this.mSsl = mSsl;
	}

	/**
	 * @param mId the mId to set
	 */
	public void setId(int mId) {
		this.mId = mId;
	}

	/**
	 * @param mPort the mPort to set
	 */
	public void setPort(int mPort) {
		this.mPort = mPort;
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
}
