package net.reichholf.dreamdroid

import android.util.Log
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import java.io.Serializable
import net.reichholf.dreamdroid.enigma.VpsMode

@Entity(tableName = "profile")
class Profile : Serializable {
    @Dao
    interface ProfileDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun addProfile(profile: Profile): Long

        @Update
        suspend fun updateProfile(profiles: Profile)

        @Delete
        suspend fun deleteProfile(profile: Profile)

        @Query("SELECT * FROM profile")
        suspend fun getProfiles(): MutableList<Profile>

        @Query("SELECT * FROM profile WHERE _id=:id")
        suspend fun getProfile(id: Int): Profile?
    }

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Int? = null

    @ColumnInfo(name = "profile")
    var name: String? = null
        set(value) {
            field = value ?: ""
        }

    @ColumnInfo(name = "host")
    var host: String? = null
        set(value) {
            field = stripScheme(value)
        }

    @ColumnInfo(name = "streamhost")
    var streamHost: String? = null
        set(value) {
            field = stripScheme(value)
        }

    /** Stored stream host, or [host] when stream host is empty. */
    val streamHostOrHost: String?
        get() = if (streamHost.isNullOrEmpty()) host else streamHost

    @ColumnInfo(name = "encoder_path")
    var encoderPath: String? = null

    @ColumnInfo(name = "user")
    var user: String? = null
        set(value) {
            field = value ?: ""
        }

    @ColumnInfo(name = "pass")
    var pass: String? = null
        set(value) {
            field = value ?: ""
        }

    @ColumnInfo(name = "encoder_user")
    var encoderUser: String? = null

    @ColumnInfo(name = "encoder_pass")
    var encoderPass: String? = null

    @ColumnInfo(name = "login")
    var login: Boolean = false

    @ColumnInfo(name = "ssl")
    var ssl: Boolean = false

    @ColumnInfo(name = "trust_all_certs")
    var allCertsTrusted: Boolean = false

    @ColumnInfo(name = "streamlogin")
    var streamLogin: Boolean = false

    @ColumnInfo(name = "file_login")
    var fileLogin: Boolean = false

    @ColumnInfo(name = "encoder_login")
    var encoderLogin: Boolean = false

    @ColumnInfo(name = "stream_mode", defaultValue = "'Direct'")
    var streamMode: StreamMode = StreamMode.Direct

    @ColumnInfo(name = "file_ssl")
    var fileSsl: Boolean = false

    /**
     * Request HTTP streams (live, and the transcoder) over https, as a reverse proxy in
     * front of the stream port serves them.
     */
    @ColumnInfo(name = "stream_ssl", defaultValue = "0")
    var streamSsl: Boolean = false

    /**
     * Zap to the service before opening a live stream. Single-tuner boxes can
     * only stream a service that is on the current transponder.
     */
    @ColumnInfo(name = "zap_and_stream", defaultValue = "0")
    var zapAndStream: Boolean = false

    @ColumnInfo(name = "simpleremote")
    var simpleRemote: Boolean = false

    @ColumnInfo(name = "port")
    var port: Int = 0

    @ColumnInfo(name = "streamport")
    var streamPort: Int = 0

    @ColumnInfo(name = "fileport")
    var filePort: Int = 0

    @ColumnInfo(name = "encoder_port")
    var encoderPort: Int = 0

    @ColumnInfo(name = "encoder_audio_bitrate")
    var encoderAudioBitrate: Int = 0

    @ColumnInfo(name = "encoder_video_bitrate")
    var encoderVideoBitrate: Int = 0

    /** Port of the HTTP transcoder for [StreamMode.Transcoding]. */
    @ColumnInfo(name = "transcode_port", defaultValue = "8002")
    var transcodePort: Int = DEFAULT_TRANSCODE_PORT

    @ColumnInfo(name = "default_ref")
    var defaultBouquetTv: String? = null

    @ColumnInfo(name = "default_ref_name")
    var defaultBouquetTvName: String? = null

    @ColumnInfo(name = "default_ref_2")
    var defaultParentBouquetTv: String? = null

    @ColumnInfo(name = "default_ref_2_name")
    var defaultParentBouquetTvName: String? = null

    @Ignore
    var sessionId: String? = null

    @ColumnInfo(name = "ssid")
    var ssid: String? = null

    @ColumnInfo(name = "defaultProfileOnNoWifi")
    var isDefaultProfileOnNoWifi: Boolean = false

    /** VPS choice for new timers when the receiver has the VPS plugin. */
    @ColumnInfo(name = "vps_default", defaultValue = "'Off'")
    var vpsDefault: VpsMode = VpsMode.Off

    /**
     * Load picons for this profile from the receiver over HTTP instead of the shared
     * synced files. [piconsOnlinePath] is the receiver's picon directory and
     * [piconsOnlineUseName] picks the matching key; both apply only while this is on.
     * Synced (offline) picons and the sync itself stay global.
     */
    @ColumnInfo(name = "picons_online", defaultValue = "0")
    var piconsOnline: Boolean = false

    /** The receiver's picon directory for [piconsOnline]. */
    @ColumnInfo(name = "picons_online_path", defaultValue = "'/usr/share/enigma2/picon'")
    var piconsOnlinePath: String = DEFAULT_PICON_PATH

    /**
     * Request online picons by service name instead of reference. Offline (synced) picons
     * and the sync itself stay global; only this online naming is per receiver.
     */
    @ColumnInfo(name = "picons_online_use_name", defaultValue = "0")
    var piconsOnlineUseName: Boolean = false

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
        defaultRef2Name: String?
    ) : this() {
        init(
            id,
            profile,
            host,
            streamHost,
            port,
            streamPort,
            filePort,
            login,
            user,
            pass,
            ssl,
            false,
            streamLogin,
            fileLogin,
            fileSsl,
            simpleRemote,
            defaultRef,
            defaultRefName,
            defaultRef2,
            defaultRef2Name,
            StreamMode.Direct,
            "stream",
            554,
            false,
            "",
            "",
            2500,
            128
        )
    }

    @Ignore
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
        streamMode: StreamMode,
        encoderPath: String?,
        encoderPort: Int,
        encoderLogin: Boolean,
        encoderUser: String?,
        encoderPass: String?,
        encoderVideoBitrate: Int,
        encoderAudioBitrate: Int
    ) : this() {
        init(
            id, name, host, streamHost, port, streamPort, filePort, login, user, pass, ssl,
            allCertsTrusted, streamLogin, fileLogin, fileSsl, simpleRemote, defaultBouquetTv,
            defaultBouquetTvName, defaultParentBouquetTv, defaultParentBouquetTvName, streamMode,
            encoderPath, encoderPort, encoderLogin, encoderUser, encoderPass, encoderVideoBitrate,
            encoderAudioBitrate
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
        streamMode: StreamMode,
        encoderPath: String?,
        encoderPort: Int,
        encoderLogin: Boolean,
        encoderUser: String?,
        encoderPass: String?,
        encoderVideoBitrate: Int,
        encoderAudioBitrate: Int
    ) {
        this.id = id
        sessionId = null
        this.name = name
        this.host = host
        this.streamHost = streamHost
        this.port = port
        this.streamPort = streamPort
        this.filePort = filePort
        this.login = login
        this.streamLogin = streamLogin
        this.fileLogin = fileLogin
        this.fileSsl = fileSsl
        this.user = user
        this.pass = pass
        this.ssl = ssl
        this.allCertsTrusted = allCertsTrusted
        this.simpleRemote = simpleRemote
        setDefaultRefValues(defaultRef, defaultRefName)
        setDefaultRef2Values(defaultRef2, defaultRef2Name)
        this.streamMode = streamMode
        this.encoderPort = encoderPort
        this.encoderPath = encoderPath
        this.encoderAudioBitrate = encoderAudioBitrate
        this.encoderVideoBitrate = encoderVideoBitrate
        this.encoderLogin = encoderLogin
        this.encoderUser = encoderUser
        this.encoderPass = encoderPass
    }

    fun setPort(port: String, ssl: Boolean) {
        this.ssl = ssl
        setPort(port)
    }

    fun setPort(port: String, ssl: Boolean, isAllCertsTrusted: Boolean) {
        this.ssl = ssl
        allCertsTrusted = isAllCertsTrusted
        setPort(port)
    }

    fun setPort(port: String) {
        this.port = parseInt(port) { if (ssl) 443 else 80 }
    }

    fun setStreamPort(streamPort: String) {
        this.streamPort = parseInt(streamPort) { this.streamPort }
    }

    fun setFilePort(filePort: String) {
        this.filePort = parseInt(filePort) { 80 }
    }

    fun setEncoderPort(port: String) {
        encoderPort = parseInt(port) { encoderPort }
    }

    fun setTranscodePort(port: String) {
        transcodePort = parseInt(port) { transcodePort }
    }

    fun setEncoderVideoBitrate(bitrate: String) {
        encoderVideoBitrate = parseInt(bitrate) { encoderVideoBitrate }
    }

    fun setEncoderAudioBitrate(bitrate: String) {
        encoderAudioBitrate = parseInt(bitrate) { encoderAudioBitrate }
    }

    fun setDefaultRefValues(ref: String?, name: String?) {
        defaultBouquetTv = ref
        defaultBouquetTvName = name
    }

    fun setDefaultRef2Values(ref: String?, name: String?) {
        defaultParentBouquetTv = ref
        defaultParentBouquetTvName = name
    }

    fun hasSameSettings(p: Profile): Boolean = host == p.host &&
        streamHostOrHost == p.streamHostOrHost &&
        user == p.user &&
        pass == p.pass &&
        login == p.login &&
        ssl == p.ssl &&
        simpleRemote == p.simpleRemote &&
        id == p.id &&
        port == p.port &&
        streamPort == p.streamPort &&
        filePort == p.filePort &&
        streamLogin == p.streamLogin &&
        streamSsl == p.streamSsl &&
        fileSsl == p.fileSsl &&
        fileLogin == p.fileLogin &&
        zapAndStream == p.zapAndStream &&
        streamMode == p.streamMode &&
        transcodePort == p.transcodePort &&
        encoderPort == p.encoderPort &&
        encoderPath == p.encoderPath &&
        encoderLogin == p.encoderLogin &&
        encoderUser == p.encoderUser &&
        encoderPass == p.encoderPass &&
        encoderVideoBitrate == p.encoderVideoBitrate &&
        encoderAudioBitrate == p.encoderAudioBitrate

    private fun parseInt(value: String, fallback: () -> Int): Int = try {
        value.toInt()
    } catch (e: NumberFormatException) {
        Log.w(DreamDroid.LOG_TAG, e.toString())
        fallback()
    }

    private fun stripScheme(value: String?): String =
        (value ?: "").replace("http://", "").replace("https://", "")

    companion object {
        private const val serialVersionUID: Long = 8176949133234868302L

        const val DEFAULT_TRANSCODE_PORT: Int = 8002

        /** Default receiver picon directory, for online picons and the sync. */
        const val DEFAULT_PICON_PATH: String = "/usr/share/enigma2/picon"

        @Ignore
        fun getDefault(): Profile = Profile(
            null, "", "", "", 443, 8001, 80, false, "root", "dreambox", true, false, false,
            false, false, "", "", "", ""
        )
    }
}
