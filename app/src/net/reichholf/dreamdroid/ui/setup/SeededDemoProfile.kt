package net.reichholf.dreamdroid.ui.setup

import net.reichholf.dreamdroid.Profile

/**
 * The offline dreamdroid.org row inserted for an empty profile table.
 * Every stored field has to match. The row id is not part of the identity.
 */
fun Profile.matchesSeededDemo(): Boolean = name == SEEDED_DEMO_NAME &&
    host == SEEDED_DEMO_HOST &&
    streamHost.isNullOrEmpty() &&
    port == 443 &&
    streamPort == 8001 &&
    filePort == 80 &&
    !login &&
    user == "root" &&
    pass == "dreambox" &&
    ssl &&
    !allCertsTrusted &&
    !streamLogin &&
    !fileLogin &&
    !fileSsl &&
    !simpleRemote &&
    defaultBouquetTv.isNullOrEmpty() &&
    defaultBouquetTvName.isNullOrEmpty() &&
    defaultParentBouquetTv.isNullOrEmpty() &&
    defaultParentBouquetTvName.isNullOrEmpty() &&
    !encoderStream &&
    encoderPath == "stream" &&
    encoderPort == 554 &&
    !encoderLogin &&
    encoderUser.isNullOrEmpty() &&
    encoderPass.isNullOrEmpty() &&
    encoderVideoBitrate == 2500 &&
    encoderAudioBitrate == 128 &&
    !zapAndStream &&
    ssid.isNullOrEmpty() &&
    !isDefaultProfileOnNoWifi

/** The seeded demo when it is the only saved profile. Any other row stays. */
fun soleSeededDemo(profiles: List<Profile>): Profile? {
    val only = profiles.singleOrNull() ?: return null
    return if (only.matchesSeededDemo()) only else null
}

private const val SEEDED_DEMO_NAME: String = "Demo"

private const val SEEDED_DEMO_HOST: String = "dreamdroid.org"
