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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that contain information needed to comply with library licenses.
 */
public final class Attribution implements Comparable<Attribution> {

    private String name;
    private List<String> copyrightNotices;
    private List<LicenseInfo> licensesInfo;
    private String website;

    private Attribution(String name, List<String> copyrightNotices, List<LicenseInfo> licensesInfo, String website) {
        this.name = name;
        this.copyrightNotices = copyrightNotices;
        this.licensesInfo = licensesInfo;
        this.website = website;
    }

    public String getName() {
        return name;
    }

    public List<String> getCopyrightNotices() {
        return copyrightNotices;
    }

    public String getFormattedCopyrightNotices() {
        StringBuilder builder = new StringBuilder();
        for (String copyrightNotice : copyrightNotices) {
            builder.append("\n").append(copyrightNotice);
        }
        return builder.toString().replaceFirst("\n", "");
    }

    public List<LicenseInfo> getLicensesInfo() {
        return licensesInfo;
    }

    public String getWebsite() {
        return website;
    }

    @Override
    public int compareTo(@NonNull Attribution o) {
        return this.name.compareToIgnoreCase(o.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Attribution)) return false;

        Attribution that = (Attribution) o;

        if (!name.equals(that.name)) return false;

        for (String copyrightNotice : copyrightNotices) {
            if (!that.copyrightNotices.contains(copyrightNotice)) return false;
        }

        for (LicenseInfo licenseInfo : licensesInfo) {
            if (!that.licensesInfo.contains(licenseInfo)) return false;
        }

        return website.equals(that.website);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + copyrightNotices.hashCode();
        result = 31 * result + licensesInfo.hashCode();
        result = 31 * result + website.hashCode();
        return result;
    }

    public static class Builder {
        private String name;
        private List<String> copyrightNotices;
        private List<LicenseInfo> licenseInfos;
        private String website;

        public Builder(String name) {
            this.name = name;
            this.copyrightNotices = new ArrayList<>();
            this.licenseInfos = new ArrayList<>();
            this.website = "";
        }

        public Builder addCopyrightNotice(String notice) {
            copyrightNotices.add(notice);
            return this;
        }

        public Builder addCopyrightNotice(String copyrightHolder, String year) {
            copyrightNotices.add("Copyright " + year + " " + copyrightHolder);
            return this;
        }

        public Builder addLicense(String name, String textUrl) {
            licenseInfos.add(new LicenseInfo(name, textUrl));
            return this;
        }

        public Builder addLicense(License license) {
            licenseInfos.add(license.getLicenseInfo());
            return this;
        }

        public Builder setWebsite(String website) {
            this.website = website;
            return this;
        }

        public Attribution build() {
            return new Attribution(name, copyrightNotices, licenseInfos, website);
        }
    }
}
