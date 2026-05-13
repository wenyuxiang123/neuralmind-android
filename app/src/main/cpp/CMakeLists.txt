cmake_minimum_required(VERSION 3.22.1)

project("neuralmind")

# Set C++17 standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-rtti -fno-exceptions")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-rtti -fno-exceptions")

# GGML definitions - use CPU only, no SIMD extensions for maximum compatibility
add_definitions(-DGGML_USE_CPU)
add_definitions(-DGGML_NATIVE=0)
add_definitions(-DGGML_NO_SIMD=1)
add_definitions(-DGGML_USE_ACCELERATE=0)
add_definitions(-DGGML_USE_OPENBLAS=0)
add_definitions(-DGGML_USE_CLBLAST=0)
add_definitions(-DGGML_USE_CUBLAS=0)
add_definitions(-DGGML_USE_METAL=0)
add_definitions(-DGGML_USE_VULKAN=0)
add_definitions(-DGGML_USE_K_QUANTS=0)

# Android definitions
add_definitions(-DANDROID)
add_definitions(-D__ANDROID__)
add_definitions(-D__ANDROID_API__=24)

# Logging
add_definitions(-DGGML_STATIC)
add_definitions(-DGGML_SHARED)

# Threading
add_definitions(-D_GNU_SOURCE)

# Llama.cpp source files (simplified version for compilation)
# These files provide the API structure for llama.cpp integration
set(LLAMA_SOURCES
    llama/llama.cpp
    llama/ggml.c
    llama/ggml-backend.cpp
    llama/ggml-alloc.c
)

# Create static library for llama.cpp
add_library(llama STATIC ${LLAMA_SOURCES})

# Include directories for llama
target_include_directories(llama PUBLIC 
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
)

# Compiler flags for llama library
target_compile_options(llama PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -fPIC
    -O3
)

# Create shared library for JNI
add_library(neuralmind-jni SHARED llama-jni.cpp)

# Include directories for JNI library
target_include_directories(neuralmind-jni PRIVATE 
    ${CMAKE_SOURCE_DIR}
)

# Link libraries
target_link_libraries(neuralmind-jni 
    llama
    android
    log
)

# Export compile commands for debugging
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Print configuration
message(STATUS "=== Llama.cpp Android Build Configuration ===")
message(STATUS "CMAKE_SYSTEM_PROCESSOR: ${CMAKE_SYSTEM_PROCESSOR}")
message(STATUS "ANDROID_ABI: ${ANDROID_ABI}")
message(STATUS "CMAKE_BUILD_TYPE: ${CMAKE_BUILD_TYPE}")
message(STATUS "GGML_USE_CPU: enabled")
message(STATUS "===========================================")
