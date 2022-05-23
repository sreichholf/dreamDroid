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

public final class LicenseInfo {
    private String name;
    private String textUrl;

    LicenseInfo(String name, String textUrl) {
        this.name = name;
        this.textUrl = textUrl;
    }

    public String getName() {
        return name;
    }

    public String getTextUrl() {
        return textUrl;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LicenseInfo)) return false;

        LicenseInfo that = (LicenseInfo) o;

        if (!name.equals(that.name)) return false;
        return textUrl.equals(that.textUrl);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + textUrl.hashCode();
        return result;
    }
}
