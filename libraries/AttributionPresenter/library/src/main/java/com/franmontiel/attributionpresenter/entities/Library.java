/*
 * Copyright (c)  2017  Francisco José Montiel Navarro.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.franmontiel.attributionpresenter.entities;

/**
 * An enumeration of common Android libraries with their Attribution information.
 */
public enum Library {

    // Presentation
    BUTTER_KNIFE(new Attribution.Builder("Butter Knife")
            .addCopyrightNotice("Copyright 2013 Jake Wharton")
            .addLicense(License.APACHE)
            .setWebsite("http://jakewharton.github.io/butterknife/")
            .build()),
    PICASSO(new Attribution.Builder("Picasso")
            .addCopyrightNotice("Copyright 2013 Square, Inc.")
            .addLicense(License.APACHE)
            .setWebsite("http://square.github.io/picasso/")
            .build()),
    GLIDE(new Attribution.Builder("Glide")
            .addCopyrightNotice("Copyright 2014 Google, Inc. All rights reserved.")
            .addLicense(License.BSD_3)
            .addLicense(License.MIT)
            .addLicense(License.APACHE)
            .setWebsite("https://github.com/bumptech/glide")
            .build()),

    // Architecture
    DAGGER(new Attribution.Builder("Dagger")
            .addCopyrightNotice("Copyright 2013 Square, Inc.")
            .addLicense(License.APACHE)
            .setWebsite("http://square.github.io/dagger/")
            .build()),
    DAGGER_2(new Attribution.Builder("Dagger 2")
            .addCopyrightNotice("Copyright 2012 The Dagger Authors")
            .addLicense(License.APACHE)
            .setWebsite("https://google.github.io/dagger/")
            .build()),
    EVENT_BUS(new Attribution.Builder("EventBus")
            .addCopyrightNotice("Copyright (C) 2012-2016 Markus Junginger, greenrobot")
            .addLicense(License.APACHE)
            .setWebsite("http://greenrobot.org/eventbus/")
            .build()),
    RX_JAVA(new Attribution.Builder("RxJava")
            .addCopyrightNotice("Copyright (c) 2016-present, RxJava Contributors")
            .addLicense(License.APACHE)
            .setWebsite("https://github.com/ReactiveX/RxJava")
            .build()),
    RX_ANDROID(new Attribution.Builder("RxAndroid")
            .addCopyrightNotice("Copyright 2015 The RxAndroid authors")
            .addLicense(License.APACHE)
            .setWebsite("https://github.com/ReactiveX/RxAndroid")
            .build()),

    // Networking
    OK_HTTP(new Attribution.Builder("OkHttp")
            .addCopyrightNotice("Copyright 2016 Square, Inc.")
            .addLicense(License.APACHE)
            .setWebsite("http://square.github.io/okhttp/")
            .build()),
    RETROFIT(new Attribution.Builder("Retrofit")
            .addCopyrightNotice("Copyright 2013 Square, Inc.")
            .addLicense(License.APACHE)
            .setWebsite("http://square.github.io/retrofit/")
            .build()),

    // Parser
    GSON(new Attribution.Builder("Gson")
            .addCopyrightNotice("Copyright 2008 Google Inc.")
            .addLicense(License.APACHE)
            .setWebsite("https://github.com/google/gson")
            .build()),

    // ORM & Database
    REALM(new Attribution.Builder("Realm")
            .addCopyrightNotice("Copyright 2016 Realm Inc.")
            .addLicense(License.APACHE)
            .setWebsite("https://github.com/realm/realm-java")
            .build());

    private Attribution attribution;

    Library(Attribution attribution) {
        this.attribution = attribution;
    }

    public Attribution getAttribution() {
        return attribution;
    }
}
