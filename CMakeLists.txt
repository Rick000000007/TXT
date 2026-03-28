cmake_minimum_required(VERSION 3.22.1)
project("txterminal" LANGUAGES CXX C)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O2 -fPIC")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -O2 -fPIC")

# Android specific settings
if(ANDROID)
    # Find the Android NDK libraries
    find_library(log-lib log)
    
    # Include directories
    include_directories(
        ${CMAKE_SOURCE_DIR}/android/app/src/main/cpp
    )
    
    # Source files for the main terminal library
    set(TERMINAL_SOURCES
        android/app/src/main/cpp/terminal_engine.cpp
        android/app/src/main/cpp/jni_bridge.cpp
    )
    
    # Create the shared library
    add_library(txterminal SHARED ${TERMINAL_SOURCES})
    
    # Link libraries
    target_link_libraries(txterminal
        ${log-lib}
        c++_shared
    )
    
    # Set target properties
    set_target_properties(txterminal PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        POSITION_INDEPENDENT_CODE ON
    )
    
    # Strip debug symbols in release builds
    if(CMAKE_BUILD_TYPE STREQUAL "Release")
        set_target_properties(txterminal PROPERTIES
            CXX_VISIBILITY_PRESET hidden
            VISIBILITY_INLINES_HIDDEN ON
        )
    endif()
endif()

# Print configuration info
message(STATUS "CMAKE_SYSTEM_NAME: ${CMAKE_SYSTEM_NAME}")
message(STATUS "CMAKE_ANDROID_ARCH_ABI: ${CMAKE_ANDROID_ARCH_ABI}")
message(STATUS "CMAKE_CXX_STANDARD: ${CMAKE_CXX_STANDARD}")
