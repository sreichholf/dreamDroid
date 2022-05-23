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

package com.franmontiel.attributionpresenter.sample;

import android.content.Context;

import com.franmontiel.attributionpresenter.AttributionPresenter;
import com.franmontiel.attributionpresenter.entities.Attribution;
import com.franmontiel.attributionpresenter.entities.Library;
import com.franmontiel.attributionpresenter.entities.License;
import com.franmontiel.attributionpresenter.listeners.OnAttributionClickListener;
import com.franmontiel.attributionpresenter.listeners.OnLicenseClickListener;

public class AttributionPresenterCreator {

    private static AttributionPresenter.Builder createBaseAttributions(Context context) {
        return new AttributionPresenter.Builder(context)
                .addAttributions(
                        new Attribution.Builder("AttributionPresenter")
                                .addCopyrightNotice("Copyright 2017 Francisco José Montiel Navarro")
                                .addLicense(License.APACHE)
                                .setWebsite("https://github.com/franmontiel/AttributionPresenter")
                                .build()
                )
                .addAttributions(
                        Library.BUTTER_KNIFE,
                        Library.GLIDE,
                        Library.DAGGER_2,
                        Library.GSON,
                        Library.REALM);
    }

    public static AttributionPresenter create(Context context) {
        return createBaseAttributions(context).build();
    }

    public static AttributionPresenter create(Context context,
                                              OnAttributionClickListener onAttributionClickListener,
                                              OnLicenseClickListener onLicenseClickListener) {
        return createBaseAttributions(context)
                .setOnAttributionClickListener(onAttributionClickListener)
                .setOnLicenseClickListener(onLicenseClickListener)
                .build();
    }

    public static AttributionPresenter create(Context context, int itemLayout, int licenseLayout) {
        return createBaseAttributions(context)
                .setItemLayout(itemLayout)
                .setLicenseLayout(licenseLayout)
                .build();
    }
}
