package net.reichholf.dreamdroid.fragment.dialogs

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.franmontiel.attributionpresenter.AttributionPresenter
import com.franmontiel.attributionpresenter.entities.Attribution
import com.franmontiel.attributionpresenter.entities.Library
import com.franmontiel.attributionpresenter.entities.License
import net.reichholf.dreamdroid.R

object DreamDroidAttributionPresenter {
    @JvmStatic
    fun newInstance(context: Context): AttributionPresenter {
        return AttributionPresenter.Builder(ContextThemeWrapper(context, R.style.Theme_DreamDroid_Dialog))
            .addAttributions(
                Library.GSON,
                Library.PICASSO,
                Library.OK_HTTP,
            )
            .addAttributions(
                Attribution.Builder("AndroidX")
                    .addCopyrightNotice("Copyright (C) The Android Open Source Project")
                    .addLicense(License.APACHE)
                    .setWebsite("https://developer.android.com/jetpack/androidx/")
                    .build(),
                Attribution.Builder("Apache Commons IO")
                    .addCopyrightNotice("Copyright 2002-2017 The Apache Software Foundation")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/apache/commons-io")
                    .build(),
                Attribution.Builder("Apache Commons NET")
                    .addCopyrightNotice("Copyright 2002-2017 The Apache Software Foundation")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/apache/commons-net")
                    .build(),
                Attribution.Builder("AttributionPresenter")
                    .addCopyrightNotice("Copyright 2017 Francisco José Montiel Navarro")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/franmontiel/AttributionPresenter")
                    .build(),
                Attribution.Builder("Material Dialogs")
                    .addCopyrightNotice("Designed and developed by Aidan Follestad (@afollestad)")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/afollestad/material-dialogs")
                    .build(),
                Attribution.Builder("vlc-android-sdk")
                    .addCopyrightNotice("Copyright (C) 2017  VLC authors, Enno Gottschalk, Aldo Borrero")
                    .addLicense(License.GPL_3)
                    .setWebsite("https://github.com/butterproject/vlc-android-sdk")
                    .build(),
                Attribution.Builder("RecyclerView-FastScroll")
                    .addCopyrightNotice("Copyright (C) 2016 Tim Malseed")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/timusus/RecyclerView-FastScroll")
                    .build(),
                Attribution.Builder("MaterialNumberPicker")
                    .addCopyrightNotice("Created by stephenvinouze on 25/09/2017.")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/StephenVinouze/MaterialNumberPicker")
                    .build(),
                Attribution.Builder("PhotoView")
                    .addCopyrightNotice("Copyright 2018 Chris Banes")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/chrisbanes/PhotoView")
                    .build(),
                Attribution.Builder("Material DateTime Picker")
                    .addCopyrightNotice("Copyright (c) 2015 Wouter Dullaert")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/wdullaer/MaterialDateTimePicker")
                    .build(),
                Attribution.Builder("Matomo SDK for Android")
                    .addCopyrightNotice("Copyright 2018 Matomo team")
                    .addLicense(License.BSD_3)
                    .setWebsite("https://github.com/matomo-org/matomo-sdk-android")
                    .build(),
                Attribution.Builder("JmDNS")
                    .addCopyrightNotice("Copyright (C) 2018 JmDNS.org and others")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/jmdns/jmdns")
                    .build(),
                Attribution.Builder("Markwon")
                    .addCopyrightNotice("Copyright 2017 Dimitry Ivanov (mail@dimitryivanov.ru)")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/noties/Markwon")
                    .build(),
                Attribution.Builder("GaugeView")
                    .addCopyrightNotice("Copyright (c) 2012 Evelina Vrabie")
                    .addLicense(License.APACHE)
                    .setWebsite("https://github.com/CodeAndMagic/GaugeView")
                    .build(),
            )
            .build()
    }
}
