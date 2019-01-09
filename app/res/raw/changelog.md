## 1.3.426
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

