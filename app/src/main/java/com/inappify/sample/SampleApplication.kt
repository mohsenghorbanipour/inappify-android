package com.inappify.sample

import android.app.Application
import com.inappify.sdk.InappifyClient

/** Owns one SDK client for the lifetime of the sample application process. */
class SampleApplication : Application() {

    val inappifyClient: InappifyClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        InappifyClient.create(this)
    }
}
