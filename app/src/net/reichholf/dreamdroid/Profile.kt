package net.reichholf.dreamdroid

import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.io.Serializable
import java.util.Objects

@Entity(tableName = "profile")
class Profile : Serializable {
    @Dao
    interface ProfileDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addProfile(profile: Profile): Long

        @Update
        fun updateProfile(profiles: Profile)

        @Delete
        fun deleteProfile(profile: Profile)

        @Query("SELECT * FROM profile")
        fun getProfiles(): MutableList<Profile>

        @Query("SELECT * FROM profile WHERE _id=:id")
        fun getProfile(id: Int): Profile
    }

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    @JvmField
    var id: Int? = null

    @ColumnInfo(name = "profile")
    @JvmField
    var name: String? = null

    @ColumnInfo(name = "host")
    @JvmField
    var host: String? = null

    @ColumnInfo(name = "streamhost")
    @JvmField
    var streamHost: String? = null

    @ColumnInfo(name = "encoder_path")
    @JvmField
    var encoderPath: String? = null

    @ColumnInfo(name = "user")
    @JvmField
    var user: String? = null

    @ColumnInfo(name = "pass")
    @JvmField
    var pass: String? = null

    @ColumnInfo(name = "encoder_user")
    @JvmField
    var encoderUser: String? = null

    @ColumnInfo(name = "encoder_pass")
    @JvmField
    var encoderPass: String? = null

    @ColumnInfo(name = "login")
    @JvmField
    var login: Boolean = false

    @ColumnInfo(name = "ssl")
    @JvmField
    var ssl: Boolean = false

    @ColumnInfo(name = "trust_all_certs")
    @JvmField
    var allCertsTrusted: Boolean = false

    @ColumnInfo(name = "streamlogin")
    @JvmField
    var streamLogin: Boolean = false

    @ColumnInfo(name = "file_login")
    @JvmField
    var fileLogin: Boolean = false

    @ColumnInfo(name = "encoder_login")
    @JvmField
    var encoderLogin: Boolean = false

    @ColumnInfo(name = "encoder_stream")
    @JvmField
    var encoderStream: Boolean = false

    @ColumnInfo(name = "file_ssl")
    @JvmField
    var fileSsl: Boolean = false

    @ColumnInfo(name = "simpleremote")
    @JvmField
    var simpleRemote: Boolean = false

    @ColumnInfo(name = "port")
    @JvmField
    var port: Int = 0

    @ColumnInfo(name = "streamport")
    @JvmField
    var streamPort: Int = 0

    @ColumnInfo(name = "fileport")
    @JvmField
    var filePort: Int = 0

    @ColumnInfo(name = "encoder_port")
    @JvmField
    var encoderPort: Int = 0

    @ColumnInfo(name = "encoder_audio_bitrate")
    @JvmField
    var encoderAudioBitrate: Int = 0

    @ColumnInfo(name = "encoder_video_bitrate")
    @JvmField
    var encoderVideoBitrate: Int = 0

    @ColumnInfo(name = "default_ref")
    @JvmField
    var defaultBouquetTv: String? = null

    @ColumnInfo(name = "default_ref_name")
    @JvmField
    var defaultBouquetTvName: String? = null

    @ColumnInfo(name = "default_ref_2")
    @JvmField
    var defaultParentBouquetTv: String? = null

    @ColumnInfo(name = "default_ref_2_name")
    @JvmField
    var defaultParentBouquetTvName: String? = null

    @Ignore
    @JvmField
    var sessionid: String? = null

    @Ignore
    @JvmField
    var cachedDeviceInfo: String? = null

    @ColumnInfo(name = "ssid")
    @JvmField
    var ssid: String? = null

    @ColumnInfo(name = "defaultProfileOnNoWifi")
    @JvmField
    var isDefaultProfileOnNoWifi: Boolean = false

    constructor()

    @Ignore
    constructor(
        id: Int?,
        profile: String?,
        host: String?,
        streamHost: String?,
        port: Int,
        streamPort: Int,
        filePort: Int,
        login: Boolean,
        user: String?,
        pass: String?,
        ssl: Boolean,
        streamLogin: Boolean,
        fileLogin: Boolean,
        fileSsl: Boolean,
        simpleRemote: Boolean,
        defaultRef: String?,
        defaultRefName: String?,
        defaultRef2: String?,
        defaultRef2Name: String?,
    ) : this() {
        init(
            id, profile, host, streamHost, port, streamPort, filePort, login, user, pass, ssl, false,
            streamLogin, fileLogin, fileSsl, simpleRemote, defaultRef, defaultRefName, defaultRef2, defaultRef2Name,
            false, "stream", 554, false, "", "", 2500, 128,
        )
    }

    constructor(
        id: Int?,
        name: String?,
        host: String?,
        streamHost: String?,
        port: Int,
        streamPort: Int,
        filePort: Int,
        login: Boolean,
        user: String?,
        pass: String?,
        ssl: Boolean,
        allCertsTrusted: Boolean,
        streamLogin: Boolean,
        fileLogin: Boolean,
        fileSsl: Boolean,
        simpleRemote: Boolean,
        defaultBouquetTv: String?,
        defaultBouquetTvName: String?,
        defaultParentBouquetTv: String?,
        defaultParentBouquetTvName: String?,
        encoderStream: Boolean,
        encoderPath: String?,
        encoderPort: Int,
        encoderLogin: Boolean,
        encoderUser: String?,
        encoderPass: String?,
        encoderVideoBitrate: Int,
        encoderAudioBitrate: Int,
    ) : this() {
        init(
            id, name, host, streamHost, port, streamPort, filePort, login, user, pass, ssl, allCertsTrusted,
            streamLogin, fileLogin, fileSsl, simpleRemote, defaultBouquetTv, defaultBouquetTvName,
            defaultParentBouquetTv, defaultParentBouquetTvName, encoderStream, encoderPath, encoderPort,
            encoderLogin, encoderUser, encoderPass, encoderVideoBitrate, encoderAudioBitrate,
        )
    }

    private fun init(
        id: Int?,
        name: String?,
        host: String?,
        streamHost: String?,
        port: Int,
        streamPort: Int,
        filePort: Int,
        login: Boolean,
        user: String?,
        pass: String?,
        ssl: Boolean,
        allCertsTrusted: Boolean,
        streamLogin: Boolean,
        fileLogin: Boolean,
        fileSsl: Boolean,
        simpleRemote: Boolean,
        defaultRef: String?,
        defaultRefName: String?,
        defaultRef2: String?,
        defaultRef2Name: String?,
        encoderStream: Boolean,
        encoderPath: String?,
        encoderPort: Int,
        encoderLogin: Boolean,
        encoderUser: String?,
        encoderPass: String?,
        encoderVideoBitrate: Int,
        encoderAudioBitrate: Int,
    ) {
        this.id = id
        sessionid = null
        cachedDeviceInfo = null
        setName(name)
        setHost(host)
        setStreamHost(streamHost)
        setPort(port)
        setStreamPort(streamPort)
        setFilePort(filePort)
        setLogin(login)
        setStreamLogin(streamLogin)
        setFileLogin(fileLogin)
        setFileSsl(fileSsl)
        setUser(user)
        setPass(pass)
        setSsl(ssl)
        setAllCertsTrusted(allCertsTrusted)
        setSimpleRemote(simpleRemote)
        setDefaultRefValues(defaultRef, defaultRefName)
        setDefaultRef2Values(defaultRef2, defaultRef2Name)
        setEncoderStream(encoderStream)
        setEncoderPort(encoderPort)
        setEncoderPath(encoderPath)
        setEncoderAudioBitrate(encoderAudioBitrate)
        setEncoderVideoBitrate(encoderVideoBitrate)
        setEncoderLogin(encoderLogin)
        setEncoderUser(encoderUser)
        setEncoderPass(encoderPass)
    }

    fun setId(id: Int) { this.id = id }
    fun setId(id: Long) { this.id = id.toInt() }
    fun setPort(port: Int) { this.port = port }
    fun setPort(port: String, ssl: Boolean) { setSsl(ssl); setPort(port) }
    fun setPort(port: String, ssl: Boolean, isAllCertsTrusted: Boolean) {
        setSsl(ssl); setAllCertsTrusted(isAllCertsTrusted); setPort(port)
    }
    fun setStreamPort(streamPort: Int) { this.streamPort = streamPort }
    fun setFilePort(filePort: Int) { this.filePort = filePort }
    fun getName(): String? = name
    fun setName(name: String?) { this.name = name ?: "" }
    fun getHost(): String? = host
    fun setHost(host: String?) {
        this.host = (host ?: "").replace("http://", "").replace("https://", "")
    }
    fun getStreamHost(): String? =
        if (streamHost.isNullOrEmpty()) host else streamHost
    fun setStreamHost(streamHost: String?) {
        this.streamHost = (streamHost ?: "").replace("http://", "").replace("https://", "")
    }
    fun getStreamHostValue(): String? = streamHost
    fun setEncoderPath(encoderPath: String?) { this.encoderPath = encoderPath }
    fun getEncoderPath(): String? = encoderPath
    fun getUser(): String? = user
    fun setUser(user: String?) { this.user = user ?: "" }
    fun getPass(): String? = pass
    fun setPass(pass: String?) { this.pass = pass ?: "" }
    fun isLogin(): Boolean = login
    fun setLogin(login: Boolean) { this.login = login }
    fun getEncoderUser(): String? = encoderUser
    fun setEncoderUser(user: String?) { encoderUser = user }
    fun getEncoderPass(): String? = encoderPass
    fun setEncoderPass(pass: String?) { encoderPass = pass }
    fun isEncoderLogin(): Boolean = encoderLogin
    fun setEncoderLogin(isLogin: Boolean) { encoderLogin = isLogin }
    fun isSsl(): Boolean = ssl
    fun setSsl(ssl: Boolean) { this.ssl = ssl }
    fun isAllCertsTrusted(): Boolean = allCertsTrusted
    fun setAllCertsTrusted(allCertsTrusted: Boolean) { this.allCertsTrusted = allCertsTrusted }
    fun isFileLogin(): Boolean = fileLogin
    fun setFileLogin(login: Boolean) { fileLogin = login }
    fun isFileSsl(): Boolean = fileSsl
    fun setFileSsl(ssl: Boolean) { fileSsl = ssl }
    fun isSimpleRemote(): Boolean = simpleRemote
    fun setSimpleRemote(simpleRemote: Boolean) { this.simpleRemote = simpleRemote }
    fun getId(): Int = id ?: -1
    fun getPort(): Int = port
    fun setPort(port: String) {
        try { this.port = Integer.valueOf(port) }
        catch (e: NumberFormatException) {
            Log.w(DreamDroid.LOG_TAG, e.toString())
            this.port = if (ssl) 443 else 80
        }
    }
    fun getPortString(): String = port.toString()
    fun getStreamPort(): Int = streamPort
    fun setStreamPort(streamPort: String) {
        try { this.streamPort = Integer.valueOf(streamPort) }
        catch (e: NumberFormatException) { Log.w(DreamDroid.LOG_TAG, e.toString()) }
    }
    fun isEncoderStream(): Boolean = encoderStream
    fun setEncoderStream(encoderStream: Boolean) { this.encoderStream = encoderStream }
    fun getStreamPortString(): String = streamPort.toString()
    fun getEncoderPort(): Int = encoderPort
    fun getEncoderPortString(): String = encoderPort.toString()
    fun setEncoderPort(port: Int) { encoderPort = port }
    fun setEncoderPort(port: String) {
        try { encoderPort = Integer.valueOf(port) }
        catch (e: NumberFormatException) { Log.w(DreamDroid.LOG_TAG, e.toString()) }
    }
    fun getEncoderVideoBitrate(): Int = encoderVideoBitrate
    fun getEncoderVideoBitrateString(): String = encoderVideoBitrate.toString()
    fun setEncoderVideoBitrate(bitrate: Int) { encoderVideoBitrate = bitrate }
    fun setEncoderVideoBitrate(bitrate: String) {
        try { encoderVideoBitrate = Integer.valueOf(bitrate) }
        catch (e: NumberFormatException) { Log.w(DreamDroid.LOG_TAG, e.toString()) }
    }
    fun getEncoderAudioBitrate(): Int = encoderAudioBitrate
    fun getEncoderAudioBitrateString(): String = encoderAudioBitrate.toString()
    fun setEncoderAudioBitrate(bitrate: Int) { encoderAudioBitrate = bitrate }
    fun setEncoderAudioBitrate(bitrate: String) {
        try { encoderAudioBitrate = Integer.valueOf(bitrate) }
        catch (e: NumberFormatException) { Log.w(DreamDroid.LOG_TAG, e.toString()) }
    }
    fun getFilePort(): Int = filePort
    fun setFilePort(filePort: String) {
        try { this.filePort = Integer.valueOf(filePort) }
        catch (e: NumberFormatException) {
            Log.w(DreamDroid.LOG_TAG, e.toString())
            this.filePort = 80
        }
    }
    fun getFilePortString(): String = filePort.toString()
    fun setDefaultRefValues(ref: String?, name: String?) {
        setDefaultBouquetTv(ref); setDefaultBouquetTvName(name)
    }
    fun setDefaultRef2Values(ref: String?, name: String?) {
        setParentBouquetTv(ref); setParentBouquetTvName(name)
    }
    fun getDefaultBouquetTv(): String? = defaultBouquetTv
    fun setDefaultBouquetTv(defaultBouquetTv: String?) { this.defaultBouquetTv = defaultBouquetTv }
    fun getDefaultBouquetTvName(): String? = defaultBouquetTvName
    fun setDefaultBouquetTvName(defaultRefName: String?) { defaultBouquetTvName = defaultRefName }
    fun getParentBouquetTv(): String? = defaultParentBouquetTv
    fun setParentBouquetTv(defaultRef2: String?) { defaultParentBouquetTv = defaultRef2 }
    fun getParentBouquetTvName(): String? = defaultParentBouquetTvName
    fun setParentBouquetTvName(defaultRef2Name: String?) { defaultParentBouquetTvName = defaultRef2Name }
    fun isStreamLogin(): Boolean = streamLogin
    fun setStreamLogin(streamLogin: Boolean) { this.streamLogin = streamLogin }
    fun setSessionId(sessionId: String?) { sessionid = sessionId }
    fun getSessionId(): String? = sessionid
    fun setCachedDeviceInfo(deviceInfo: String?) { cachedDeviceInfo = deviceInfo }
    fun getCachedDeviceInfo(): String? = cachedDeviceInfo
    fun getSsid(): String? = ssid
    fun setSsid(ssid: String?) { this.ssid = ssid }

    fun isDefaultProfileOnNoWifi(): Boolean = isDefaultProfileOnNoWifi
    fun setDefaultProfileOnNoWifi(defaultProfileOnNoWifi: Boolean) {
        isDefaultProfileOnNoWifi = defaultProfileOnNoWifi
    }

    fun equals(p: Profile): Boolean {
        return getHost() == p.getHost() &&
            getStreamHost() == p.getStreamHost() &&
            getUser() == p.getUser() &&
            getPass() == p.getPass() &&
            isLogin() == p.isLogin() &&
            isSsl() == p.isSsl() &&
            isSimpleRemote() == p.isSimpleRemote() &&
            Objects.equals(getId(), p.getId()) &&
            getPort() == p.getPort() &&
            getStreamPort() == p.getStreamPort() &&
            getFilePort() == p.getFilePort() &&
            isStreamLogin() == p.isStreamLogin() &&
            isFileSsl() == p.isFileSsl() &&
            isFileLogin() == p.isFileLogin() &&
            isEncoderStream() == p.isEncoderStream() &&
            getEncoderPort() == p.getEncoderPort() &&
            getEncoderPath() == p.getEncoderPath() &&
            isEncoderLogin() == p.isEncoderLogin() &&
            getEncoderUser() == p.getEncoderUser() &&
            getEncoderPass() == p.getEncoderPass() &&
            getEncoderVideoBitrate() == p.getEncoderVideoBitrate() &&
            getEncoderAudioBitrate() == p.getEncoderAudioBitrate()
    }

    companion object {
        private const val serialVersionUID: Long = 8176949133234868302L

        @JvmStatic
        @Ignore
        fun getDefault(): Profile =
            Profile(null, "", "", "", 443, 8001, 80, false, "root", "dreambox", true, false, false, false, false, "", "", "", "")
    }
}
