
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
* dreamDroid requires at least Android 2.1 (which is the minimum SDK Version required by ActionBarSherlock >= 4.0) to work properly.

# Developing / building dreamDroid (with eclipse)

Before you can build dreamDroid you usually have to import some android library projects into your eclise workspace.
All required libraries can be found in the "libraries" folder of this git repository.

## Required Libraries

* VPI - Jake Wharton's ViewPagerIndicator (2.3.1): http://viewpagerindicator.com/
* ABS - Jake Wharton's ActionBarSherlock (4.1.0): http://actionbarsherlock.com/ 
* numberpicker - Michael Novakjr's Numberpicker (cloned on 2012-09-08): https://github.com/mrn/numberpicker/
* SlidingMenu - jfeinstein's Sliding menu (cloned on 2013-02-14): https://github.com/jfeinstein10/SlidingMenu

The following SDK versions have to be installed for a clean build:

* API v14 (4.0) for the ViewPagerIndicator and ActionBarSherlock
* API v15 (4.0.3) for the numberpicker
* API v16 (4.1) for dreamDroid itself
