Linux Environment runtime assets
================================

The stable Linux local tool validates these app assets before it reports ready:

- `linux/proot/<abi>/proot` copied at runtime to `filesDir/linux_env/bin/proot`
- Alpine rootfs extracted to `filesDir/linux_env/rootfs`

Supported ABI names should match Android's `Build.SUPPORTED_ABIS`, for example
`arm64-v8a`, `armeabi-v7a`, and `x86_64`.

Until the ABI-specific PRoot binary and Alpine rootfs are packaged or installed,
`linux_environment_status` and `run_linux_command` return structured setup
status instead of crashing.
