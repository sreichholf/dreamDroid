package net.reichholf.dreamdroid.fragment.dialogs;

import android.content.Context;

import com.franmontiel.attributionpresenter.AttributionPresenter;
import com.franmontiel.attributionpresenter.entities.Attribution;
import com.franmontiel.attributionpresenter.entities.Library;
import com.franmontiel.attributionpresenter.entities.License;

public class DreamDroidAttributionPresenter {
	public static AttributionPresenter newInstance(Context context) {
		return new AttributionPresenter.Builder(context)
				.addAttributions(
					Library.GSON,
					Library.PICASSO,
					Library.OK_HTTP
				)
				.addAttributions(
					new Attribution.Builder("AndroidX")
						.addCopyrightNotice("Copyright (C) The Android Open Source Project")
						.addLicense(License.APACHE)
						.setWebsite("https://developer.android.com/jetpack/androidx/")
						.build(),
					new Attribution.Builder("Apache Commons IO")
						.addCopyrightNotice("Copyright 2002-2017 The Apache Software Foundation")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/apache/commons-io")
						.build(),
					new Attribution.Builder("Apache Commons NET")
						.addCopyrightNotice("Copyright 2002-2017 The Apache Software Foundation")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/apache/commons-net")
						.build(),
					new Attribution.Builder("AttributionPresenter")
						.addCopyrightNotice("Copyright 2017 Francisco José Montiel Navarro")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/franmontiel/AttributionPresenter")
						.build(),
					new Attribution.Builder("Material Dialogs")
						.addCopyrightNotice("Designed and developed by Aidan Follestad (@afollestad)")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/afollestad/material-dialogs")
						.build(),
					new Attribution.Builder("vlc-android-sdk")
						.addCopyrightNotice("Copyright (C) 2017  VLC authors, Enno Gottschalk, Aldo Borrero")
						.addLicense(License.GPL_3)
						.setWebsite("https://github.com/butterproject/vlc-android-sdk")
						.build(),
					new Attribution.Builder("RecyclerView-FastScroll")
						.addCopyrightNotice("Copyright (C) 2016 Tim Malseed")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/timusus/RecyclerView-FastScroll")
						.build(),
					new Attribution.Builder("MaterialNumberPicker")
						.addCopyrightNotice("Created by stephenvinouze on 25/09/2017.")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/StephenVinouze/MaterialNumberPicker")
						.build(),
					new Attribution.Builder("PhotoView")
						.addCopyrightNotice("Copyright 2018 Chris Banes")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/chrisbanes/PhotoView")
						.build(),
					new Attribution.Builder("Material DateTime Picker")
						.addCopyrightNotice("Copyright (c) 2015 Wouter Dullaert")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/wdullaer/MaterialDateTimePicker")
						.build(),
					new Attribution.Builder("Matomo SDK for Android")
						.addCopyrightNotice("Copyright 2018 Matomo team")
						.addLicense(License.BSD_3)
						.setWebsite("https://github.com/matomo-org/matomo-sdk-android")
						.build(),
					new Attribution.Builder("JmDNS")
						.addCopyrightNotice("Copyright (C) 2018 JmDNS.org and others")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/jmdns/jmdns")
						.build(),
					new Attribution.Builder("Markwon")
						.addCopyrightNotice("Copyright 2017 Dimitry Ivanov (mail@dimitryivanov.ru)")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/noties/Markwon")
						.build(),
					new Attribution.Builder("GaugeView")
						.addCopyrightNotice("Copyright (c) 2012 Evelina Vrabie")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/CodeAndMagic/GaugeView")
						.build(),
					new Attribution.Builder("MemorizingTrustManager")
						.addCopyrightNotice("Copyright (c) 2010 Georg Lukas <georg@op-co.de>")
						.addLicense(License.MIT)
						.setWebsite("https://github.com/ge0rg/MemorizingTrustManager")
						.build(),
					new Attribution.Builder("android-retrostreams")
						.addCopyrightNotice("Copyright (c) 2018 Stefan Zobel <spliterator@gmail.com>")
						.addLicense(License.GPL_2)
						.setWebsite("https://github.com/ge0rg/MemorizingTrustManager")
						.build(),
					new Attribution.Builder("Android-State")
						.addCopyrightNotice("Copyright (c) 2017 Evernote Corporation.")
						.addLicense("Eclipse Public License - v 1.0","https://www.eclipse.org/legal/epl-v10.html")
						.setWebsite("https://github.com/ge0rg/MemorizingTrustManager")
						.build(),
					new Attribution.Builder("Bridge")
						.addCopyrightNotice("Copyright 2017 Livefront")
						.addLicense(License.APACHE)
						.setWebsite("https://github.com/livefront/bridge")
						.build()
				).build();
	}
}
