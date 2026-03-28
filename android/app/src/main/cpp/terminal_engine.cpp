#include "terminal_engine.h"
#include <jni.h>
#include <android/log.h>

#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>

// NEW: Additional headers for execution engine
#include <sstream>
#include <vector>
#include <unordered_map>

#define LOG_TAG "TXTerminal"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// NEW: TX_EXEC logging macro
#define TX_EXEC(...) __android_log_print(ANDROID_LOG_DEBUG, "TX_EXEC", __VA_ARGS__)

namespace tx {

// NEW: Global command cache for binary resolution
std::unordered_map<std::string, std::string> command_cache;

TerminalEngine::TerminalEngine()
    : userspacePath_(""),
      usrBinPath_(""),
      usrLibPath_(""),
      outputCallback_(nullptr),
      shouldStopReading_(false),
      userspaceEnabled_(true),
      savedRows_(24),
      savedCols_(80) {
    currentProcess_.pid = -1;
    currentProcess_.masterFd = -1;
    currentProcess_.isRunning = false;
}

TerminalEngine::~TerminalEngine() {
    cleanup();
}

bool TerminalEngine::initialize() {
    LOGD("Initializing TerminalEngine");
    return true;
}

void TerminalEngine::setUserspacePath(const std::string& path) {
    std::lock_guard<std::mutex> lock(mutex_);
    userspacePath_ = path;
    usrBinPath_ = path + "/bin";
    usrLibPath_ = path + "/lib";
    LOGD("Userspace path set to: %s", path.c_str());
}

void TerminalEngine::setOutputCallback(OutputCallback callback) {
    outputCallback_ = callback;
}

// NEW: Argument parser - splits command string into arguments
std::vector<std::string> split_args(const std::string& input) {
    std::istringstream iss(input);
    std::vector<std::string> args;
    std::string arg;

    while (iss >> arg) {
        args.push_back(arg);
    }
    return args;
}

// NEW: Binary resolver - checks if command exists in userspace
std::string resolve_binary(const std::string& cmd) {
    // Skip if it contains path separators (absolute/relative path)
    if (cmd.find("/") != std::string::npos) return "";
    if (cmd.find("..") != std::string::npos) return "";

    // Check cache first
    if (command_cache.count(cmd)) {
        return command_cache[cmd];
    }

    // Check userspace bin directory
    std::string base = "/data/user/0/com.tx.terminal/files/usr/bin/" + cmd;

    if (access(base.c_str(), X_OK) == 0) {
        command_cache[cmd] = base;
        return base;
    }

    return "";
}

bool TerminalEngine::createPty(int& masterFd, int& slaveFd) {
    masterFd = posix_openpt(O_RDWR | O_NOCTTY);
    if (masterFd < 0) {
        lastError_ = "Failed to open PTY master: " + std::string(strerror(errno));
        LOGE("%s", lastError_.c_str());
        return false;
    }

    if (grantpt(masterFd) < 0) {
        lastError_ = "Failed to grant PTY: " + std::string(strerror(errno));
        LOGE("%s", lastError_.c_str());
        close(masterFd);
        return false;
    }

    if (unlockpt(masterFd) < 0) {
        lastError_ = "Failed to unlock PTY: " + std::string(strerror(errno));
        LOGE("%s", lastError_.c_str());
        close(masterFd);
        return false;
    }

    char* slaveName = ptsname(masterFd);
    if (!slaveName) {
        lastError_ = "Failed to get PTY slave name";
        LOGE("%s", lastError_.c_str());
        close(masterFd);
        return false;
    }

    slaveFd = open(slaveName, O_RDWR);
    if (slaveFd < 0) {
        lastError_ = "Failed to open PTY slave: " + std::string(strerror(errno));
        LOGE("%s", lastError_.c_str());
        close(masterFd);
        return false;
    }

    return true;
}

bool TerminalEngine::setupPtySlave(int slaveFd) {
    struct termios termios;
    if (tcgetattr(slaveFd, &termios) < 0) {
        return false;
    }

    // Set raw mode
    cfmakeraw(&termios);

    // Enable echo
    termios.c_lflag |= ECHO;

    if (tcsetattr(slaveFd, TCSANOW, &termios) < 0) {
        return false;
    }

    // Set terminal size
    struct winsize ws;
    ws.ws_row = savedRows_;
    ws.ws_col = savedCols_;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(slaveFd, TIOCSWINSZ, &ws);

    return true;
}

void TerminalEngine::prepareEnvironment() {
    // Save current environment variables that we'll modify
    const char* envVars[] = {
        "PATH", "LD_LIBRARY_PATH", "HOME", "TMPDIR", "TERM"
    };

    for (const auto& var : envVars) {
        const char* value = getenv(var);
        if (value) {
            originalEnvVars_.push_back(std::string(var) + "=" + value);
        }
    }

    // Set custom environment variables
    if (userspaceEnabled_ && !userspacePath_.empty()) {
        std::string newPath = usrBinPath_ + ":/system/bin:/vendor/bin";
        setenv("PATH", newPath.c_str(), 1);
        LOGD("Set PATH=%s", newPath.c_str());

        std::string newLdPath = usrLibPath_ + ":/system/lib64:/vendor/lib64";
        setenv("LD_LIBRARY_PATH", newLdPath.c_str(), 1);
        LOGD("Set LD_LIBRARY_PATH=%s", newLdPath.c_str());

        std::string home = userspacePath_ + "/home";
        setenv("HOME", home.c_str(), 1);
        LOGD("Set HOME=%s", home.c_str());

        std::string tmpdir = userspacePath_ + "/tmp";
        setenv("TMPDIR", tmpdir.c_str(), 1);
        LOGD("Set TMPDIR=%s", tmpdir.c_str());

        setenv("TERM", "xterm-256color", 1);
    }

    // Apply custom environment variables
    for (const auto& envVar : environmentVariables_) {
        setenv(envVar.name.c_str(), envVar.value.c_str(), 1);
    }
}

void TerminalEngine::restoreEnvironment() {
    // Restore original environment
    for (const auto& envStr : originalEnvVars_) {
        size_t pos = envStr.find('=');
        if (pos != std::string::npos) {
            std::string name = envStr.substr(0, pos);
            std::string value = envStr.substr(pos + 1);
            setenv(name.c_str(), value.c_str(), 1);
        }
    }
    originalEnvVars_.clear();
}

// NEW: Environment enforcement - sets all required env vars before exec
void enforce_environment() {
    setenv("PATH", "/data/user/0/com.tx.terminal/files/usr/bin:/system/bin", 1);
    setenv("LD_LIBRARY_PATH", "/data/user/0/com.tx.terminal/files/usr/lib", 1);
    setenv("HOME", "/data/user/0/com.tx.terminal/files/usr/home", 1);
    setenv("TMPDIR", "/data/user/0/com.tx.terminal/files/usr/tmp", 1);
    setenv("TERM", "xterm-256color", 1);
}

pid_t TerminalEngine::forkAndExecute(const std::string& executable,
                                      const std::vector<std::string>& args,
                                      int slaveFd) {
    pid_t pid = fork();

    if (pid < 0) {
        lastError_ = "Fork failed: " + std::string(strerror(errno));
        LOGE("%s", lastError_.c_str());
        return -1;
    }

    if (pid == 0) {
        // Child process
        close(currentProcess_.masterFd);

        // Create new session
        setsid();

        // Set up PTY slave as controlling terminal
        ioctl(slaveFd, TIOCSCTTY, 0);

        // Redirect stdin, stdout, stderr to PTY slave
        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);

        close(slaveFd);

        // NEW: Enforce environment before any execution
        enforce_environment();

        // Prepare additional environment
        prepareEnvironment();

        // NEW: Parse command and check for userspace binary
        auto cmd_args = split_args(executable);
        if (!cmd_args.empty()) {
            std::string bin = resolve_binary(cmd_args[0]);

            if (!bin.empty()) {
                TX_EXEC("Executing via linker64: %s", bin.c_str());

                std::vector<char*> exec_args;
                exec_args.push_back(const_cast<char*>("linker64"));
                exec_args.push_back(const_cast<char*>(bin.c_str()));

                // Add remaining arguments
                for (size_t i = 1; i < cmd_args.size(); i++) {
                    exec_args.push_back(const_cast<char*>(cmd_args[i].c_str()));
                }

                // Add additional args from the args parameter
                for (const auto& arg : args) {
                    exec_args.push_back(const_cast<char*>(arg.c_str()));
                }

                exec_args.push_back(nullptr);

                execv("/system/bin/linker64", exec_args.data());
                // If execv fails, fall through to fallback
            }
        }

        // FALLBACK: Standard execution
        // Build argument array
        std::vector<char*> argv;
        argv.push_back(const_cast<char*>(executable.c_str()));
        for (const auto& arg : args) {
            argv.push_back(const_cast<char*>(arg.c_str()));
        }
        argv.push_back(nullptr);

        // Execute
        execvp(executable.c_str(), argv.data());

        // ULTIMATE FALLBACK: If we get here, exec failed - try system shell
        LOGE("execvp failed: %s", strerror(errno));
        TX_EXEC("Falling back to /system/bin/sh");
        execl("/system/bin/sh", "sh", nullptr);

        // If even that fails
        _exit(127);
    }

