Linux Environment runtime assets
================================

The stable Linux local tool validates these app assets before it reports ready:

- `linux/proot/<abi>/proot` copied at runtime to `filesDir/linux_env/bin/proot`
- Alpine rootfs extracted to `filesDir/linux_env/rootfs`

Supported ABI names should match Android's `Build.SUPPORTED_ABIS`, for example
`arm64-v8a`, `armeabi-v7a`, and `x86_64`.

Bundled PRoot binaries are sourced from:
https://github.com/green-green-avk/build-proot-android

Archive to Android ABI mapping:

- `proot-android-aarch64.tar.gz` -> `linux/proot/arm64-v8a/proot`
- `proot-android-armv7a.tar.gz` -> `linux/proot/armeabi-v7a/proot`
- `proot-android-x86_64.tar.gz` -> `linux/proot/x86_64/proot`

Until the ABI-specific PRoot binary is packaged and Alpine rootfs is installed,
`linux_environment_status` and `run_linux_command` return structured setup
status instead of crashing.
