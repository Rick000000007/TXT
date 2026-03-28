# TX Terminal

A fully-featured Android terminal emulator with custom userspace support, PTY-based process execution, and storage integration.

## Features

### Core Features
- **PTY-based Terminal**: Full pseudo-terminal support with proper TTY handling
- **Custom Userspace**: Bundle and execute custom binaries and libraries
- **Transparent Execution**: Automatic linker invocation for userspace binaries (no manual linker needed)
- **Argument-Safe Commands**: Proper argument parsing and safe execution
- **Command Override**: Userspace binaries automatically override system commands
- **Native NDK Engine**: High-performance C++ terminal engine
- **Storage Integration**: Easy access to Android storage via `setup-tx-storage`
- **Storage Auto-Init**: Automatic storage setup on first launch
- **Network Support**: Full internet access permissions
- **Modern Android**: Supports Android 7.0+ (API 24+) with modern storage permissions

### UI/UX Features
- **Monochrome Theme**: Clean black/white/grey design
- **Multi-Session Support**: Up to 10 concurrent terminal sessions with tab interface
- **Virtual Key Bar**: Special keys (CTRL, TAB, ESC, arrows, etc.)
- **Smart CTRL Logic**: Tap = one-time, Long press = lock
- **Text Selection**: Copy/Paste with proper monochrome colors
- **Long Press Menu**: Copy, Paste, Select All
- **Wake Lock**: Keep terminal awake option
- **Smooth Scrolling**: Momentum scroll with GPU-friendly rendering
- **Pinch-to-Zoom**: Adjustable font size

## Project Structure

```
TX-Terminal/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/tx/terminal/
│   │   │   ├── cpp/                    # Native C++ code
│   │   │   ├── assets/usr/             # Userspace binaries
│   │   │   │   ├── bin/                # Custom binaries
│   │   │   │   └── lib/                # Custom libraries
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
├── CMakeLists.txt                      # Native build configuration
└── README.md
```

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- Android NDK 25.2.9519653
- CMake 3.22.1+
- JDK 17

### Build Steps

1. **Clone the repository**:
   ```bash
   cd TX-Terminal/android
   ```

2. **Build with Gradle**:
   ```bash
   ./gradlew assembleDebug
   ```

   Or for release:
   ```bash
   ./gradlew assembleRelease
   ```

3. **Install on device**:
   ```bash
   ./gradlew installDebug
   ```

### Build Variants

- `debug`: Debug build with symbols and logging
- `release`: Optimized release build

## Userspace System

### Overview

TX Terminal includes a custom userspace that allows bundling and executing custom binaries:

- **Binaries**: Stored in `assets/usr/bin/`
- **Libraries**: Stored in `assets/usr/lib/`
- **Environment**: Configured with custom PATH and LD_LIBRARY_PATH

### Included Binaries

| Binary | Description |
|--------|-------------|
| `sh` | Shell wrapper with custom environment |
| `hello` | Demo program showing userspace execution |
| `echo` | Print arguments to stdout |
| `setup-tx-storage` | Storage integration setup script |
| `help` | Built-in help command |
| `tx-info` | System information display |

### Environment Variables

```bash
PATH=/data/user/0/com.tx.terminal/files/usr/bin:/system/bin
LD_LIBRARY_PATH=/data/user/0/com.tx.terminal/files/usr/lib:/system/lib64
HOME=/data/user/0/com.tx.terminal/files/usr/home
TMPDIR=/data/user/0/com.tx.terminal/files/usr/tmp
TERM=xterm-256color
```

## Storage Setup

### Using setup-tx-storage

Run the storage setup command in the terminal:

```bash
setup-tx-storage
```

This creates the following symlinks:

| Path | Target |
|------|--------|
| `~/storage/shared` | `/sdcard` |
| `~/storage/downloads` | `/sdcard/Download` |
| `~/storage/documents` | `/sdcard/Documents` |
| `~/storage/pictures` | `/sdcard/Pictures` |
| `~/storage/music` | `/sdcard/Music` |
| `~/storage/movies` | `/sdcard/Movies` |

### Accessing Storage

After running `setup-tx-storage`, access storage via:

```bash
ls ~/storage/shared      # List main storage
ls ~/storage/downloads   # List downloads
cd ~/storage/documents   # Change to documents
```

## Custom Binary Guide

### Adding a Custom Binary

1. **Compile your binary** for Android (ARM64):
   ```bash
   $NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang \
       -o mybinary mybinary.c -static
   ```

2. **Copy to assets**:
   ```bash
   cp mybinary android/app/src/main/assets/usr/bin/
   ```

3. **Set executable permission** in `UserspaceInstaller.kt`:
   ```kotlin
   file.setExecutable(true, false)
   ```

4. **Rebuild and install**:
   ```bash
   ./gradlew installDebug
   ```

5. **Run your binary**:
   ```bash
   mybinary
   ```

### Example: hello.c