    // Parent process
    close(slaveFd);
    return pid;
}

std::string TerminalEngine::resolveCommandPath(const std::string& command) const {
    // Check if it's an absolute path
    if (command[0] == '/') {
        return command;
    }

    // Check if it's a relative path
    if (command.find('/') != std::string::npos) {
        return command;
    }

    // Search in PATH
    if (userspaceEnabled_ && !usrBinPath_.empty()) {
        std::string fullPath = usrBinPath_ + "/" + command;
        if (executableExists(fullPath)) {
            return fullPath;
        }
    }

    // Return as-is for system search
    return command;
}

bool TerminalEngine::executableExists(const std::string& path) const {
    return access(path.c_str(), X_OK) == 0;
}

bool TerminalEngine::executeCommand(const std::string& command) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (command.empty()) {
        return false;
    }

    // Kill any existing process
    if (currentProcess_.isRunning) {
        killProcess(9);
    }

    // Parse command
    std::vector<std::string> tokens;
    std::string current;
    bool inQuote = false;
    char quoteChar = 0;

    for (size_t i = 0; i < command.length(); ++i) {
        char c = command[i];

        if (!inQuote && (c == '"' || c == '\'')) {
            inQuote = true;
            quoteChar = c;
        } else if (inQuote && c == quoteChar) {
            inQuote = false;
            quoteChar = 0;
        } else if (!inQuote && c == ' ') {
            if (!current.empty()) {
                tokens.push_back(current);
                current.clear();
            }
        } else {
            current += c;
        }
    }

    if (!current.empty()) {
        tokens.push_back(current);
    }

    if (tokens.empty()) {
        return false;
    }

    std::string executable = resolveCommandPath(tokens[0]);
    std::vector<std::string> args(tokens.begin() + 1, tokens.end());

    LOGD("Executing: %s", executable.c_str());
    TX_EXEC("Command: %s", command.c_str());

    // Create PTY
    int masterFd, slaveFd;
    if (!createPty(masterFd, slaveFd)) {
        return false;
    }

    if (!setupPtySlave(slaveFd)) {
        close(masterFd);
        close(slaveFd);
        return false;
    }

    currentProcess_.masterFd = masterFd;

    // Fork and execute
    pid_t pid = forkAndExecute(executable, args, slaveFd);
    if (pid < 0) {
        close(masterFd);
        return false;
    }

    currentProcess_.pid = pid;
    currentProcess_.isRunning = true;
    currentProcess_.executable = executable;
    currentProcess_.args = args;

    // Start output reading thread
    shouldStopReading_ = false;
    outputThread_ = std::thread(&TerminalEngine::readOutputLoop, this);

    LOGD("Process started with PID: %d", pid);
    return true;
}

