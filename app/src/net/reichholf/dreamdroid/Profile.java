/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Update;

import java.io.Serializable;
import java.util.List;


@Entity( tableName = "profile")
public class Profile implements Serializable {
	private static final long serialVersionUID = 8176949133234868302L;
	@Dao
	public interface ProfileDao {

		@Insert(onConflict = OnConflictStrategy.REPLACE)
		long addProfile(Profile profile);

		@Update
		void updateProfile(Profile profiles);

		@Delete
		void deleteProfile(Profile profile);

		@Query("SELECT * FROM profile")
		List<Profile> getProfiles();

		@Query("SELECT * FROM profile WHERE _id=:id")
		Profile getProfile(int id);
	}


	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name= "_id")
	public Integer id;

	@ColumnInfo(name = "profile")
	public String name;

	@ColumnInfo(name = "host")
	public String host;

	@ColumnInfo(name = "streamhost")
	public String streamHost;

	@ColumnInfo(name = "encoder_path")
	public String encoderPath;

	@ColumnInfo(name = "user")
	public String user;

	@ColumnInfo(name = "pass")
	public String pass;

	@ColumnInfo(name = "encoder_user")
	public String encoderUser;

	@ColumnInfo(name = "encoder_pass")
	public String encoderPass;

	@ColumnInfo(name = "login")
	public boolean login;

	@ColumnInfo(name = "ssl")
	public boolean ssl;

	@ColumnInfo(name = "trust_all_certs")
	public boolean allCertsTrusted;

	@ColumnInfo(name = "streamlogin")
	public boolean streamLogin;

	@ColumnInfo(name = "file_login")
	public boolean fileLogin;

	@ColumnInfo(name = "encoder_login")
	public boolean encoderLogin;

	@ColumnInfo(name = "encoder_stream")
	public boolean encoderStream;

	@ColumnInfo(name = "file_ssl")
	public boolean fileSsl;

	@ColumnInfo(name = "simpleremote")
	public boolean simpleRemote;

	@ColumnInfo(name = "port")
	public int port;

	@ColumnInfo(name = "streamport")
	public int streamPort;

	@ColumnInfo(name = "fileport")
	public int filePort;

	@ColumnInfo(name = "encoder_port")
	public int encoderPort;

	@ColumnInfo(name = "encoder_audio_bitrate")
	public int encoderAudioBitrate;

	@ColumnInfo(name = "encoder_video_bitrate")
	public int encoderVideoBitrate;


	@ColumnInfo(name = "default_ref")
	public String defaultBouquetTv;

	@ColumnInfo(name = "default_ref_name")
	public String defaultBouquetTvName;

	@ColumnInfo(name = "default_ref_2")
	public String defaultParentBouquetTv;

	@ColumnInfo(name = "default_ref_2_name")
	public String defaultParentBouquetTvName;

	@Ignore
	public String sessionid;

	@Ignore
	public String cachedDeviceInfo;

	@ColumnInfo(name = "ssid")
	public String ssid;

	@ColumnInfo(name = "defaultProfileOnNoWifi")
	public boolean isDefaultProfileOnNoWifi;

	@Ignore
	public static Profile getDefault() {
		return new Profile(null, "", "", "", 80, 8001, 80, false, "root", "dreambox", false, false, false, false, false, "", "", "", "");
	}

	@Ignore
	public Profile(Integer id, String profile, String host, String streamHost, int port, int streamPort, int filePort, boolean login,
				   String user, String pass, boolean ssl, boolean streamLogin, boolean fileLogin, boolean fileSsl,
				   boolean simpleRemote, String defaultRef, String defaultRefName, String defaultRef2, String defaultRef2Name) {
		init(id, profile, host, streamHost, port, streamPort, filePort, login, user, pass, ssl, false, streamLogin, fileLogin, fileSsl, simpleRemote, defaultRef, defaultRefName, defaultRef2, defaultRef2Name, false, "stream", 554, false, "", "", 2500, 128);
	}


	public Profile(int id, String name, String host, String streamHost, int port, int streamPort, int filePort, boolean login,
				   String user, String pass, boolean ssl, boolean allCertsTrusted, boolean streamLogin, boolean fileLogin, boolean fileSsl,
				   boolean simpleRemote, String defaultBouquetTv, String defaultBouquetTvName, String defaultParentBouquetTv, String defaultParentBouquetTvName, boolean encoderStream,
				   String encoderPath, int encoderPort, boolean encoderLogin, String encoderUser, String encoderPass, int encoderVideoBitrate, int encoderAudioBitrate) {
		init(id, name, host, streamHost, port, streamPort, filePort, login, user, pass, ssl, allCertsTrusted, streamLogin, fileLogin, fileSsl, simpleRemote, defaultBouquetTv, defaultBouquetTvName, defaultParentBouquetTv, defaultParentBouquetTvName, encoderStream, encoderPath, encoderPort, encoderLogin, encoderUser, encoderPass, encoderVideoBitrate, encoderAudioBitrate);
	}

	private void init(Integer id, String name, String host, String streamHost, int port, int streamPort, int filePort, boolean login,
					  String user, String pass, boolean ssl, boolean allCertsTrusted, boolean streamLogin, boolean fileLogin, boolean fileSsl,
					  boolean simpleRemote, String defaultRef, String defaultRefName, String defaultRef2, String defaultRef2Name, boolean encoderStream,
					  String encoderPath, int encoderPort, boolean encoderLogin, String encoderUser, String encoderPass, int encoderVideoBitrate, int encoderAudioBitrate) {
		this.id = id;
		sessionid = null;
		cachedDeviceInfo = null;

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
		setAllCertsTrusted(allCertsTrusted);
		setSimpleRemote(simpleRemote);
		setDefaultRefValues(defaultRef, defaultRefName);
		setDefaultRef2Values(defaultRef2, defaultRef2Name);
		//Encoder
		setEncoderStream(encoderStream);
		setEncoderPort(encoderPort);
		setEncoderPath(encoderPath);
		setEncoderAudioBitrate(encoderAudioBitrate);
		setEncoderVideoBitrate(encoderVideoBitrate);
		setEncoderLogin(encoderLogin);
		setEncoderUser(encoderUser);
		setEncoderPass(encoderPass);
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setId(long id) {
		this.id = (int) id;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setPort(@NonNull String port, boolean ssl) {
		setSsl(ssl);
		setPort(port);
	}

	public void setPort(@NonNull String port, boolean ssl, boolean isAllCertsTrusted) {
		setSsl(ssl);
		setAllCertsTrusted(isAllCertsTrusted);
		setPort(port);
	}

	public void setStreamPort(int streamPort) {
		this.streamPort = streamPort;
	}

	public void setFilePort(int filePort) {
		this.filePort = filePort;
	}

	@Nullable
	public String getName() {
		return name;
	}

	public void setName(@Nullable String name) {
		if (name == null) {
			name = "";
		}
		this.name = name;
	}

	public String getHost() {
		return host;
	}

	public void setHost(@Nullable String host) {
		if (host == null) {
			host = "";
		}
		this.host = host.replace("http://", "").replace("https://", "");
	}

	public String getStreamHost() {
		if ("".equals(streamHost) || streamHost == null) {
			return host;
		} else {
			return streamHost;
		}
	}

	public void setStreamHost(@Nullable String streamHost) {
		if (streamHost == null)
			streamHost = "";
		this.streamHost = streamHost.replace("http://", "").replace("https://", "");
	}

	public String getStreamHostValue() {
		return streamHost;
	}

	public void setEncoderPath(String encoderPath) {
		this.encoderPath = encoderPath;
	}

	public String getEncoderPath() {
		return encoderPath;
	}

	@Nullable
	public String getUser() {
		return user;
	}

	public void setUser(@Nullable String user) {
		if (user == null)
			user = "";
		this.user = user;
	}

	@Nullable
	public String getPass() {
		return pass;
	}

	public void setPass(@Nullable String pass) {
		if (pass == null)
			pass = "";
		this.pass = pass;
	}

	public boolean isLogin() {
		return login;
	}

	public void setLogin(boolean login) {
		this.login = login;
	}

	public String getEncoderUser() {
		return encoderUser;
	}

	public void setEncoderUser(String user) {
		encoderUser = user;
	}

	public String getEncoderPass() {
		return encoderPass;
	}

	public void setEncoderPass(String pass) {
		encoderPass = pass;
	}

	public boolean isEncoderLogin() {
		return encoderLogin;
	}

	public void setEncoderLogin(boolean isLogin) {
		encoderLogin = isLogin;
	}

	public boolean isSsl() {
		return ssl;
	}

	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public boolean isAllCertsTrusted() {
		return allCertsTrusted;
	}

	public void setAllCertsTrusted(boolean allCertsTrusted) {
		this.allCertsTrusted = allCertsTrusted;
	}

	public boolean isFileLogin() {
		return fileLogin;
	}

	public void setFileLogin(boolean login) {
		fileLogin = login;
	}

	public boolean isFileSsl() {
		return fileSsl;
	}

	public void setFileSsl(boolean ssl) {
		fileSsl = ssl;
	}

	public boolean isSimpleRemote() {
		return simpleRemote;
	}

	public void setSimpleRemote(boolean simpleRemote) {
		this.simpleRemote = simpleRemote;
	}

	public Integer getId() {
		return id;
	}

	public int getPort() {
		return port;
	}

	public void setPort(@NonNull String port) {
		try {
			this.port = Integer.valueOf(port);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
			if (ssl) {
				this.port = 443;
			} else {
				this.port = 80;
			}
		}
	}

	@NonNull
	public String getPortString() {
		return String.valueOf(port);
	}

	public int getStreamPort() {
		return streamPort;
	}

	public void setStreamPort(@NonNull String streamPort) {
		try {
			this.streamPort = Integer.valueOf(streamPort);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
		}
	}


	public boolean isEncoderStream() {
		return encoderStream;
	}

	public void setEncoderStream(boolean encoderStream) {
		this.encoderStream = encoderStream;
	}

	@NonNull
	public String getStreamPortString() {
		return String.valueOf(streamPort);
	}

	public int getEncoderPort() {
		return encoderPort;
	}
	@NonNull
	public String getEncoderPortString() {
		return String.valueOf(encoderPort);
	}

	public void setEncoderPort(int port) {
		encoderPort = port;
	}

	public void setEncoderPort(@NonNull String port) {
		try {
			encoderPort = Integer.valueOf(port);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
		}
	}

	public int getEncoderVideoBitrate() {
		return encoderVideoBitrate;
	}

	@NonNull
	public String getEncoderVideoBitrateString() {
		return String.valueOf(encoderVideoBitrate);
	}

	public void setEncoderVideoBitrate(int bitrate) {
		encoderVideoBitrate = bitrate;
	}

	public void setEncoderVideoBitrate(@NonNull String bitrate) {
		try {
			encoderVideoBitrate = Integer.valueOf(bitrate);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
		}
	}

	public int getEncoderAudioBitrate() {
		return encoderAudioBitrate;
	}

	@NonNull
	public String getEncoderAudioBitrateString() {
		return String.valueOf(encoderAudioBitrate);
	}

	public void setEncoderAudioBitrate(int bitrate) {
		encoderAudioBitrate = bitrate;
	}

	public void setEncoderAudioBitrate(@NonNull String bitrate) {
		try {
			encoderAudioBitrate = Integer.valueOf(bitrate);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
		}
	}

	public int getFilePort() {
		return filePort;
	}

	public void setFilePort(@NonNull String filePort) {
		try {
			this.filePort = Integer.valueOf(filePort);
		} catch (NumberFormatException e) {
			Log.w(DreamDroid.LOG_TAG, e.toString());
			this.filePort = 80;
		}
	}

	@NonNull
	public String getFilePortString() {
		return String.valueOf(filePort);
	}

	public void setDefaultRefValues(String ref, String name) {
		setDefaultBouquetTv(ref);
		setDefaultBouquetTvName(name);
	}

	public void setDefaultRef2Values(String ref, String name) {
		setParentBouquetTv(ref);
		setParentBouquetTvName(name);
	}

	public String getDefaultBouquetTv() {
		return defaultBouquetTv;
	}

	public void setDefaultBouquetTv(String defaultBouquetTv) {
		this.defaultBouquetTv = defaultBouquetTv;
	}

	public String getDefaultBouquetTvName() {
		return defaultBouquetTvName;
	}

	public void setDefaultBouquetTvName(String defaultRefName) {
		defaultBouquetTvName = defaultRefName;
	}

	public String getParentBouquetTv() {
		return defaultParentBouquetTv;
	}

	public void setParentBouquetTv(String defaultRef2) {
		defaultParentBouquetTv = defaultRef2;
	}

	public String getParentBouquetTvName() {
		return defaultParentBouquetTvName;
	}

	public void setParentBouquetTvName(String defaultRef2Name) {
		defaultParentBouquetTvName = defaultRef2Name;
	}

	public boolean isStreamLogin() {
		return streamLogin;
	}

	public void setStreamLogin(boolean streamLogin) {
		this.streamLogin = streamLogin;
	}

	public void setSessionId(String sessionId) {
		sessionid = sessionId;
	}

	@Nullable
	public String getSessionId() {
		return sessionid;
	}

	public void setCachedDeviceInfo(String deviceInfo) {
		cachedDeviceInfo = deviceInfo;
	}

	@Nullable
	public String getCachedDeviceInfo() {
		return cachedDeviceInfo;
	}

	public String getSsid() {
		return ssid;
	}

	public void setSsid(String ssid) {
		this.ssid = ssid;
	}

	public boolean isDefaultProfileOnNoWifi() {
		return isDefaultProfileOnNoWifi;
	}

	public void setDefaultProfileOnNoWifi(boolean defaultProfileOnNoWifi) {
		isDefaultProfileOnNoWifi = defaultProfileOnNoWifi;
	}

	public boolean equals(@NonNull Profile p) {
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
				&& getFilePort() == p.getFilePort()
				&& isStreamLogin() == p.isStreamLogin()
				&& isFileSsl() == p.isFileSsl()
				&& isFileLogin() == p.isFileLogin()
				&& isEncoderStream() == p.isEncoderStream()
				&& getEncoderPort() == p.getEncoderPort()
				&& getEncoderPath().equals(p.getEncoderPath())
				&& isEncoderLogin() == p.isEncoderLogin()
				&& getEncoderUser().equals(p.getEncoderUser())
				&& getEncoderPass().equals(p.getEncoderPass())
				&& getEncoderVideoBitrate() == p.getEncoderVideoBitrate()
				&& getEncoderAudioBitrate() == p.getEncoderAudioBitrate();
	}
}
