package net.reichholf.dreamdroid.ui.setup

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

/** The wizard fields that survive process death. Defaults are a fresh wizard. */
data class SetupDraft(
    val step: SetupStep = SetupStep.Welcome,
    val host: String = "",
    val useHttps: Boolean = false,
    val portText: String = "80",
    val login: Boolean = true,
    val user: String = "root",
    val pass: String = "dreambox",
    val profileName: String = "",
    val nameEdited: Boolean = false,
    val trustAllCerts: Boolean = false,
    val suggestedName: String = "",
    val askedForNetwork: Boolean = false
) {
    fun portNumber(): Int = portText.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: if (useHttps) 443 else 80

    fun toProfile() = wizardProfile(
        name = profileName,
        host = host,
        port = portNumber(),
        useHttps = useHttps,
        login = login,
        user = user,
        pass = pass,
        trustAllCerts = trustAllCerts
    )
}

interface SetupAssistantSavedAccess {
    operator fun get(key: String): Any?
    operator fun set(key: String, value: Any)
}

class MapSetupAssistantSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    SetupAssistantSavedAccess {
    override fun get(key: String): Any? = values[key]

    override fun set(key: String, value: Any) {
        values[key] = value
    }
}

/** Absent or unknown keys read as the fresh-wizard default. Reading does not write. */
fun readSetupDraft(access: SetupAssistantSavedAccess): SetupDraft {
    val defaults = SetupDraft()
    fun string(key: String, default: String) = access[key] as? String ?: default
    fun flag(key: String, default: Boolean) = access[key] as? Boolean ?: default
    val stepName = access[SetupAssistantSavedKeys.STEP] as? String
    return SetupDraft(
        step = SetupStep.entries.firstOrNull { it.name == stepName } ?: defaults.step,
        host = string(SetupAssistantSavedKeys.HOST, defaults.host),
        useHttps = flag(SetupAssistantSavedKeys.USE_HTTPS, defaults.useHttps),
        portText = string(SetupAssistantSavedKeys.PORT_TEXT, defaults.portText),
        login = flag(SetupAssistantSavedKeys.LOGIN, defaults.login),
        user = string(SetupAssistantSavedKeys.USER, defaults.user),
        pass = string(SetupAssistantSavedKeys.PASS, defaults.pass),
        profileName = string(SetupAssistantSavedKeys.PROFILE_NAME, defaults.profileName),
        nameEdited = flag(SetupAssistantSavedKeys.NAME_EDITED, defaults.nameEdited),
        trustAllCerts = flag(SetupAssistantSavedKeys.TRUST_ALL_CERTS, defaults.trustAllCerts),
        suggestedName = string(SetupAssistantSavedKeys.SUGGESTED_NAME, defaults.suggestedName),
        askedForNetwork = flag(SetupAssistantSavedKeys.ASKED_FOR_NETWORK, defaults.askedForNetwork)
    )
}

fun SetupDraft.writeTo(access: SetupAssistantSavedAccess) {
    access[SetupAssistantSavedKeys.STEP] = step.name
    access[SetupAssistantSavedKeys.HOST] = host
    access[SetupAssistantSavedKeys.USE_HTTPS] = useHttps
    access[SetupAssistantSavedKeys.PORT_TEXT] = portText
    access[SetupAssistantSavedKeys.LOGIN] = login
    access[SetupAssistantSavedKeys.USER] = user
    access[SetupAssistantSavedKeys.PASS] = pass
    access[SetupAssistantSavedKeys.PROFILE_NAME] = profileName
    access[SetupAssistantSavedKeys.NAME_EDITED] = nameEdited
    access[SetupAssistantSavedKeys.TRUST_ALL_CERTS] = trustAllCerts
    access[SetupAssistantSavedKeys.SUGGESTED_NAME] = suggestedName
    access[SetupAssistantSavedKeys.ASKED_FOR_NETWORK] = askedForNetwork
}
