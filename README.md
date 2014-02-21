[![Build Status](https://drone.io/github.com/sreichholf/dreamDroid/status.png)](https://drone.io/github.com/sreichholf/dreamDroid/latest)

# LICENSE
>© Stephan Reichholf (stephan at reichholf dot net)
>
>Unless stated otherwise in a files head all java and xml-code of this Project is:
>Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
>http://creativecommons.org/licenses/by-nc-sa/3.0/
> 
>All graphics, except the dreamDroid icon, can be used for any other non-commercial purposes.
>The dreamDroid icon may not be used in any other projects than dreamDroid itself.
>
>All files that are part of 3rd party projects are not part of this license agreement but keep their original license

# Functionality
Remotely control your enigma2 based Dreambox with any Android Device (running android >= 2.1)!

dreamDroid has a fragment-based layout which is optimized for all common device sizes.

# Target Platforms
dreamDroid is built for use with genuine Dreamboxes and the included genuine WebInterface.
Officially supported are the following devices:

* Dreambox DM 7020 HD
* Dreambox DM 8000 HD PVR
* Dreambox DM 800 HD se
* Dreambox DM 800 HD PVR
* Dreambox DM 500 HD
* Dreambox DM 7025(+)

# Requirements

* The target dreamboxes should be running a WebInterface with Version 1.6.8 or later.
* dreamDroid requires at least Android 2.1 (which is the minimum SDK Version required by android-support-v7-appcompat) to work properly.

# Developing / building dreamDroid with AndroidStudio

Simply import dreamDroid as gradle project and you should be fine.


## Required Libraries

* android-support-v7-appcompat - Googles appcompat library (for the ActionBar)
* numberpicker - Michael Novakjr's Numberpicker (cloned on 2012-09-08): https://github.com/mrn/numberpicker/
* gaugeview-library - CodeAndMagic's GaugeView: https://github.com/CodeAndMagic/GaugeView
* ckChangeLog - ckettis ckChangeLog library: https://github.com/cketti/ckChangeLog
* PhotoView - Chris Banes PhotoView library: https://github.com/chrisbanes/PhotoView
* MemorizingTrustManager as seen in owncloud's News App: https://github.com/owncloud/News-Android-App/

The following SDK versions have to be installed for a clean build:

* API v18 (4.3) for the android libraries
* API v19 (4.4) for dreamDroid itself
