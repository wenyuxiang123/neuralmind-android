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
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Fetch llama.cpp from GitHub release archive (much faster than git clone)
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
