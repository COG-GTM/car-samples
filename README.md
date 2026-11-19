Car Samples Repository
======================

This repository contains individual Android projects to help you get started writing apps for
the car. See [docs/architecture.md](docs/architecture.md) for how the projects are built and how
they run.

The samples are grouped by the library they use to integrate with the car:

- [`car_app_library/`](car_app_library/README.md) - samples for the Android for Cars App Library,
  which lets you build navigation, point-of-interest and other template-based apps. Each sample
  (`navigation`, `showcase`) builds a `mobile` app that Android Auto projects from a phone and an
  `automotive` app that runs on Android Automotive OS.
- [`car-lib/`](car-lib/README.md) - samples that use the Android Automotive OS Car API directly.
  It currently holds one app, [`CarGearViewerKotlin`](car-lib/CarGearViewerKotlin/README.md),
  which shows the car's current gear.

The two folders are separate Gradle builds with their own wrappers and JDK versions:

| Project | JDK | Build |
| --- | --- | --- |
| `car_app_library/` | 17 | `./gradlew :showcase:mobile:assembleDebug :showcase:automotive:assembleDebug` |
| `car-lib/CarGearViewerKotlin/` | 11 | `./gradlew :automotive:assembleDebug` |
