// Created by ruoyi.sjd on 2025/5/7.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package me.rerere.ai.mnn

interface GenerateProgressListener {
    fun onProgress(progress: String?): Boolean
}
