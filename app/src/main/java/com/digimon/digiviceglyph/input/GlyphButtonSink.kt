package com.digimon.digiviceglyph.input

interface GlyphButtonSink {
    fun onButtonDown(button: GlyphButton)
    fun onButtonUp(button: GlyphButton)
    fun triggerStep()
    fun motionInputMode(): GlyphMotionMode = GlyphMotionMode.DEFAULT
    fun acceptsPassiveWalking(): Boolean = false
    fun requiresIdleControlWake(): Boolean = false
}
