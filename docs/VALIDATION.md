# Validation / 验证记录

- Release build: **v0.27.0**, versionCode **27**, arm64-v8a, non-debuggable, **unsigned** (the distribution platform re-signs it).
- Source: frozen v0.27 source plus the three-layer launcher icon; runtime assets byte-identical to the v0.27 build.
- Tests: **112 passed**, 0 failures, 0 errors, 0 skipped.
- Manifest checked with aapt2: package `com.kejing.spatialarcade.melody`, not debuggable. apksigner confirms no signature.
- APK SHA-256 (unsigned): `38081cf4e5df2292ef1be021f25a248a9ac2f95721030defe962df1481b97205`. Re-signing changes this hash.
- Only Marble Melody Space media is bundled (score box, 20 v0.27 routes, note samples, album art); other games' simulation code remains in the shared regression library.
- Screenshots come from native emulator runs of development builds (v0.27 playing view; v0.26 library/overview). Physical hand input, sound quality and long-session performance are not claimed as release acceptance.

本次生成独立项目与未签名 release APK（由分发平台重签名）。已核对构建类型、包名、版本、资源范围和 112 项测试。截图来自此前开发版的模拟器原生画面；真实设备上的手势、音效与性能仍需验收。

See [machine-readable evidence](build-verification.json).
