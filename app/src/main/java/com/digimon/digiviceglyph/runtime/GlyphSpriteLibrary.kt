package com.digimon.digiviceglyph.runtime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class GlyphSpriteLibrary(context: Context) {
    private val assets = context.assets
    private val cache = mutableMapOf<String, List<Bitmap>>()

    fun getFrame(name: String, frameIndex: Int = 0): Bitmap? {
        val frames = cache.getOrPut(name) { loadFrames(name) }
        if (frames.isEmpty()) return null
        return frames[frameIndex.mod(frames.size)]
    }

    private fun loadFrames(name: String): List<Bitmap> {
        val dir = "sprites/$name"
        val files = assets.list(dir)?.sortedBy(::frameSortKey).orEmpty()
        if (files.isEmpty()) return emptyList()
        return buildList {
            for (file in files) {
                val path = "$dir/$file"
                val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    add(bitmap)
                }
            }
        }
    }

    private fun frameSortKey(fileName: String): Int {
        return Regex("_(\\d+)\\.png$", RegexOption.IGNORE_CASE)
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }
}
