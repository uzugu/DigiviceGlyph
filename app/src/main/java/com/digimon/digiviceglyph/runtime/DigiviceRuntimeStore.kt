package com.digimon.digiviceglyph.runtime

import android.content.Context

object DigiviceRuntimeStore {
    @Volatile
    private var runtime: DigiviceV1Runtime? = null

    fun get(context: Context): DigiviceV1Runtime {
        val existing = runtime
        if (existing != null) return existing
        return synchronized(this) {
            runtime ?: DigiviceV1Runtime(context.applicationContext).also { runtime = it }
        }
    }
}
