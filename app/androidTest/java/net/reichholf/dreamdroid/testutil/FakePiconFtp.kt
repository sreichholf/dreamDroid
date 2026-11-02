package net.reichholf.dreamdroid.testutil

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.di.PiconFtpModule
import net.reichholf.dreamdroid.helpers.FtpReply
import net.reichholf.dreamdroid.helpers.PiconFtp

/**
 * Stands in for the receiver's FTP server in instrumented tests: it answers from memory and
 * records what the sync asked, so no socket is opened. A singleton, so a test configures the
 * same instance the worker receives.
 */
@Singleton
class FakePiconFtp @Inject constructor() : PiconFtp {
    var host: String? = null
        private set
    var credentials: Pair<String?, String?>? = null
        private set
    var directory: String? = null
        private set
    val retrieved = mutableListOf<String>()

    var picons: List<String> = emptyList()
    var contents: Map<String, ByteArray> = emptyMap()
    var failRetrieveFor: Set<String> = emptySet()
    var loginReply = FtpReply(ok = true, code = 230, text = "User logged in")
    var directoryReply = FtpReply(ok = true, code = 250, text = "Directory changed")
    var retrieveReply = FtpReply(ok = true, code = 226, text = "Transfer complete")

    override fun connect(host: String) {
        this.host = host
    }

    override fun login(user: String?, pass: String?): FtpReply {
        credentials = user to pass
        return loginReply
    }

    override fun changeDirectory(path: String): FtpReply {
        directory = path
        return directoryReply
    }

    override fun listPicons(): List<String> = picons

    override fun retrieve(name: String, to: OutputStream): FtpReply {
        retrieved += name
        if (name in failRetrieveFor) {
            return FtpReply(ok = false, code = 550, text = "No such file")
        }
        if (retrieveReply.ok) {
            contents[name]?.let { to.write(it) }
        }
        return retrieveReply
    }

    override fun disconnect() = Unit
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PiconFtpModule::class])
abstract class FakePiconFtpModule {
    @Binds
    abstract fun piconFtp(fake: FakePiconFtp): PiconFtp
}
