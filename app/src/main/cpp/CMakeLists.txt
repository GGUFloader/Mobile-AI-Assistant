cmake_minimum_required(VERSION 3.22.1)

project("localchatbot")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Performance optimizations for ARM64
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -fno-finite-math-only")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math -fno-finite-math-only")

# Enable ARM NEON SIMD optimizations (critical for mobile performance)
set(GGML_NEON ON CACHE BOOL "Enable ARM NEON" FORCE)

# Disable Vulkan (requires complex setup)
set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan" FORCE)

# Configure llama.cpp build options before adding subdirectory
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build common utils library" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Build tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Use libcurl" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libs" FORCE)

# Disable features that slow down mobile inference
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "Disable Accelerate" FORCE)

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
