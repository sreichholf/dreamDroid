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
public class Profile implements Serializable {
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
	private int mStreamPort;
	private int mFilePort;
	private String mDefaultRef;
	private String mDefaultRefName;
	private String mDefaultRef2;
	private String mDefaultRef2Name;

	public Profile(Cursor c) {
		set(c);
	}

	public Profile() {
		setId(-1);
		setPort(80);
		setStreamPort(8001);
		setFilePort(80);
		setLogin(false);
		setSimpleRemote(false);
		setUser("root");
		setPass("dreambox");
		setDefaultRefValues("", "");
		setDefaultRef2Values("", "");
	}

	/**
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
	public Profile(String name, String host, String streamHost, int port, int streamPort, int filePort,
			boolean login, String user, String pass, boolean ssl, boolean simpleRemote) {
		set(name, host, streamHost, port, streamPort, filePort, login, user, pass, ssl, simpleRemote);
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
	public Profile(int id, String name, String host, String streamHost, int port, int streamPort, int filePort,
			boolean login, String user, String pass, boolean ssl, boolean simpleRemote) {
		set(id, name, host, streamHost, port, streamPort, filePort, login, user, pass, ssl, simpleRemote);
	}

	/**
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
	public void set(String name, String host, String streamHost, int port, int streamPort, int filePort,
			boolean login, String user, String pass, boolean ssl, boolean simpleRemote) {
		set(-1, name, host, streamHost, port, streamPort, filePort, login, user, pass, ssl, simpleRemote);
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
	public void set(int id, String name, String host, String streamHost, int port, int streamPort, int filePort,
			boolean login, String user, String pass, boolean ssl, boolean simpleRemote) {
		setId(id);
		setName(name);
		setHost(host);
		setStreamHost(streamHost);
		setPort(port);
		setStreamPort(streamPort);
		setFilePort(filePort);
		setLogin(login);
		setUser(user);
		setPass(pass);
		setSsl(ssl);
		setSimpleRemote(simpleRemote);
		setDefaultRefValues("", "");
		setDefaultRef2Values("", "");
	}

	public void set(Cursor c) {
		setName(c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE)));
		setHost(c.getString(c.getColumnIndex(DatabaseHelper.KEY_HOST)));
		setStreamHost(c.getString(c.getColumnIndex(DatabaseHelper.KEY_STREAM_HOST)));
		setUser(c.getString(c.getColumnIndex(DatabaseHelper.KEY_USER)));
		setPass(c.getString(c.getColumnIndex(DatabaseHelper.KEY_PASS)));

		setId(c.getInt(c.getColumnIndex(DatabaseHelper.KEY_ID)));
		setPort(c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PORT)));

		int streamPort = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_STREAM_PORT));
		if (streamPort <= 0)
			streamPort = 8001;
		setStreamPort(streamPort);

		int filePort = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_FILE_PORT));
		if (filePort <= 0)
			filePort = 80;
		setFilePort(filePort);

		int login = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_LOGIN));
		if (login == 1) {
			setLogin(true);
		} else {
			setLogin(false);
		}

		int ssl = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_SSL));
		if (ssl == 1) {
			setSsl(true);
		} else {
			setSsl(false);
		}

		int simpleRemote = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_SIMPLE_REMOTE));
		if (simpleRemote == 1) {
			setSimpleRemote(true);
		} else {
			setSimpleRemote(false);
		}

		String defaultRef = c.getString(c.getColumnIndex(DatabaseHelper.KEY_DEFAULT_REF));
		String defaultRefName = c.getString(c.getColumnIndex(DatabaseHelper.KEY_DEFAULT_REF_NAME));
		setDefaultRefValues(defaultRef, defaultRefName);

		String defaultRef2 = c.getString(c.getColumnIndex(DatabaseHelper.KEY_DEFAULT_REF_2));
		String defaultRef2Name = c.getString(c.getColumnIndex(DatabaseHelper.KEY_DEFAULT_REF_2_NAME));
		setDefaultRef2Values(defaultRef2, defaultRef2Name);
	}

	/**
	 * @param mName
	 *            the Profile to set
	 */
	public void setName(String name) {
		mName = name;
	}

	/**
	 * @param mHost
	 *            the Host to set
	 */
	public void setHost(String host) {
		mHost = host.replace("http://", "").replace("https://", "");
	}

