/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import java.io.Serializable;

import android.util.Log;


/**
 * @author sre
 */
public class Profile implements Serializable {

	private static final long serialVersionUID = 8176949133234868302L;

    private final int mId;
    private String mName;
	private String mHost;
	private String mStreamHost;
	private String mUser;
	private String mPass;
	private boolean mLogin;
	private boolean mSsl;
	private boolean mStreamLogin;
	private boolean mFileLogin;
	private boolean mFileSsl;
	private boolean mSimpleRemote;
	private int mPort;
	private int mStreamPort;
	private int mFilePort;
	private String mDefaultRef;
	private String mDefaultRefName;
	private String mDefaultRef2;
	private String mDefaultRef2Name;


    public static final Profile DEFAULT = new Profile(-1, "", "", "", 80, 8001, 80, false,"root", "dreambox", false, false, false, false, false, "", "", "", "");

	public Profile(int id, String name, String host, String streamHost, int port, int streamPort, int filePort, boolean login,
			String user, String pass, boolean ssl, boolean streamLogin, boolean fileLogin, boolean fileSsl,
			boolean simpleRemote, String defaultRef, String defaultRefName, String defaultRef2, String defaultRef2Name) {

        mId = id;
        setName(name);
        setHost(host);
        setStreamHost(streamHost);
        setPort(port);
        setStreamPort(streamPort);
        setFilePort(filePort);
        setLogin(login);
        setStreamLogin(streamLogin);
        setFileLogin(fileLogin);
        setFileSsl(fileSsl);
        setUser(user);
        setPass(pass);
        setSsl(ssl);
        setSimpleRemote(simpleRemote);
        setDefaultRefValues(defaultRef, defaultRefName);
        setDefaultRef2Values(defaultRef2, defaultRef2Name);
	}

	public void setName(String name) {
		if (name == null) {
            name = "";
        }
		mName = name;
	}

	public void setHost(String host) {
		if (host == null) {
            host = "";
        }
		mHost = host.replace("http://", "").replace("https://", "");
	}

	public void setStreamHost(String streamHost) {
		if (streamHost == null)
			streamHost = "";
		mStreamHost = streamHost.replace("http://", "").replace("https://", "");
	}

	public void setUser(String user) {
		if (user == null)
			user = "";
		mUser = user;
	}

	public void setPass(String pass) {
		if (pass == null)
			pass = "";
		mPass = pass;
	}

	public void setLogin(boolean login) {
		mLogin = login;
	}

	public void setSsl(boolean ssl) {
		mSsl = ssl;
	}

	public void setFileLogin(boolean login) {
		mFileLogin = login;
	}

	public void setFileSsl(boolean ssl) {
		mFileSsl = ssl;
	}

	public void setSimpleRemote(boolean simpleRemote) {
		mSimpleRemote = simpleRemote;
	}

	public void setPort(int port) {
		mPort = port;
	}

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

	public void setPort(String port, boolean ssl) {
		mSsl = ssl;
		setPort(port);
	}

	public void setStreamPort(int streamPort) {
		mStreamPort = streamPort;
	}

	public void setStreamPort(String streamPort) {
		try {
			mStreamPort = Integer.valueOf(streamPort);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
			mStreamPort = 8001;
		}
	}

	public void setFilePort(int filePort) {
		mFilePort = filePort;
	}

	public void setFilePort(String filePort) {
		try {
			mFilePort = Integer.valueOf(filePort);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
			mFilePort = 80;
		}
	}

	public String getName() {
		return mName;
	}

	public String getHost() {
		return mHost;
	}

	public String getStreamHost() {
		if ("".equals(mStreamHost) || mStreamHost == null) {
			return mHost;
		} else {
			return mStreamHost;
		}
	}


	public String getStreamHostValue() {
		return mStreamHost;
	}

	public String getUser() {
		return mUser;
	}

	public String getPass() {
		return mPass;
	}

	public boolean isLogin() {
		return mLogin;
	}

	public boolean isSsl() {
		return mSsl;
	}

	public boolean isFileLogin() {
		return mFileLogin;
	}

	public boolean isFileSsl() {
		return mFileSsl;
	}

	public boolean isSimpleRemote() {
		return mSimpleRemote;
	}

	public int getId() {
		return mId;
	}

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

	public boolean isStreamLogin() {
		return mStreamLogin;
	}

	public void setStreamLogin(boolean streamLogin) {
		mStreamLogin = streamLogin;
	}

	public boolean equals(Profile p) {
		return getHost().equals(p.getHost()) && getStreamHost().equals(p.getStreamHost())
				&& getUser().equals(p.getUser()) && getPass().equals(p.getPass()) && isLogin() == p.isLogin()
				&& isSsl() == p.isSsl() && isSimpleRemote() == p.isSimpleRemote() && getId() == p.getId()
				&& getPort() == p.getPort() && getStreamPort() == p.getStreamPort() && getFilePort() == p.getFilePort()
				&& isStreamLogin() == p.isStreamLogin() && isFileSsl() == p.isFileSsl()
				&& isFileLogin() == p.isFileLogin();
	}

}
