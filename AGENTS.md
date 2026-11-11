# Marble Melody Space development handoff

Build: JDK 21; `./gradlew testDebugUnitTest assembleDebug assembleRelease`. SDK 6.1.9; SpatialUI/PicoTheme only; retain static Noto Sans SC 500/600.

This repository builds only GameKind.C, package `com.kejing.spatialarcade.melody`, from the frozen v0.27 source. Preserve simulation behavior and the MIDI-derived note timing; route GLBs and physics are generated together, do not edit one without the other. Keep debug receivers in src/debug only. Do not add a release signingConfig or keystore: release APKs ship unsigned and the distribution platform re-signs them. Never commit signing keys, passwords, local.properties or logs containing device identifiers.

Detailed internal history is maintained separately in the private context repository.
