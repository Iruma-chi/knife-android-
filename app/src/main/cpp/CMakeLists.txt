cmake_minimum_required(VERSION 3.22.1)
project(tsukiport_native_importer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(nximport SHARED
    nximport/nximport_jni.cpp
    nximport/nximport.cpp
    nximport/nxcrypto.cpp
)

target_include_directories(nximport PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/nximport"
)

target_compile_definitions(nximport PRIVATE
    NXI_JNI_PKG=com_dev_tsukihimeandroid
)

target_compile_options(nximport PRIVATE
    -O2
    -fvisibility=hidden
    -fexceptions
    -frtti
    -Wall
    -Wextra
    -Wpedantic
)

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_options(nximport PRIVATE -march=armv8-a+crypto)
    target_compile_definitions(nximport PRIVATE __BIONIC_NO_PAGE_SIZE_MACRO)
    target_link_options(nximport PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
    )
endif()

find_library(ANDROID_LOG_LIB log REQUIRED)
target_link_libraries(nximport PRIVATE ${ANDROID_LOG_LIB})

# The package reader's C++ implementation and C ABI are compiled into libmain.so
# by engine/knife/build.rs. Keep the Android JNI shim in a separate library that
# depends one-way on libmain.so; never link libmain.so back to libnximport.so.
if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(KNIFE_ENGINE_LIB "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmain.so")
    if(NOT EXISTS "${KNIFE_ENGINE_LIB}")
        message(FATAL_ERROR "Knife engine library missing: ${KNIFE_ENGINE_LIB}")
    endif()
    add_library(packagejni SHARED nximport/package_jni.cpp)
    target_compile_options(packagejni PRIVATE
        -O2 -march=armv8-a+crypto -fvisibility=hidden -fexceptions -frtti
        -Wall -Wextra -Wpedantic
    )
    target_link_options(packagejni PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
    )
    target_link_libraries(packagejni PRIVATE "${KNIFE_ENGINE_LIB}")
endif()
