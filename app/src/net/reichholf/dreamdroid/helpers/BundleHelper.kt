package net.reichholf.dreamdroid.helpers

import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import java.io.Serializable

object BundleHelper {
    fun toStringArrayList(strings: Array<CharSequence>): ArrayList<String> {
        val list = ArrayList<String>(strings.size)
        for (string in strings) {
            list.add(string.toString())
        }
        return list
    }

    fun toCharSequenceArray(strings: ArrayList<String>): Array<CharSequence> =
        Array(strings.size) { i ->
            strings[i]
        }
}

inline fun <reified T : Serializable> Bundle.getSerializableCompat(key: String): T? =
    BundleCompat.getSerializable(this, key, T::class.java)

inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String): T? =
    IntentCompat.getSerializableExtra(this, key, T::class.java)
