cmake_minimum_required(VERSION 3.22.1)

project("localchatbot")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure llama.cpp build options before adding subdirectory
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build common utils library" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Build tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Use libcurl" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libs" FORCE)

# Build llama.cpp as a subdirectory
add_subdirectory(llama.cpp build-llama)

# Create the JNI library
add_library(llama-android SHARED
    llama-android.cpp
)

# Link against llama.cpp libraries
target_link_libraries(llama-android
    llama
    common
    android
    log
)
