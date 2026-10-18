package com.portalcast.tv.data.net

import android.os.Build
import com.portalcast.tv.BuildConfig

/**
 * Sent on every HTTP request and by ExoPlayer. MUST NOT contain `Tizen`, `SMART-TV`,
 * `SmartTV`, `Web0S` or `WebOS`: the server treats those as browser-less smart TVs
 * and stops honouring `proxy=1` on live streams (contract §1).
 */
object UserAgent {
    val value: String = "PortalcastTV/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT}; ${Build.MODEL ?: "device"})"
        .replace(Regex("Tizen|SMART-TV|SmartTV|Web0S|WebOS", RegexOption.IGNORE_CASE), "TV")
}
