package com.aikit.sample

import platform.UIKit.UIDevice

private class IosPlatform : Platform {
    override val name: String =
        "${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"
}

actual fun platform(): Platform = IosPlatform()
