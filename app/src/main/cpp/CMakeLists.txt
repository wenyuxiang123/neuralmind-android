cmake_minimum_required(VERSION 3.22.1)

project("neuralmind")

// For now, just create a simple library without llama.cpp
// We'll add llama.cpp later when needed
add_library(neuralmind-jni SHARED
    llama-jni.cpp
)

target_include_directories(neuralmind-jni PRIVATE
    ${CMAKE_SOURCE_DIR}
)

target_link_libraries(neuralmind-jni
    android
    log
)
