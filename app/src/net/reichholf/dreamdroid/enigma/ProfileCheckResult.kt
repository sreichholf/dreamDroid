package net.reichholf.dreamdroid.enigma

data class ProfileCheckEntry(
    val hasError: Boolean,
    val what: Int,
    val value: String,
    val errorTextId: Int,
    val errorTextExt: String?
)

data class ProfileCheckResult(
    val hasError: Boolean = false,
    val isSoftError: Boolean = false,
    val errorTextId: Int = -1,
    val errorTextExt: String = "",
    val entries: List<ProfileCheckEntry> = emptyList(),
    val failure: EnigmaFailure? = null
)