bool TerminalEngine::executeWithLinker(const std::string& linker,
                                        const std::string& executable,
                                        const std::vector<std::string>& args) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (currentProcess_.isRunning) {
        killProcess(9);
    }

    // Create PTY
    int masterFd, slaveFd;
    if (!createPty(masterFd, slaveFd)) {
        return false;
    }

    if (!setupPtySlave(slaveFd)) {
        close(masterFd);
        close(slaveFd);
        return false;
    }

    currentProcess_.masterFd = masterFd;

    pid_t pid = fork();

    if (pid < 0) {
        lastError_ = "Fork failed: " + std::string(strerror(errno));
        LOGE("%s", lastError_.c_str());
        return false;
    }

    if (pid == 0) {
        // Child process
        close(masterFd);
        setsid();
        ioctl(slaveFd, TIOCSCTTY, 0);

        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        close(slaveFd);

        // NEW: Enforce environment
        enforce_environment();
        prepareEnvironment();

        // Build argument array for linker execution
        std::vector<char*> argv;
        argv.push_back(const_cast<char*>(linker.c_str()));
        argv.push_back(const_cast<char*>(executable.c_str()));
        for (const auto& arg : args) {
            argv.push_back(const_cast<char*>(arg.c_str()));
        }
        argv.push_back(nullptr);

        execvp(linker.c_str(), argv.data());
        LOGE("execvp failed: %s", strerror(errno));
        _exit(127);
    }

    close(slaveFd);
    currentProcess_.pid = pid;
    currentProcess_.isRunning = true;

    shouldStopReading_ = false;
    outputThread_ = std::thread(&TerminalEngine::readOutputLoop, this);

    LOGD("Process started with linker, PID: %d", pid);
    return true;
}

