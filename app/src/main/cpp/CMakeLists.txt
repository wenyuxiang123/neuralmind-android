cmake_minimum_required(VERSION 3.22.1)

project("neuralmind")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Disable everything in llama.cpp we don't need
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENBLAS OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_CPU_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Architecture-specific settings for 32-bit ARM compatibility
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    # Disable AArch64-only optimizations on 32-bit ARM
    set(GGML_CPU_AARCH64 OFF CACHE BOOL "" FORCE)
    # Disable FP16 vector arguments which use NEON instructions not available on armeabi-v7a
    set(GGML_FP16_VEC_ARG OFF CACHE BOOL "" FORCE)
    # Ensure we use IEEE format for half precision
    add_compile_options(-mfp16-format=ieee)
endif()

# Fetch llama.cpp from GitHub release archive
include(FetchContent)
FetchContent_Declare(
    llama_cpp
    URL      https://github.com/ggml-org/llama.cpp/archive/refs/tags/b9128.tar.gz
    SOURCE_SUBDIR .
)

FetchContent_MakeAvailable(llama_cpp)

# Create shared library for JNI
add_library(neuralmind-jni SHARED llama-jni.cpp)

# Include directories
target_include_directories(neuralmind-jni PRIVATE 
    ${CMAKE_SOURCE_DIR}
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
    ${llama_cpp_SOURCE_DIR}/ggml/src
)

# Link libraries
target_link_libraries(neuralmind-jni 
    llama
    ggml
    android
    log
)
