cmake_minimum_required(VERSION 3.22.1)
project("mnnllmapp")

# Enable ccache
find_program(CCACHE_PROGRAM ccache)
if (CCACHE_PROGRAM)
    message(STATUS "Using ccache: ${CCACHE_PROGRAM}")
    set(CMAKE_C_COMPILER_LAUNCHER ${CCACHE_PROGRAM})
    set(CMAKE_CXX_COMPILER_LAUNCHER ${CCACHE_PROGRAM})
endif ()

add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        llm_mnn_jni.cpp
        diffusion_jni.cpp
        diffusion_session.cpp
        llm_session.cpp
        crash_util.cpp
)

# Add 16KB page size support (required for Android 15+ devices)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

#mnn
set(MNN_SOURCE_ROOT "${CMAKE_SOURCE_DIR}/mnn")
#set (MNN_SOURCE_ROOT "/Users/songjinde/git/DAI/AliNNPrivate")

# Set MNN install root based on ABI
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(MNN_INSTALL_ROOT "${MNN_SOURCE_ROOT}/project/android/build_64")
elseif (ANDROID_ABI STREQUAL "x86_64")
    set(MNN_INSTALL_ROOT "${MNN_SOURCE_ROOT}/project/android/build_x86_64")
else ()
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
endif ()

#set (MNN_ROOT "${CMAKE_SOURCE_DIR}/../../../../../../../")
message(INFO "MNN_SOURCE_ROOT: ${MNN_SOURCE_ROOT}")
message(INFO "MNN_INSTALL_ROOT: ${MNN_INSTALL_ROOT}")
message("ABI: ${ANDROID_ABI}")

include_directories("${MNN_SOURCE_ROOT}/include/")

#diffusion
include_directories("${MNN_SOURCE_ROOT}/transformers/diffusion/engine/include/")

#llm
include_directories("${MNN_SOURCE_ROOT}/transformers/llm/engine/include")
#include_directories("${CMAKE_SOURCE_DIR}/include")

#audio
include_directories("${MNN_SOURCE_ROOT}/tools/audio/include")
include_directories("${CMAKE_SOURCE_DIR}/third_party")
# Add library paths
set(LIB_PATH "${MNN_INSTALL_ROOT}/lib")

# Link the shared library

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libMNN.so")

#add_library(llm SHARED IMPORTED)
#set_target_properties(llm PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libllm.so")
#
#add_library(diffusion SHARED IMPORTED)
#set_target_properties(diffusion PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libdiffusion.so")
#
#add_library(mnn_cl SHARED IMPORTED)
#set_target_properties(mnn_cl PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libMNN_CL.so")
#
#add_library(mnn_audio SHARED IMPORTED)
#set_target_properties(mnn_audio PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libMNNAudio.so")
#
#add_library(mnn_cv SHARED IMPORTED)
#set_target_properties(mnn_cv PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libMNNOpenCV.so")
#
#add_library(mnn_Express SHARED IMPORTED)
#set_target_properties(mnn_Express PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libMNN_Express.so")


# Link your native library with the `.so` file

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log
        MNN
)
