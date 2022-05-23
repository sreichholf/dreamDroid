AttributionPresenter
=================
An Android library to easily display attribution information of open source libraries.

![AttributionPresenter Dialog](https://github.com/franmontiel/AttributionPresenter/blob/master/screenshot-dialog.png)
![AttributionPresenter Dark Themed List](https://github.com/franmontiel/AttributionPresenter/blob/master/screenshot-dark-theme.png)

Download
--------
Step 1. Add the JitPack repository in your root build.gradle at the end of repositories:
```groovy
allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
    }
}
```
Step 2. Add the dependency
```groovy
dependencies {
        compile 'com.github.franmontiel:AttributionPresenter:1.0.1'
}   
```
Usage
-----
Create an instance of `AttributionPresenter` using the `Builder`:
```java
AttributionPresenter attributionPresenter = new AttributionPresenter.Builder(context)
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
                        Library.REALM)
                .build();
```
Then you can directly show a dialog with the attributions:
```java
attributionPresenter.showDialog("Open Source Libraries");
```
Or get an adapter to use the list of attributions in a ListView:
```java
attributionPresenter.getAdapter();
```
The default behavior when the user clicks on an attribution item or on a license name is to open the browser with the website or license text. This behavior can be changed by setting the appropriate listeners in the `Builder`.

Styling
-----
The default style will adapt to Light and Dark themes automatically but you can always change the style of the attribution items:
* Overriding the default styles in your styles.xml.
 ```xml
    <style name="AttributionName" parent="TextAppearance.AppCompat">
        <item name="android:textColor">?attr/colorAccent</item>
        <item name="android:textSize">16sp</item>
    </style>

    <style name="AttributionCopyright" parent="TextAppearance.AppCompat">
        <item name="android:textColor">?android:attr/textColorPrimary</item>
    </style>

    <style name="AttributionLicense" parent="TextAppearance.AppCompat">
        <item name="android:textColor">?android:attr/textColorSecondary</item>
    </style>
 ```
* Or providing your own layouts for the item and/or license views using the `Builder`.

License
-------
    Copyright 2017 Francisco José Montiel Navarro

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