The included `hello` program demonstrates:
- Argument parsing
- Environment variable access
- System information display

```bash
hello           # Default greeting
hello -n John   # Greet by name
hello --env     # Show environment
hello --help    # Show help
```

## Transparent Execution

TX Terminal now features **transparent command execution** - userspace binaries are automatically executed via the system linker without manual intervention:

```bash
# These commands automatically use the userspace binaries
hello                    # No manual linker needed
ls                       # Uses userspace ls if available
echo "Hello World"       # Uses userspace echo
```

### How It Works

1. **Command Resolution**: When a command is entered, the engine checks if it exists in `/data/user/0/com.tx.terminal/files/usr/bin/`
2. **Automatic Linker**: If found, the binary is automatically executed via `/system/bin/linker64`
3. **Fallback**: If not in userspace, the command falls back to system PATH
4. **Argument Safety**: All arguments are properly parsed and passed safely

### Command Cache

Resolved binary paths are cached for faster subsequent executions:
- First `hello` → resolves → caches → executes
- Second `hello` → uses cache → executes faster

## Native Engine API

### JNI Bridge

The native terminal engine provides:

```kotlin
// Initialize
NativeTerminal.initialize()

// Set userspace path
NativeTerminal.setUserspacePath("/data/.../files/usr")

// Execute command
NativeTerminal.executeCommand("ls -la")

// Execute with linker
NativeTerminal.executeWithLinker("/system/bin/linker64", "usr/bin/sh", arrayOf("-l"))

// Send input to running process
NativeTerminal.sendInput("echo hello\n")

// Resize terminal
NativeTerminal.resizeTerminal(24, 80)

// Kill process
NativeTerminal.killProcess(15)  // SIGTERM
NativeTerminal.killProcess(9)   // SIGKILL
```

### PTY Operations

The native engine handles:
- PTY master/slave creation
- Terminal attribute configuration
- Process forking and execution
- Environment variable injection
- Output streaming via callbacks

## Permissions

### Network
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
```

### Storage (Modern)
```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>
```

### Storage (Legacy)
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29"/>
```

## Feature Flags

### USE_USERSPACE

Toggle userspace support in Settings:

- **Enabled**: Use custom binaries from `usr/bin/`
- **Disabled**: Use only system binaries

Programmatically:
```kotlin
TXApplication.getInstance().setUserspaceEnabled(true)
```

## Testing

### Test Commands

Run these commands to verify functionality:

```bash
# Test userspace binary
hello

# Test storage setup
setup-tx-storage
ls ~/storage

# Test shell
sh -c "echo Hello from userspace shell"

# Test environment
echo $PATH
echo $HOME
echo $LD_LIBRARY_PATH

# Test system commands
ps
df
uname -a
```

### Expected Output

```
$ hello

╔═══════════════════════════════════════╗
║     TX Terminal - Hello Program       ║
║           Version 1.0.0               ║
╚═══════════════════════════════════════╝

Hello, World! 👋
Welcome to TX Terminal!

$ setup-tx-storage
🔧 Setting up TX storage...
  ✓ Linked /sdcard -> ~/storage/shared
  ✓ Linked /sdcard/Download -> ~/storage/downloads
✅ Storage setup complete!
```

## Troubleshooting

### Userspace not working

1. Check if userspace is enabled in Settings
2. Verify binaries were copied:
   ```bash
   ls /data/data/com.tx.terminal/files/usr/bin/
   ```
3. Check permissions:
   ```bash
   ls -la /data/data/com.tx.terminal/files/usr/bin/sh
   ```

### Storage access denied

1. Grant storage permissions in Android Settings
2. Run `setup-tx-storage` again
3. Check Android version compatibility

### Native library not loading

1. Verify NDK is installed: `sdkmanager --list_installed | grep ndk`
2. Check ABI compatibility in `build.gradle`
3. Clean and rebuild: `./gradlew clean assembleDebug`

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────┐
│           MainActivity.kt               │
│  ┌─────────────┐    ┌──────────────┐   │
│  │ TerminalView│    │ CommandInput │   │
│  └─────────────┘    └──────────────┘   │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│         NativeTerminal.kt               │
│         (JNI Bridge)                    │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│      terminal_engine.cpp (NDK)          │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
│  │   PTY   │  │  Fork   │  │  Exec   │ │
│  │  Setup  │  │ Process │  │Command  │ │
│  └─────────┘  └─────────┘  └─────────┘ │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│         Userspace Binaries              │
│    /data/.../files/usr/bin/sh           │
│    /data/.../files/usr/bin/hello        │
└─────────────────────────────────────────┘
```

## License

MIT License - See LICENSE file for details

## Contributing

Contributions welcome! Please follow the existing code style and add tests for new features.

## Credits

- TX Terminal Team
- Android NDK Team
- Open Source Community

---

**Version**: 1.0.0  
**Build**: 2024  
**Min SDK**: 24 (Android 7.0)  
**Target SDK**: 34 (Android 14)
