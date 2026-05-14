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
# Enable LLAMAFILE optimizations for ARM processors (improves inference speed)
set(GGML_CPU_LLAMAFILE ON CACHE BOOL "" FORCE)
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
# ARM architecture notes for Snapdragon 778G (Cortex-A78 + Cortex-A55):
# - Cannot use GGML_CPU_ARM_ARCH=armv8.2-a+dotprod because it compiles dotprod
#   instructions into ALL ggml-cpu code, causing SIGILL on Cortex-A55 (no dotprod).
# - Cannot use GGML_NATIVE because it requires running test programs on the target
#   device (not possible in CI cross-compilation).
# - The safe approach: let ggml-cpu use default ARMv8-A (NEON only, safe on all cores).
#   The LLAMAFILE sggemm code uses NEON intrinsics which are fast and safe.
# - GGML_CPU_LLAMAFILE=ON is already set above, enabling the optimized sgemm.cpp
#   which uses ARM NEON for matrix multiplication (safe on both A78 and A55).
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
