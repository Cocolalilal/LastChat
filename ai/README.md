# 环境配置

## 准备环境

- 安装CMake
- 安装NDK，并配置`ANDROID_NDK`环境变量

## git submodule

在项目根目录执行以下命令初始化子模块：

```bash
git submodule update --init --recursive
```

注意：必须在git仓库的根目录（`rikkahub/`）执行此命令，不是在 `src/main/cpp/mnn` 目录。

## 构建libMNN.so

进入 `src/main/cpp/mnn` 目录，执行以下命令：

```bash
./build.sh
```
