cmake_minimum_required(VERSION 3.22.1)
project("reins-whisper")

# Own, isolated toolchain — not shared with :mosh's CMake config (ticket 012).
#
# Real whisper.cpp integration (ticket 024), adapted from whisper.cpp's official
# examples/whisper.android (lib/src/main/jni/whisper/CMakeLists.txt): builds
# src/whisper.cpp plus vendored ggml (whisper-cpp/ggml, CPU backend only) straight
# into this module's own .so, same as upstream does. See VENDORED.md for exactly
# what was copied in and why.
set(CMAKE_CXX_STANDARD 17)
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/whisper-cpp)
set(WHISPER_VERSION "1.7.6") # matches the vendored whisper.cpp checkout; see VENDORED.md

set(
        SOURCE_FILES
        ${WHISPER_LIB_DIR}/src/whisper.cpp
        whisper_jni.cpp
)

find_library(log-lib log)

include(FetchContent)

function(build_library target_name)
    add_library(${target_name} SHARED ${SOURCE_FILES})

    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
    target_compile_definitions(${target_name} PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

    if (${target_name} STREQUAL "reins_whisper_v8fp16_va")
        target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
        set(GGML_COMPILE_OPTIONS -march=armv8.2-a+fp16)
    elseif (${target_name} STREQUAL "reins_whisper_vfpv4")
        target_compile_options(${target_name} PRIVATE -mfpu=neon-vfpv4)
        set(GGML_COMPILE_OPTIONS -mfpu=neon-vfpv4)
    endif ()

    if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
        target_compile_options(${target_name} PRIVATE -O3)
        target_compile_options(${target_name} PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections)

        target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
        target_link_options(${target_name} PRIVATE -Wl,--exclude-libs,ALL)
        target_link_options(${target_name} PRIVATE -flto)
    endif ()

    FetchContent_Declare(reins_ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
    FetchContent_MakeAvailable(reins_ggml)
    target_compile_options(ggml PRIVATE ${GGML_COMPILE_OPTIONS})
    target_link_libraries(${target_name} ${log-lib} android ggml)
endfunction()

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_library("reins_whisper_v8fp16_va")
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    build_library("reins_whisper_vfpv4")
endif ()

build_library("reins-whisper") # Default/fallback target, always built

include_directories(${WHISPER_LIB_DIR})
include_directories(${WHISPER_LIB_DIR}/src)
include_directories(${WHISPER_LIB_DIR}/include)
include_directories(${WHISPER_LIB_DIR}/ggml/include)
include_directories(${WHISPER_LIB_DIR}/ggml/src)
include_directories(${WHISPER_LIB_DIR}/ggml/src/ggml-cpu)
