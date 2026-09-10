Car Samples Repository
==================

This repository contains a set of individual Android Studio projects to help you get started
writing apps for the car.

The samples are categorized by the libraries they use to integrate with the car:
- Car App Library - The library that allows developers to build navigation, parking,
and charging apps for the Android Auto-compatible cars. The app may be running in
a phone connected to the car or in the Android Automotive OS running in the car.
- car-lib/  - A sample library for Android Automotive OS.

Java 11 → 21 migration
=======================

Both Gradle projects require JDK 21 to build.

- Gradle: 5.6.4 → 8.9 for `car-lib/CarGearViewerKotlin`; 8.6 → 8.9 for `car_app_library`.
- Android Gradle Plugin: 3.6.1 → 8.5.2 for `car-lib/CarGearViewerKotlin`; 8.4.2 → 8.5.2 for `car_app_library`.
- Kotlin: 1.3.61 → 2.0.20 for `car-lib/CarGearViewerKotlin`; 2.0.0 → 2.0.20 for `car_app_library`.
- Repositories use `mavenCentral()` instead of `jcenter()`.
- The deprecated `kotlin-android-extensions` plugin was removed.
- The Android namespace moved from the manifest to `build.gradle`.
- Android bytecode targets are unchanged (Java 1.8 for `car-lib/CarGearViewerKotlin`, Java 17 for `car_app_library`); Android compileSdk, targetSdk, and minSdk values are unchanged.
