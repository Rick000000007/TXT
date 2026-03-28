#ifndef TERMINAL_ENGINE_H
#define TERMINAL_ENGINE_H

#include <string>
#include <vector>
#include <functional>
#include <thread>
#include <atomic>
#include <mutex>

namespace tx {

// Callback type for terminal output
using OutputCallback = std::function<void(const char* data, size_t length)>;

// Process information structure
struct ProcessInfo {
    pid_t pid;
    int masterFd;
    bool isRunning;
    std::string executable;
    std::vector<std::string> args;
};

// Environment variable
struct EnvVar {
    std::string name;
    std::string value;
};

class TerminalEngine {
public:
    TerminalEngine();
    ~TerminalEngine();

    // Initialize the terminal engine
    bool initialize();

    // Set userspace path
    void setUserspacePath(const std::string& path);

    // Set output callback
    void setOutputCallback(OutputCallback callback);

    // Execute a command
    bool executeCommand(const std::string& command);

    // Execute with custom linker
    bool executeWithLinker(const std::string& linker, const std::string& executable,
                          const std::vector<std::string>& args);

    // Send input to the running process
    bool sendInput(const std::string& input);

    // Send raw data to the running process
    bool sendRawData(const char* data, size_t length);

    // Resize the terminal
    bool resizeTerminal(int rows, int cols);

    // Check if a process is running
    bool isProcessRunning() const;

    // Get the current process PID
    pid_t getCurrentPid() const;

    // Kill the current process
    bool killProcess(int signal = 15);

    // Set environment variables
    void setEnvironmentVariables(const std::vector<EnvVar>& envVars);

    // Add a single environment variable
    void addEnvironmentVariable(const std::string& name, const std::string& value);

    // Clear environment variables
    void clearEnvironmentVariables();

    // Enable/disable userspace mode
    void setUserspaceEnabled(bool enabled);

    // Check if userspace is enabled
    bool isUserspaceEnabled() const;

    // Get the last error message
    std::string getLastError() const;

    // Cleanup resources
    void cleanup();

private:
    // Create a PTY pair
    bool createPty(int& masterFd, int& slaveFd);

    // Setup the PTY slave
    bool setupPtySlave(int slaveFd);

    // Fork and execute process
    pid_t forkAndExecute(const std::string& executable,
                         const std::vector<std::string>& args,
                         int slaveFd);

    // Prepare environment for execution
    void prepareEnvironment();

    // Restore environment after execution
    void restoreEnvironment();

    // Read output from the master FD
    void readOutputLoop();

    // Find linker path based on architecture
    std::string findLinkerPath() const;

    // Check if executable exists
    bool executableExists(const std::string& path) const;

    // Build full command path
    std::string resolveCommandPath(const std::string& command) const;

private:
    std::string userspacePath_;
    std::string usrBinPath_;
    std::string usrLibPath_;
    OutputCallback outputCallback_;
    ProcessInfo currentProcess_;
    std::vector<EnvVar> environmentVariables_;
    std::vector<std::string> originalEnvVars_;
    std::thread outputThread_;
    std::atomic<bool> shouldStopReading_;
    std::atomic<bool> userspaceEnabled_;
    mutable std::mutex mutex_;
    std::string lastError_;
    int savedRows_;
    int savedCols_;
};

} // namespace tx

#endif // TERMINAL_ENGINE_H