void TerminalEngine::readOutputLoop() {
    char buffer[4096];

    while (!shouldStopReading_) {
        ssize_t bytesRead = read(currentProcess_.masterFd, buffer, sizeof(buffer) - 1);

        if (bytesRead > 0) {
            buffer[bytesRead] = '\0';

            if (outputCallback_) {
                outputCallback_(buffer, bytesRead);
            }
        } else if (bytesRead == 0) {
            // EOF
            break;
        } else {
            if (errno == EAGAIN || errno == EINTR) {
                continue;
            }
            break;
        }
    }

    // Check if process has exited
    int status;
    pid_t result = waitpid(currentProcess_.pid, &status, WNOHANG);
    if (result == currentProcess_.pid || result < 0) {
        currentProcess_.isRunning = false;
    }
}

bool TerminalEngine::sendInput(const std::string& input) {
    return sendRawData(input.c_str(), input.length());
}

bool TerminalEngine::sendRawData(const char* data, size_t length) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!currentProcess_.isRunning || currentProcess_.masterFd < 0) {
        return false;
    }

    ssize_t written = write(currentProcess_.masterFd, data, length);
    return written == static_cast<ssize_t>(length);
}

bool TerminalEngine::resizeTerminal(int rows, int cols) {
    std::lock_guard<std::mutex> lock(mutex_);

    savedRows_ = rows;
    savedCols_ = cols;

    if (currentProcess_.masterFd < 0) {
        return false;
    }

    struct winsize ws;
    ws.ws_row = rows;
    ws.ws_col = cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    return ioctl(currentProcess_.masterFd, TIOCSWINSZ, &ws) == 0;
}

bool TerminalEngine::isProcessRunning() const {
    return currentProcess_.isRunning;
}

pid_t TerminalEngine::getCurrentPid() const {
    return currentProcess_.pid;
}

bool TerminalEngine::killProcess(int signal) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!currentProcess_.isRunning || currentProcess_.pid < 0) {
        return false;
    }

    LOGD("Killing process %d with signal %d", currentProcess_.pid, signal);

    int result = kill(currentProcess_.pid, signal);

    if (signal == 9 || signal == 15) {
        shouldStopReading_ = true;

        if (outputThread_.joinable()) {
            outputThread_.join();
        }

        if (currentProcess_.masterFd >= 0) {
            close(currentProcess_.masterFd);
            currentProcess_.masterFd = -1;
        }

        currentProcess_.isRunning = false;
        currentProcess_.pid = -1;
    }

    return result == 0;
}

void TerminalEngine::setEnvironmentVariables(const std::vector<EnvVar>& envVars) {
    std::lock_guard<std::mutex> lock(mutex_);
    environmentVariables_ = envVars;
}

void TerminalEngine::addEnvironmentVariable(const std::string& name, const std::string& value) {
    std::lock_guard<std::mutex> lock(mutex_);
    environmentVariables_.push_back({name, value});
}

void TerminalEngine::clearEnvironmentVariables() {
    std::lock_guard<std::mutex> lock(mutex_);
    environmentVariables_.clear();
}

void TerminalEngine::setUserspaceEnabled(bool enabled) {
    userspaceEnabled_ = enabled;
}

bool TerminalEngine::isUserspaceEnabled() const {
    return userspaceEnabled_;
}

std::string TerminalEngine::getLastError() const {
    return lastError_;
}

void TerminalEngine::cleanup() {
    if (currentProcess_.isRunning) {
        killProcess(9);
    }

    shouldStopReading_ = true;

    if (outputThread_.joinable()) {
        outputThread_.join();
    }

    if (currentProcess_.masterFd >= 0) {
        close(currentProcess_.masterFd);
        currentProcess_.masterFd = -1;
    }
}

} // namespace tx