	/**
	 * @param streamHost
	 *            the streaming host to set
	 */
	public void setStreamHost(String streamHost) {
		if (streamHost == null) {
			streamHost = "";
		}
		mStreamHost = streamHost.replace("http://", "").replace("https://", "");
	}

	/**
	 * @param mUser
	 *            the User to set
	 */
	public void setUser(String user) {
		mUser = user;
	}

	/**
	 * @param mPass
	 *            the Pass to set
	 */
	public void setPass(String pass) {
		mPass = pass;
	}

	/**
	 * @param mLogin
	 *            the Login to set
	 */
	public void setLogin(boolean login) {
		mLogin = login;
	}

	/**
	 * @param mSsl
	 *            SSL yes/no
	 */
	public void setSsl(boolean ssl) {
		mSsl = ssl;
	}

	/**
	 * @param simpleRemote
	 *            yes/no
	 */
	public void setSimpleRemote(boolean simpleRemote) {
		mSimpleRemote = simpleRemote;
	}

	/**
	 * @param mId
	 *            the Id to set
	 */
	public void setId(int id) {
		mId = id;
	}

	/**
	 * @param mPort
	 *            the Port to set
	 */
	public void setPort(int port) {
		mPort = port;
	}

	/**
	 * @param port
	 */
	public void setPort(String port) {
		try {
			mPort = Integer.valueOf(port);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
			if (mSsl) {
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
	public void setPort(String port, boolean ssl) {
		mSsl = ssl;
		setPort(port);
	}

	/**
	 * @param streamPort
	 */
	public void setStreamPort(int streamPort) {
		mStreamPort = streamPort;
	}

	/**
	 * @param streamPort
	 */
	public void setStreamPort(String streamPort) {
		try {
			mStreamPort = Integer.valueOf(streamPort);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
			mStreamPort = 8001;
		}
	}

	/**
	 * @param streamPort
	 */
	public void setFilePort(int filePort) {
		mFilePort = filePort;
	}

	/**
	 * @param streamPort
	 */
	public void setFilePort(String filePort) {
		try {
			mFilePort = Integer.valueOf(filePort);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
			mFilePort = 80;
		}
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
	public String getStreamHost() {
		if ("".equals(mStreamHost) || mStreamHost == null) {
			return mHost;
		} else {
			return mStreamHost;
		}
	}

	/**
	 * @return
	 */
	public String getStreamHostValue() {
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

	public boolean isSimpleRemote() {
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

	public String getPortString() {
		return String.valueOf(mPort);
	}

	public int getStreamPort() {
		return mStreamPort;
	}

	public String getStreamPortString() {
		return String.valueOf(mStreamPort);
	}

	public int getFilePort() {
		return mFilePort;
	}

	public String getFilePortString() {
		return String.valueOf(mFilePort);
	}

	public void setDefaultRefValues(String ref, String name) {
		setDefaultRef(ref);
		setDefaultRefName(name);
	}

	public void setDefaultRef2Values(String ref, String name) {
		setDefaultRef2(ref);
		setDefaultRef2Name(name);
	}

	public String getDefaultRef() {
		return mDefaultRef;
	}

	public void setDefaultRef(String defaultRef) {
		mDefaultRef = defaultRef;
	}

	public String getDefaultRefName() {
		return mDefaultRefName;
	}

	public void setDefaultRefName(String defaultRefName) {
		mDefaultRefName = defaultRefName;
	}

	public String getDefaultRef2() {
		return mDefaultRef2;
	}

	public void setDefaultRef2(String defaultRef2) {
		mDefaultRef2 = defaultRef2;
	}

	public String getDefaultRef2Name() {
		return mDefaultRef2Name;
	}

	public void setDefaultRef2Name(String defaultRef2Name) {
		mDefaultRef2Name = defaultRef2Name;
	}
	
	public boolean equals(Profile p) {
		return getHost().equals(p.getHost())
				&& getStreamHost().equals(p.getStreamHost())
				&& getUser().equals(p.getUser())
				&& getPass().equals(p.getPass())
				&& isLogin() == p.isLogin()
				&& isSsl() == p.isSsl()
				&& isSimpleRemote() == p.isSimpleRemote()
				&& getId() == p.getId()
				&& getPort() == p.getPort()
				&& getStreamPort() == p.getStreamPort()
				&& getFilePort() == p.getFilePort();
	}
}
