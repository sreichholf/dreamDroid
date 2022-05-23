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
 * An enumeration of the most common open source licenses.
 */
public enum License {
    APACHE("Apache License 2.0", "http://www.apache.org/licenses/LICENSE-2.0"),
    BSD_2("BSD-2-Clause", "https://opensource.org/licenses/BSD-2-Clause"),
    BSD_3("BSD-3-Clause", "https://opensource.org/licenses/BSD-3-Clause"),
    GPL_2("GPL-2.0", "http://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html"),
    GPL_3("GPL-3.0", "http://www.gnu.org/licenses/gpl-3.0-standalone.html"),
    LGPL_2_1("LGPL-2.1", "http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html"),
    LGPL_3("LGPL-3.0", "http://www.gnu.org/licenses/lgpl-3.0-standalone.html"),
    MIT("MIT License", "https://opensource.org/licenses/MIT");

    private LicenseInfo licenseInfo;

    License(String name, String textUrl) {
        this.licenseInfo = new LicenseInfo(name, textUrl);
    }

    public LicenseInfo getLicenseInfo() {
        return licenseInfo;
    }
}
