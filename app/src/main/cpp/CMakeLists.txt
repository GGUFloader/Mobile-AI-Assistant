cmake_minimum_required(VERSION 3.22.1)

project("localchatbot")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add llama.cpp source directory
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)
set(GGML_DIR ${LLAMA_DIR}/ggml)

# Include headers
include_directories(${LLAMA_DIR}/include)
include_directories(${LLAMA_DIR}/src)
include_directories(${LLAMA_DIR}/common)
include_directories(${GGML_DIR}/include)
include_directories(${GGML_DIR}/src)
include_directories(${GGML_DIR}/src/ggml-cpu)

# Create the JNI library (placeholder - doesn't link llama.cpp yet)
# The current llama-android.cpp is a stub that doesn't use llama.cpp
add_library(llama-android SHARED
    llama-android.cpp
)

target_link_libraries(llama-android
    android
    log
)
