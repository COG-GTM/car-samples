AAOS Car Gear Viewer (Kotlin)
===========================================
Demonstrates connecting to the Car API and listening for gear change events. Works on
Android P (API 28) and above.

Introduction
============
This app allows a user to see the current gear on the main infotainment screen (IVI).

The app connects to the Car API and subscribes to gear change events using `CarPropertyManager`.
If it receives a change event, it updates the UI; if it receives an error events, it just logs them.

Please find Car API documentation on https://developer.android.com/reference/android/car/packages
and car services source code at
https://cs.android.com/android/platform/superproject/+/master:packages/services/Car/car-lib

Prerequisites
--------------

- [Android 10 SDK (API level 29) Revision 5](https://developer.android.com/studio/releases/platforms#10) or newer.
- A device running Android Automotive OS P or newer.

Getting Started
---------------
This sample uses the Gradle build system (Gradle 5.6.4 wrapper, Android Gradle Plugin 3.6.1) and
needs JDK 11. Point Gradle at your Android SDK with `ANDROID_HOME` or a `local.properties` file
containing `sdk.dir=<your android sdk directory path>`, then run:

```bash
./gradlew :automotive:assembleDebug
adb install automotive/build/outputs/apk/debug/automotive-debug.apk
```

On an Android Automotive OS emulator, change the gear with
`adb shell cmd car_service inject-vhal-event 289408001 <1=N|2=R|4=P|8=D>`.

Support
-------

- Stack Overflow: https://stackoverflow.com/questions/tagged/android-automotive
