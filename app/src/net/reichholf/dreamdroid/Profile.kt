package net.reichholf.dreamdroid

import android.util.Log
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

    @ColumnInfo(name = "encoder_stream")
    var encoderStream: Boolean = false

    @ColumnInfo(name = "file_ssl")
    var fileSsl: Boolean = false

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

    @Ignore
    var cachedDeviceInfo: String? = null

    @ColumnInfo(name = "ssid")
    var ssid: String? = null

    @ColumnInfo(name = "defaultProfileOnNoWifi")
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
            false,
            "stream",
            554,
            false,
            "",
            "",
            2500,
            128,
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
            id, name, host, streamHost, port, streamPort, filePort, login, user, pass, ssl,
            allCertsTrusted, streamLogin, fileLogin, fileSsl, simpleRemote, defaultBouquetTv,
            defaultBouquetTvName, defaultParentBouquetTv, defaultParentBouquetTvName, encoderStream,
            encoderPath, encoderPort, encoderLogin, encoderUser, encoderPass, encoderVideoBitrate,
            encoderAudioBitrate,
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
        sessionId = null
        cachedDeviceInfo = null
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
        this.encoderStream = encoderStream
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

    fun hasSameSettings(p: Profile): Boolean {
        return host == p.host &&
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
            fileSsl == p.fileSsl &&
            fileLogin == p.fileLogin &&
            encoderStream == p.encoderStream &&
            encoderPort == p.encoderPort &&
            encoderPath == p.encoderPath &&
            encoderLogin == p.encoderLogin &&
            encoderUser == p.encoderUser &&
            encoderPass == p.encoderPass &&
            encoderVideoBitrate == p.encoderVideoBitrate &&
            encoderAudioBitrate == p.encoderAudioBitrate
    }

    private fun parseInt(value: String, fallback: () -> Int): Int {
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            Log.w(DreamDroid.LOG_TAG, e.toString())
            fallback()
        }
    }

    private fun stripScheme(value: String?): String {
        return (value ?: "").replace("http://", "").replace("https://", "")
    }

    companion object {
        private const val serialVersionUID: Long = 8176949133234868302L

        @Ignore
        fun getDefault(): Profile =
            Profile(
                null, "", "", "", 443, 8001, 80, false, "root", "dreambox", true, false, false,
                false, false, "", "", "", "",
            )
    }
}
