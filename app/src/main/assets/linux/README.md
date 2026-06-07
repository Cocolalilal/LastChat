Linux Environment runtime assets
================================

The stable Linux local tool validates these runtime files before it reports
ready:

- `app/src/main/jniLibs/<abi>/libproot.so` extracted by Android to
  `applicationInfo.nativeLibraryDir`
- `app/src/main/jniLibs/<abi>/libproot-userland.so` is preferred on
  Android 10+ because it is the fork's Android/UserLAnd-oriented runner
- `app/src/main/jniLibs/<abi>/libproot-loader.so` and, where available,
  `libproot-loader32.so` exported as `PROOT_LOADER` and `PROOT_LOADER_32`
- Alpine rootfs extracted to `filesDir/linux_env/rootfs`

Supported ABI names should match Android's `Build.SUPPORTED_ABIS`, for example
`arm64-v8a`, `armeabi-v7a`, and `x86_64`.

Bundled PRoot binaries are sourced from:
https://github.com/green-green-avk/build-proot-android

Archive to Android ABI mapping:

- `proot-android-aarch64.tar.gz` -> `jniLibs/arm64-v8a/libproot.so`
- `proot-android-armv7a.tar.gz` -> `jniLibs/armeabi-v7a/libproot.so`
- `proot-android-x86_64.tar.gz` -> `jniLibs/x86_64/libproot.so`

The `root/bin/proot-userland` binary from each archive is packaged as
`libproot-userland.so`.

Each archive also includes `root/libexec/proot/loader` and sometimes
`loader32`; these are packaged as `libproot-loader.so` and
`libproot-loader32.so` so PRoot can execute the loader from Android's native
library directory instead of an app-writable temp path.

The `.so` extension is intentional. Android reliably allows executing files
extracted from the native library directory; copied asset executables under
`filesDir` can fail with `error=13, Permission denied` on modern Android.

Until the ABI-specific PRoot binary is packaged and Alpine rootfs is installed,
`linux_environment_status` and `run_linux_command` return structured setup
status instead of crashing.
