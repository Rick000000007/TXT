/*
 * hello.c - Simple custom binary for TX Terminal
 * Demonstrates userspace binary execution
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define VERSION "1.0.0"

void print_banner(void) {
    printf("\n");
    printf("╔═══════════════════════════════════════╗\n");
    printf("║     TX Terminal - Hello Program       ║\n");
    printf("║           Version %s               ║\n", VERSION);
    printf("╚═══════════════════════════════════════╝\n");
    printf("\n");
}

void print_help(void) {
    printf("Usage: hello [OPTIONS]\n");
    printf("\n");
    printf("Options:\n");
    printf("  -h, --help       Show this help message\n");
    printf("  -v, --version    Show version information\n");
    printf("  -n, --name NAME  Greet by name\n");
    printf("  -e, --env        Show environment variables\n");
    printf("\n");
    printf("Examples:\n");
    printf("  hello            Print default greeting\n");
    printf("  hello -n John    Greet John\n");
    printf("  hello --env      Show environment info\n");
    printf("\n");
}

void print_version(void) {
    printf("hello version %s\n", VERSION);
    printf("TX Terminal Userspace Binary\n");
    printf("\n");
}

void print_environment(void) {
    extern char **environ;
    
    printf("Environment Variables:\n");
    printf("─────────────────────────────────────────\n");
    
    char **env = environ;
    while (*env) {
        printf("  %s\n", *env);
        env++;
    }
    
    printf("\n");
    printf("Special Variables:\n");
    printf("─────────────────────────────────────────\n");
    printf("  PID:  %d\n", getpid());
    printf("  PPID: %d\n", getppid());
    printf("  UID:  %d\n", getuid());
    printf("  GID:  %d\n", getgid());
    printf("\n");
}

void greet(const char *name) {
    printf("Hello, %s! 👋\n", name);
    printf("Welcome to TX Terminal!\n");
    printf("\n");
    printf("Current time: ");
    fflush(stdout);
    system("date");
    printf("\n");
}

int main(int argc, char *argv[]) {
    const char *name = "World";
    int show_env = 0;
    
    // Parse arguments
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            print_banner();
            print_help();
            return 0;
        }
        else if (strcmp(argv[i], "-v") == 0 || strcmp(argv[i], "--version") == 0) {
            print_version();
            return 0;
        }
        else if ((strcmp(argv[i], "-n") == 0 || strcmp(argv[i], "--name") == 0) && i + 1 < argc) {
            name = argv[++i];
        }
        else if (strcmp(argv[i], "-e") == 0 || strcmp(argv[i], "--env") == 0) {
            show_env = 1;
        }
        else if (argv[i][0] != '-') {
            name = argv[i];
        }
    }
    
    print_banner();
    
    if (show_env) {
        print_environment();
    }
    
    greet(name);
    
    printf("💡 Try running 'setup-tx-storage' to set up storage integration!\n");
    printf("\n");
    
    return 0;
}
