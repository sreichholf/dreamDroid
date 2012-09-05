/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import java.io.Serializable;

import android.database.Cursor;
import android.util.Log;

/**
 * @author sre
 *
 */
public class Profile implements Serializable{	
	/**
	 * 
	 */
	
	private static final long serialVersionUID = 8176949133234868302L;
	
	private String mName;
	private String mHost;
	private String mStreamHost;
	private String mUser;
	private String mPass;
	private boolean mLogin;
	private boolean mSsl;
	private boolean mSimpleRemote;
	private int mId;
	private int mPort;
	private String mInterfaceVersion;

	public Profile(Cursor c){
		set(c);
	}
	
	public Profile(){
		setId(-1);
	}
	
	/**
	 * @param profile
	 * @param host
	 * @param streamHost
	 * @param port
	 * @param login
	 * @param user
	 * @param pass
	 * @param ssl
	 * @param simpleRemote
	 */
	public Profile(String profile, String host, String streamHost, int port, boolean login, String user, String pass, boolean ssl, boolean simpleRemote){
		set(profile, host, streamHost, port, login, user, pass, ssl, simpleRemote);
	}
	

	/**
	 * @param id
	 * @param profile
	 * @param host
	 * @param streamHost
	 * @param port
	 * @param login
	 * @param user
	 * @param pass
	 * @param ssl
	 * @param simpleRemote
	 */
	public Profile(int id, String profile, String host, String streamHost, int port, boolean login, String user, String pass, boolean ssl, boolean simpleRemote){
		set(id, profile, host, streamHost, port, login, user, pass, ssl, simpleRemote);
	}
	
	/**
	 * @param profile
	 * @param host
	 * @param streamHost
	 * @param port
	 * @param login
	 * @param user
	 * @param pass
	 * @param ssl
	 * @param simpleRemote
	 */
	public void set(String profile, String host, String streamHost, int port, boolean login, String user, String pass, boolean ssl, boolean simpleRemote){
		setId(-1);
		setName(profile);
		setHost(host);
		setStreamHost(streamHost);
		setPort(port);
		setLogin(login);
		setUser(user);
		setPass(pass);
		setSsl(ssl);
		setSimpleRemote(simpleRemote);
	}
	

	/**
	 * @param id
	 * @param name
	 * @param host
	 * @param streamHost
	 * @param port
	 * @param login
	 * @param user
	 * @param pass
	 * @param ssl
	 * @param simpleRemote
	 */
	public void set(int id, String name, String host, String streamHost, int port, boolean login, String user, String pass, boolean ssl, boolean simpleRemote){
		setId(id);
		setName(name);
		setHost(host);
		setStreamHost(streamHost);
		setPort(port);
		setLogin(login);
		setUser(user);
		setPass(pass);
		setSsl(ssl);
		setSimpleRemote(simpleRemote);
	}
	
	public void set(Cursor c){
		setName( c.getString(c.getColumnIndex(DreamDroid.KEY_PROFILE)) );
		setHost( c.getString(c.getColumnIndex(DreamDroid.KEY_HOST)) );
		setStreamHost( c.getString(c.getColumnIndex(DreamDroid.KEY_STREAM_HOST)));
		setUser ( c.getString(c.getColumnIndex(DreamDroid.KEY_USER)) );
		setPass ( c.getString(c.getColumnIndex(DreamDroid.KEY_PASS)) );
		
		setId( c.getInt(c.getColumnIndex(DreamDroid.KEY_ID)) );
		setPort( c.getInt(c.getColumnIndex(DreamDroid.KEY_PORT)) );		
		
		int login = c.getInt(c.getColumnIndex(DreamDroid.KEY_LOGIN));
		if(login == 1){
			setLogin(true);
		} else {
			setLogin(false);
		}
		
		int ssl = c.getInt(c.getColumnIndex(DreamDroid.KEY_SSL));
		if(ssl == 1){
			setSsl(true);
		} else {
			setSsl(false);
		}

		int simpleRemote = c.getInt(c.getColumnIndex(DreamDroid.KEY_SIMPLE_REMOTE));
		if(simpleRemote == 1){
			setSimpleRemote(true);
		} else {
			setSimpleRemote(false);
		}
	}
	
	/**
	 * @param mName the Profile to set
	 */
	public void setName(String name) {
		this.mName = name;
	}

	/**
	 * @param mHost the Host to set
	 */
	public void setHost(String host) {
		this.mHost = host.replace("http://", "").replace("https://", "");
	}
	
	/**
	 * @param streamHost the streaming host to set
	 */
	public void setStreamHost(String streamHost){
		if(streamHost == null){
			streamHost = "";
		}
		this.mStreamHost = streamHost.replace("http://", "").replace("https://", "");
	}

	/**
	 * @param mUser the User to set
	 */
	public void setUser(String user) {
		this.mUser = user;
	}

	/**
	 * @param mPass the Pass to set
	 */
	public void setPass(String pass) {
		this.mPass = pass;
	}

	/**
	 * @param mLogin the Login to set
	 */
	public void setLogin(boolean login) {
		this.mLogin = login;
	}

	/**
	 * @param mSsl SSL yes/no
	 */
	public void setSsl(boolean ssl) {
		this.mSsl = ssl;
	}
	
	/**
	 * @param simpleRemote yes/no
	 */
	public void setSimpleRemote(boolean simpleRemote){
		mSimpleRemote = simpleRemote;
	}

	/**
	 * @param mId the Id to set
	 */
	public void setId(int id) {
		this.mId = id;
	}

	/**
	 * @param mPort the Port to set
	 */
	public void setPort(int port) {
		this.mPort = port;
	}
	
	/**
	 * @param port
	 */
	public void setPort(String port){	
		try{
			mPort = Integer.valueOf(port);
		} catch(NumberFormatException e ){
			Log.w(DreamDroid.LOG_TAG, e.toString());
			if(mSsl){
				mPort = 443;
			} else {
				mPort = 80;
			}
		}
	}
	
	/**
	 * @param port
	 * @param ssl
	 */
	public void setPort(String port, boolean ssl){
		mSsl = ssl;		
		setPort(port);
	}

	/**
	 * @return the Profile
	 */
	public String getName() {
		return mName;
	}

	/**
	 * @return the Host
	 */
	public String getHost() {
		return mHost;
	}
	
	/**
	 * @return the host for streaming
	 */
	public String getStreamHost(){
		if("".equals(mStreamHost) || mStreamHost == null){
			return mHost;
		} else {
			return mStreamHost;
		}
	}
	
	/**
	 * @return
	 */
	public String getStreamHostValue(){
		return mStreamHost;
	}

	/**
	 * @return the User
	 */
	public String getUser() {
		return mUser;
	}

	/**
	 * @return the Pass
	 */
	public String getPass() {
		return mPass;
	}

	/**
	 * @return the Login
	 */
	public boolean isLogin() {
		return mLogin;
	}

	/**
	 * @return SSL yes/no
	 */
	public boolean isSsl() {
		return mSsl;
	}
	
	public boolean isSimpleRemote(){
		return mSimpleRemote;
	}

	/**
	 * @return the Id
	 */
	public int getId() {
		return mId;
	}

	/**
	 * @return the Port
	 */
	public int getPort() {
		return mPort;
	}
	
	public String getPortString(){
		return (String.valueOf(mPort));
	}

	public String getInterfaceVersion() {
		return mInterfaceVersion;
	}

	public void setInterfaceVersion(String interfaceVersion) {
		mInterfaceVersion = interfaceVersion;
	}
}
