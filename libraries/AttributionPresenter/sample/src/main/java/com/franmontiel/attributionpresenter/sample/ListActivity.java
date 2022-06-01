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

import android.os.Bundle;
import androidx.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;
import android.widget.Toast;

import com.franmontiel.attributionpresenter.entities.Attribution;
import com.franmontiel.attributionpresenter.entities.LicenseInfo;
import com.franmontiel.attributionpresenter.listeners.OnAttributionClickListener;
import com.franmontiel.attributionpresenter.listeners.OnLicenseClickListener;

public class ListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list);

        ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(AttributionPresenterCreator.create(
                this,
                new OnAttributionClickListener() {
                    @Override
                    public boolean onAttributionClick(Attribution attribution) {
                        Toast.makeText(getApplicationContext(), "Attribution click: " + attribution.getName(), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                },
                new OnLicenseClickListener() {
                    @Override
                    public boolean onLicenseClick(LicenseInfo licenseInfo) {
                        Toast.makeText(getApplicationContext(), "License click: " + licenseInfo.getName(), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }).getAdapter());
    }
}
