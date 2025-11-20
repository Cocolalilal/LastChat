#!/bin/bash

# 检查并更新子模块
echo "Checking submodules..."
if [ ! -f "mnn/CMakeLists.txt" ]; then
    echo "Error: MNN submodule not found. Please initialize and update submodules:"
    echo "git submodule update --init --recursive"
    exit 1
fi

# 确保子模块是最新的
git submodule update --recursive

# 通用编译参数
COMMON_FLAGS="-DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true -DMNN_USE_LOGCAT=true -DMNN_OPENCL=true -DLLM_SUPPORT_VISION=true -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DLLM_SUPPORT_AUDIO=true -DMNN_BUILD_AUDIO=true -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384'"

cd mnn/project/android

# 构建 arm64-v8a
echo "Building for arm64-v8a..."
sleep 3
rm -rf build_64
mkdir -p build_64
cd build_64
../build_64.sh "$COMMON_FLAGS -DMNN_ARM82=true -DCMAKE_INSTALL_PREFIX=."
make install
cd ..

# 构建 x86_64
echo "Building for x86_64..."
sleep 3
rm -rf build_x86_64
mkdir -p build_x86_64
cd build_x86_64
cmake ../../../ \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DCMAKE_BUILD_TYPE=Release \
  -DANDROID_ABI="x86_64" \
  -DANDROID_STL=c++_static \
  -DANDROID_NATIVE_API_LEVEL=android-21 \
  -DANDROID_TOOLCHAIN=clang \
  -DMNN_USE_SSE=ON \
  -DMNN_ARM82=false \
  -DMNN_BUILD_FOR_ANDROID_COMMAND=ON \
  $COMMON_FLAGS \
  -DCMAKE_INSTALL_PREFIX=.
make install
