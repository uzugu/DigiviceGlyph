package com.digimon.digiviceglyph.input

interface GlyphButtonSink {
    fun onButtonDown(button: GlyphButton)
    fun onButtonUp(button: GlyphButton)
}
