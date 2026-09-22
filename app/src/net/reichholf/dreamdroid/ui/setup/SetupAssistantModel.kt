package net.reichholf.dreamdroid.ui.setup

import android.content.Context
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult

enum class SetupStep {
    Welcome,
    Find,
    Connection,
    SignIn,
    Name
}

data class SetupReceiver(val name: String, val host: String, val port: Int)

fun Profile.toSetupReceiver(): SetupReceiver = SetupReceiver(
    name = name.orEmpty().ifBlank { host.orEmpty() },
    host = host.orEmpty(),
    port = if (port > 0) port else 80
)

fun ProfileCheckResult.isCertificateFailure(): Boolean {
    val unreachable = failure as? EnigmaFailure.Unreachable ?: return false
    return unreachable.reason == EnigmaFailure.UnreachableReason.Ssl
}

fun ProfileCheckResult.setupMessage(context: Context): String {
    if (errorTextExt.isNotBlank()) {
        return errorTextExt
    }
    val fromFailure = failure?.userMessage(context).orEmpty()
    if (fromFailure.isNotBlank()) {
        return fromFailure
    }
    if (errorTextId > 0) {
        return context.getString(errorTextId)
    }
    return ""
}

fun wizardProfile(
    name: String,
    host: String,
    port: Int,
    useHttps: Boolean,
    login: Boolean,
    user: String,
    pass: String,
    trustAllCerts: Boolean
): Profile {
    val profile = Profile(
        null,
        name.ifBlank { host.trim() },
        host.trim(),
        "",
        port,
        8001,
        80,
        login,
        user,
        pass,
        useHttps,
        false,
        false,
        false,
        false,
        "",
        "",
        "",
        ""
    )
    profile.allCertsTrusted = useHttps && trustAllCerts
    return profile
}
