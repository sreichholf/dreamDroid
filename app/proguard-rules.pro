-keep class android.support.v4.internal.** { *; }
-keep interface android.support.v4.internal.** { *; }
-keep class android.support.v7.internal.** { *; }
-keep interface android.support.v7.internal.** { *; }
-keep class android.support.design.widget.** { *; }
-keep interface android.support.design.widget.** { *; }
-keep class uk.co.senab.photoview.** { *; }
-keep interface uk.co.senab.photoview.** { *; }
-dontwarn android.support.design.**
-dontwarn android.support.v7.**
-dontwarn android.support.v4.**
# Do not optimize/shrink LibVLC, because of native code
-keep class org.videolan.libvlc.** { *; }
# Same for MediaLibrary
-keep class org.videolan.medialibrary.** { *; }

