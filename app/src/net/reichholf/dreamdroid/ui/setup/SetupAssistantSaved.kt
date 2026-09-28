package net.reichholf.dreamdroid.ui.setup

import androidx.lifecycle.SavedStateHandle

object SetupAssistantSavedKeys {
    const val STEP = "setup_step"
    const val HOST = "setup_host"
    const val USE_HTTPS = "setup_use_https"
    const val PORT_TEXT = "setup_port_text"
    const val LOGIN = "setup_login"
    const val USER = "setup_user"
    const val PASS = "setup_pass"
    const val PROFILE_NAME = "setup_profile_name"
    const val NAME_EDITED = "setup_name_edited"
    const val TRUST_ALL_CERTS = "setup_trust_all_certs"
    const val SUGGESTED_NAME = "setup_suggested_name"
    const val ASKED_FOR_NETWORK = "setup_asked_for_network"
}

/**
 * The wizard's choices that survive process death. Defaults are a fresh wizard. The
 * typed fields (host, port, user, password, name) are `SavedTextField`s on the
 * ViewModel, saved under the matching [SetupAssistantSavedKeys].
 */
data class SetupDraft(
    val step: SetupStep = SetupStep.Welcome,
    val useHttps: Boolean = false,
    val login: Boolean = true,
    val nameEdited: Boolean = false,
    val trustAllCerts: Boolean = false,
    val suggestedName: String = "",
    val askedForNetwork: Boolean = false
)

/** Defaults of the typed fields in a fresh wizard. */
object SetupDefaults {
    const val PORT_TEXT = "80"
    const val USER = "root"
    const val PASS = "dreambox"
}

/** [portText] as a port, or the default for [useHttps] when it is not one. */
fun setupPort(portText: String, useHttps: Boolean): Int =
    portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: if (useHttps) 443 else 80

/** Absent or unknown keys read as the fresh-wizard default. Reading does not write. */
fun readSetupDraft(handle: SavedStateHandle): SetupDraft {
    val defaults = SetupDraft()
    fun string(key: String, default: String) = handle.get<Any>(key) as? String ?: default
    fun flag(key: String, default: Boolean) = handle.get<Any>(key) as? Boolean ?: default
    val stepName = handle.get<Any>(SetupAssistantSavedKeys.STEP) as? String
    return SetupDraft(
        step = SetupStep.entries.firstOrNull { it.name == stepName } ?: defaults.step,
        useHttps = flag(SetupAssistantSavedKeys.USE_HTTPS, defaults.useHttps),
        login = flag(SetupAssistantSavedKeys.LOGIN, defaults.login),
        nameEdited = flag(SetupAssistantSavedKeys.NAME_EDITED, defaults.nameEdited),
        trustAllCerts = flag(SetupAssistantSavedKeys.TRUST_ALL_CERTS, defaults.trustAllCerts),
        suggestedName = string(SetupAssistantSavedKeys.SUGGESTED_NAME, defaults.suggestedName),
        askedForNetwork = flag(SetupAssistantSavedKeys.ASKED_FOR_NETWORK, defaults.askedForNetwork)
    )
}

fun SetupDraft.writeTo(handle: SavedStateHandle) {
    handle[SetupAssistantSavedKeys.STEP] = step.name
    handle[SetupAssistantSavedKeys.USE_HTTPS] = useHttps
    handle[SetupAssistantSavedKeys.LOGIN] = login
    handle[SetupAssistantSavedKeys.NAME_EDITED] = nameEdited
    handle[SetupAssistantSavedKeys.TRUST_ALL_CERTS] = trustAllCerts
    handle[SetupAssistantSavedKeys.SUGGESTED_NAME] = suggestedName
    handle[SetupAssistantSavedKeys.ASKED_FOR_NETWORK] = askedForNetwork
}
