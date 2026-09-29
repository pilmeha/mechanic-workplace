package com.example.mechanic.onec

import com.example.mechanic.config.AppConfig

fun createOneCClient(config: AppConfig): OneCClient = when (config.oneCMode) {
    AppConfig.OneCMode.DEMO -> DemoOneCClient()
    AppConfig.OneCMode.HTTP -> HttpOneCClient(config.oneCBaseUrl, config.oneCUsername, config.oneCPassword)
}
