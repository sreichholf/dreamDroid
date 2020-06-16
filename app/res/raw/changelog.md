## 1.5.439
* UPD: Update google libraries, adjust themes
* FIX: Fix build issues on f-droid

## 1.5.438
* FIX: restore compatibility with Android < 5.0
* FIX: crash when streaming

## 1.5.437
* DEL: Auto-Theme selection and with that the coarse location permission have been dropped
* DEL: Donations have been removed with no intention for any replacement (~70% of any donation got eaten up by google and local taxes)
* UPD: AndroidX libriaries updated to 1.1 where available
* UPD: Built with Android 10 SDK
* FIX: Some minor crashes have been fixed

## 1.4.434
* Remove tracking/statistics module and everything related (Privacy statement is gone, too)
* NEW: expose TabbedNavigationActivity via URL-Scheme (thx to Stefan H.)
* NEW: move progress to top of service-card
* FIX: Video Overlay could get messed up at certain show/hide timings
* DEV: Code cleanups

## 1.4.433
* FIX: Virtual remote widget
* FIX (potentially): Broken longclick in lists on some devices

## 1.4.432
* FIX: Virtual remote layout was broken
* FIX: Potential fix for unclickable items in servicelist for few users

## 1.4.431
* Redesign Video player for all devices (tv/mobile, includes PVR controls for recordings)
* Slighty restyle Movie/Service-EPG Detail Dialog, add it for TV
* Use Android 9 style for buttons
* Add "Running Event" dialog to video player
* FIX: Backup restore handling
* FIX: crash in relation to automated device detection
* FIX: crash in video player when a recording is an invalid length in it's metadata

## 1.3.430
* FIX: crash when trying to stream with external video player
* FIX: crash when encoder-port/bitrate is empty or invalid
* FIX: Bug in integrated gaugeview-library that caused "READ_PHONE_STATE" permission to be added by the android SDK [https://github.com/...](https://github.com/sreichholf/dreamDroid/commit/f3eb97472a850ddbeca7bf91a14c4163f845cc35)

## 1.3.429
* Improvements to the videoplayer (don't stop on rotation, jump in recordings, playback time displayed correctly when watching recorded movies)

## 1.3.428
* Improve overall usability of Video Player (especially on TV)
* TV: Cleanup settings, add encoder settings to connection profile
* FIX: Movie-EPG was bright in Night-Mode

## 1.3.425
* FIX: Keyboard controllability
* DEV: Code cleanup

## 1.3.424
* FIX: Crash on Profile edit
* FIX: Crashes on devices with Android < 8 (Servicelist, Backup)

## 1.3.423
* FIX: Android TV issues (introduced in 1.2.420)
* NEW: Add Open Source License Attribution (new button in About)
* DEV: Internal changes (quite a bit of them) to fix some long pending crash issues (android-state with livefront:bridge)
* DEV: Workaround an android issue with Map and Parcel [https://medium.com/...](https://medium.com/the-wtf-files/the-mysterious-case-of-the-bundle-and-the-map-7b15279a794e)

## 1.2.422
* FIX: Crashes in settings backup
* FIX: Some AndroidTV Eligibility issues

## 1.2.421
* FIX: unencrypted http

## 1.2.420
* NEW: Settings Backup
* FIX: Some improvements for the TV version of dreamDroid
* FIX: Fix NPEs
* DEV: Switch to Android SDK 28 and AndroidX
* DEV: Update external libraries
* DEV: Code cleanups

## 1.2.418
* FIX: Picon sync was writing empty files (resync to fix picon display)

## 1.2.417
* NEW: New dialog for changes
* FIX: A bunch of bugs that caused odd behaviour (empty lists, wrong content) and crashes after rotating or resuming the app
* IMP: Improve Singal Monitor sound quality, force screen on while it's open

## 1.2.416
* FIX: Startup crash on pre-lollipop devices

## 1.2.415
* Note: For users of the play store all changes after 1.2-400 should be taken into account!
* FIX: Virtual Remote Widget on Android >= Oreo

## 1.2.414
* More fixes to the VideoPlayer
* Fast scrolling is back

## 1.2.409
* DreamDroid is now free Software, licensed under GPLv3
* Some changes for releasing on f-droid

## 1.2-405
* FIX: Sync-Notifications on Android >= Oreo

## 1.2-404
* Adaptive icon on Android >= Oreo

## 1.2-403
* FIX: Update to latest VLC, hopefully fixes all kinds of video/audio playback issues

## 1.2-400
* FIX: Screenshots

## 1.2-398
* FIX: Several crashes
* FIX: Reload button

## 1.2-397
* FIX: Crashes when leaving big lists (e.g. when using Edit Timer from full service EPG)

## 1.2-396
* FIX: Several crashes (hopefully the worst ones)
* FIX: Theme changes weren't applied until the app was restarted

## 1.2-395
* FIX: Crash when choosing to "Edit Timer" out of the EPG list
* FIX: Missing text on yes/no dialogs

## 1.2-394
* NEW: Initial support for Android TV
* NEW: Internal updates and fixes
* NEW: Some cool improvements to the navigation by F. Edelmann
* NEW: Lot's of visual changes and improvements
* FIX: Issues with certificates
* IMP: Performance improvements

## 1.1-386
* FIX: Crashing VideoPlayer

## 1.1-385
* FIX: Some android 7.0 related crashes
* FIX: Some video player issues where rotation/multiwindow was messing up the aspect ratio

## 1.1-384
* NEW: Audio/Subtitle track selection in video player
* FIX: some crashes
* IMP: minor ui improvements

## 1.1-381
* FIX: "loading..." showed up after rotation when a list was visible

## 1.1-380
* FIX: Improve error handling in some of the lists
* FIX: Workaround for issues with "EPG" on older dreambox models

## 1.1-379
* FIX: Profile Name fix was actually missing

## 1.1-378
* NEW: Picons can now be loaded on-demand via http(s) without syncing them before (beware your traffic!)
* FIX: Profile name was not visible for editing anymore
* FIX: Hide EPG-Date/Time selection header when changing from EPG to Settings

## 1.1-377
* NEW: Change EPG Info from Popup to Bottom Sheet
* IMP: Improve video player behaviour in terms of showing/hiding the the services
* NEW: Add brightness and volume gestures to video player (brightness left half, volume right half)
* NEW: Add basic "seek" functionality to video player
* FIX: The video player service list crashed when selecting a marker
* FIX: Try to fix newlines being filtered out, this COULD cause issues on older boxes, please report if so
* FIX: Request location permission only if actually required. Make the default Theme settings "Day" so it's not required as a default (Android >= 6.0)
* IMP: Movie list folder selection now uses a Floating Action Button
* IMP: Zap bouquet selection is now similar to the one know from "EPG"
* IMP: Connection profile checks will not be repeated over and over again
* TEC: Change a lot of graphics to vector images
* TEC: Switch from Universal Image Loader to Picasso
* TEC: Update some external libraries and cleanup some stuff

## 1.1-372
* FIX: Serveral smaller errors that caused crashes
* FIX: The app is a lot smaller again

## 1.1
* NEW: Add an integrated video player based on VLC
* NEW: Support for Encoder-based streams on DM7080 and DM820
* NEW: Proper Android 6.x permission support
* NEW: Automatic Day/Night Theme switching (configurable in settings, requires coarse location)
* NEW: Android N MultiWindow support
* UPD: Latest state-of-the art UI and external libraries (HUGE internal changes!)
* FIX: Some smaller fixes (e.g. broken EPG with older devices, doubled location/tag entries, ...)
* NEW: Bugs we haven't yet discovered ;)

