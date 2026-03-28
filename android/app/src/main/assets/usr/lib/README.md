# Userspace Libraries

This directory contains shared libraries for TX Terminal userspace.

## Required Libraries

### libc++_shared.so
The C++ standard library shared object from the Android NDK.

This library is automatically provided by the Android build system when building native code with `ANDROID_STL=c++_shared`.

## Optional Libraries

### libandroid-support.so
Support library for additional Android functionality.

## Library Path

At runtime, the library path is set to:
```
LD_LIBRARY_PATH=/data/user/0/com.tx.terminal/files/usr/lib:/system/lib64:/vendor/lib64
```

## Adding Custom Libraries

1. Compile your library for Android (ARM64/ARM/x86_64)
2. Place the .so file in this directory
3. The library will be copied to the device during userspace installation
4. Ensure proper permissions (644) are set
