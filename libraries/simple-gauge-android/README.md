# Android Simple Gauge Library 
[![](https://jitpack.io/v/Gruzer/simple-gauge-android.svg)](https://jitpack.io/#Gruzer/simple-gauge-android)

Simple Gauge for Android. Currently avalible 4 shapes of gauges, you can change (colors of ranges) 



**Gauges...**<br/>
<img src="images/multigauge.jpg" width="20%" />
<img src="images/HalfGauge.jpg" width="20%" />
<img src="images/FullGauge.jpg" width="20%" />
<img src="images/ArcGauge.jpg" width="20%" />


** in app usage example** <br/>
<img src="images/app_gif.gif" width="25%" />



# Download

Add it in your root build.gradle at the end of repositories:

``` gradle

allprojects {
    repositories {
     	...
     	maven { url 'https://jitpack.io' }
    }
}

```
Add the dependency to app build.gradle
``` gradle
dependencies {
	implementation 'com.github.Gruzer:simple-gauge-android:0.3.1'
}
```


# Whats new

New release avalible <a href="https://github.com/Gruzer/simple-gauge-android/releases">Read </a>


# Simple Usage

```xml

<com.ekn.gruzer.gaugelibrary.HalfGauge
        android:id="@+id/halfGauge"
        android:layout_width="200dp"
        android:layout_height="200dp" />

```

for all gauges, first need to set Color Ranges and set min value and max value
```kotlin
	val range = Range()
        range.color = Color.parseColor("#ce0000")
        range.from = 0.0
        range.to = 50.0

        val range2 = Range()
        range2.color = Color.parseColor("#E3E500")
        range2.from = 50.0
        range2.to = 100.0

        val range3 = Range()
        range3.color = Color.parseColor("#00b20b")
        range3.from = 100.0
        range3.to = 150.0
		
	//add color ranges to gauge
	halfGauge.addRange(range)
        halfGauge.addRange(range2)
        halfGauge.addRange(range3)

	//set min max and current value
	halfGauge.minValue = 0.0
        halfGauge.maxValue = 150.0
        halfGauge.value = 0.0		

```


## All Speedometers, Gauges :

<table style="width:100%">
  <tr>
    <th>Name</th>
    <th>Screenshot</th>
    <th>XML Layout</th>
  </tr>

  <tr>
    <td width="24%"> <a href="">1. MultiGauge - Wiki</a></td>
    <td width="22%"><img src="images/multigauge.jpg"/></td>
    <td>
       <pre>
&lt; com.ekn.gruzer.gaugelibrary.MultiGauge
        android:id="@+id/multiGauge"
        android:layout_width="200dp"
        android:layout_height="200dp" />
	</pre>
    </td>
  </tr>

  <tr>
    <td> <a href="">2. HalfGauge - Wiki</a></td>
    <td><img src="images/HalfGauge.jpg"/></td>
    <td>
      <pre>
&lt; com.ekn.gruzer.gaugelibrary.HalfGauge
        android:id="@+id/halfGauge"
        android:layout_width="200dp"
        android:layout_height="200dp" />
      </pre>
    </td>
  </tr>

  <tr>
    <td> <a href="">3. FullGauge - Wiki</a></td>
    <td><img src="images/FullGauge.jpg"/></td>
    <td>
      <pre>
&lt; com.ekn.gruzer.gaugelibrary.FullGauge
        android:id="@+id/fullGauge"
        android:layout_width="200dp"
        android:layout_height="200dp" />
      </pre>
    </td>
  </tr>

  <tr>
    <td> <a href="">4. ArcGauge - Wiki</a></td>
    <td><img src="images/ArcGauge.jpg"/></td>
    <td>
      <pre>
&lt; com.ekn.gruzer.gaugelibrary.ArcGauge
        android:id="@+id/arcGauge"
        android:layout_width="200dp"
        android:layout_height="200dp" />
      </pre>
    </td>
  </tr>

  
</table>


## TODO
* Optimize.
* add animation to all gauges.
* build new custom gauges.
* add more custumize options
* add wiki 

###PS
if you have any idea, image, template please **open new issue** and give me the image , and i well try to add it to the Library.

## License

Copyright 2018 Evstafiev Konstantin 

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

* [Apache Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

