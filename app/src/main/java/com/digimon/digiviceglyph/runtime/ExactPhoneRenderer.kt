package com.digimon.digiviceglyph.runtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import java.util.Calendar
import kotlin.math.roundToInt

data class PhoneBattleSnapshot(
    val enemyId: Int,
    val enemyName: String,
    val boss: Boolean,
    val mineHp: Int,
    val enemyHp: Int,
    val currentEvo: Int,
    val turn: Int,
    val phase: String,
    val phaseTicks: Int,
    val menuIndex: Int,
    val pushPress: Int,
    val evoCharge: Int,
    val swapIndex: Int,
    val evoSuccess: Boolean,
    val resultText: String,
    val readyGoFrame: Int,
    val evoMenu: Int,
    val evoPosY: Int,
    val evoAnimation: Boolean,
    val finishEvo: Int,
    val finishFilter: Boolean,
    val finishSlideX: Int,
    val resultAnimation: Boolean,
    val attackTurn: Int,
    val attackPosX: Int,
    val attackAnimation: Boolean,
    val startBlinkVisible: Boolean,
    val startSlideX: Int,
    val startSlideY: Int
)

data class PhoneRescueSnapshot(
    val charIndex: Int,
    val charge: Int,
    val phase: Int,
    val visible: Boolean,
    val filter: Int
)

data class PhoneSlotSnapshot(
    val roll1: Int,
    val roll2: Int,
    val phase: String,
    val stop1: Boolean,
    val stop2: Boolean,
    val counter1: Int,
    val counter2: Int,
    val animation: Boolean,
    val resultDistance: Int,
    val resultApplied: Boolean
)

data class PhoneCardSnapshot(
    val roundIndex: Int,
    val totalPointUnits: Int,
    val roll1: Int,
    val roll2: Int,
    val press1: Boolean,
    val press2: Boolean,
    val counter: Int,
    val phase: String,
    val animation: Boolean,
    val roundPointUnits: Int,
    val resultDistance: Int,
    val resultApplied: Boolean
)

data class PhoneVisualSnapshot(
    val screen: String,
    val idleClockEnabled: Boolean,
    val currentChar: Int,
    val selectedChar: Int,
    val menuIndex: Int,
    val bootStage: Int,
    val bootFrame: Int,
    val bootVisible: Boolean,
    val bootSlideX: Int,
    val distance: Int,
    val steps: Int,
    val dpower: Int,
    val wins: Int,
    val battles: Int,
    val area: Int,
    val mapPreviewArea: Int,
    val mapBlinkVisible: Boolean,
    val mapTargetDistance: Int,
    val completedAreas: IntArray,
    val finishOffset: Int,
    val finishAnimFrame: Int,
    val statusPage: Int,
    val statusMode: Int,
    val statusDetailPage: Int,
    val statusBarFrame: Int,
    val autorun: Boolean,
    val stepActive: Boolean,
    val happy: Boolean,
    val happyAnimation: Boolean,
    val defeat: Boolean,
    val battlePending: Boolean,
    val lastEncounterType: String,
    val finishReturnX: Int,
    val slot: PhoneSlotSnapshot?,
    val card: PhoneCardSnapshot?,
    val battle: PhoneBattleSnapshot?,
    val rescue: PhoneRescueSnapshot?
)

class ExactPhoneRenderer(
    private val spriteLibrary: GlyphSpriteLibrary
) {
    companion object {
        private const val PHONE_WIDTH = 160
        private const val PHONE_HEIGHT = 160
        private const val CONTENT_WIDTH = 32
        private const val CONTENT_HEIGHT = 16
        private const val GLYPH_SIZE = 25
        private const val GLYPH_SPRITE_LUMA_THRESHOLD = 160
        private const val GLYPH_CROP_LEFT = (CONTENT_WIDTH - GLYPH_SIZE) / 2
        private const val GLYPH_CONTENT_TOP = 4
        private const val GLYPH_CLOCK_DIGIT_WIDTH = 3
        private const val GLYPH_CLOCK_DIGIT_HEIGHT = 4
        private const val GLYPH_CLOCK_DIGIT_GAP = 1
        private const val GLYPH_CLOCK_TOP = 0
        private const val GLYPH_CLOCK_BOTTOM = 21
        private val GLYPH_CLOCK_DIGITS = arrayOf(
            // 0
            arrayOf("010", "101", "010", "000"),
            // 1
            arrayOf("100", "010", "010", "000"),
            // 2
            arrayOf("110", "010", "011", "000"),
            // 3
            arrayOf("111", "011", "111", "000"),
            // 4
            arrayOf("101", "111", "001", "000"),
            // 5
            arrayOf("011", "010", "110", "000"),
            // 6
            arrayOf("100", "111", "111", "000"),
            // 7
            arrayOf("111", "001", "001", "000"),
            // 8
            arrayOf("011", "111", "110", "000"),
            // 9
            arrayOf("111", "111", "001", "000")
        )

        private val BASE_SPRITES = arrayOf("spr_agu", "spr_gabu", "spr_biyo", "spr_pal", "spr_tento", "spr_goma", "spr_pata", "spr_gato")
        private val ATTACK_SPRITES = arrayOf("spr_agu_attack", "spr_gabu_attack", "spr_biyo_attack", "spr_pal_attack", "spr_tento_attack", "spr_goma_attack", "spr_pata_attack", "spr_gato_attack")
        private val HAPPY_SPRITES = arrayOf("spr_agu_happy", "spr_gabu_happy", "spr_biyo_happy", "spr_pal_happy", "spr_tento_happy", "spr_goma_happy", "spr_pata_happy", "spr_gato_happy")
        private val DEFEAT_SPRITES = arrayOf("spr_agu_defeat", "spr_gabu_defeat", "spr_biyo_defeat", "spr_pal_defeat", "spr_tento_defeat", "spr_goma_defeat", "spr_pata_defeat", "spr_gato_defeat")
        private val STEP_SPRITES = arrayOf("spr_agu_step", "spr_gabu_step", "spr_biyo_step", "spr_pal_step", "spr_tento_step", "spr_goma_step", "spr_pata_step", "spr_gato_step")
        private val SLOT_SPRITES = arrayOf(
            "spr_agu",
            "spr_tyranomon",
            "spr_picodevimon",
            "spr_biyo",
            "spr_gato",
            "spr_scumon",
            "spr_pal",
            "spr_pata",
            "spr_shellmon",
            "spr_gabu",
            "spr_bakemon",
            "spr_numemon",
            "spr_hangyomon",
            "spr_tento",
            "spr_gazimon",
            "spr_goma"
        )
        private val CARD_IDS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, -1, -1, -1, -1, -1, -1, -1, -1)
        private val CARD_SPRITES = arrayOf(
            "spr_agu",
            "spr_gabu",
            "spr_biyo",
            "spr_pal",
            "spr_tento",
            "spr_goma",
            "spr_pata",
            "spr_gato",
            "spr_tyranomon",
            "spr_picodevimon",
            "spr_scumon",
            "spr_shellmon",
            "spr_bakemon",
            "spr_numemon",
            "spr_hangyomon",
            "spr_gazimon"
        )
        private val EVOLUTION_SPRITES = arrayOf(
            arrayOf("spr_agu", "spr_grey", "spr_metalgrey"),
            arrayOf("spr_gabu", "spr_garuru", "spr_weregaruru"),
            arrayOf("spr_biyo", "spr_birdra", "spr_garuda"),
            arrayOf("spr_pal", "spr_toge", "spr_lily"),
            arrayOf("spr_tento", "spr_kabuteri", "spr_megakabuteri"),
            arrayOf("spr_goma", "spr_ikaku", "spr_zudo"),
            arrayOf("spr_pata", "spr_ange", "spr_magnaange"),
            arrayOf("spr_gato", "spr_angewo_d", "spr_magnadra")
        )
        private val ENEMY_SPRITES = arrayOf(
            "spr_scumon", "spr_numemon", "spr_shellmon", "spr_bakemon", "spr_picodevimon",
            "spr_gazimon", "spr_hangyomon", "spr_anomalocarimon", "spr_tyranomon", "spr_phantomon",
            "spr_megadramon_s", "spr_warumonzaemon", "spr_devimon_digivice", "spr_etemon",
            "spr_myotismon", "spr_metalseadramon", "spr_puppetmon", "spr_mugendramon", "spr_piedmon"
        )
        private val MAP_POSITIONS = arrayOf(
            intArrayOf(22, 4),
            intArrayOf(22, 9),
            intArrayOf(14, 9),
            intArrayOf(7, 9),
            intArrayOf(7, 4),
            intArrayOf(13, 4),
            intArrayOf(17, 4)
        )
    }

    private val phoneBitmap = Bitmap.createBitmap(PHONE_WIDTH, PHONE_HEIGHT, Bitmap.Config.ARGB_8888)
    private val phoneCanvas = Canvas(phoneBitmap)
    private val contentBitmap = Bitmap.createBitmap(CONTENT_WIDTH, CONTENT_HEIGHT, Bitmap.Config.ARGB_8888)
    private val contentCanvas = Canvas(contentBitmap)
    private val glyphBitmap = Bitmap.createBitmap(GLYPH_SIZE, GLYPH_SIZE, Bitmap.Config.ARGB_8888)
    private val glyphCanvas = Canvas(glyphBitmap)
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D1611")
        style = Paint.Style.FILL
    }
    private val shellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5D865D")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val lcdFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val lcdFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7F1E4")
        textSize = 10f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7E4D2")
        textSize = 9f
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
        isDither = false
    }
    private val glyphRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private val srcRect = Rect()
    private val dstRect = Rect()
    private var lastLoggedScreen: String? = null
    private var lastLoggedStatusPage = -1
    private var lastLoggedStatusMode = -1

    fun render(snapshot: PhoneVisualSnapshot, frameCounter: Int): Bitmap {
        phoneCanvas.drawColor(Color.parseColor("#11161A"))

        val shellRect = RectF(10f, 6f, 150f, 126f)
        val lcdRect = RectF(18f, 18f, 142f, 94f)
        phoneCanvas.drawRoundRect(shellRect, 12f, 12f, shellPaint)
        phoneCanvas.drawRoundRect(shellRect, 12f, 12f, shellBorderPaint)
        phoneCanvas.drawRoundRect(lcdRect, 6f, 6f, Paint(shellPaint).apply { color = Color.parseColor("#16231D") })

        renderContent(snapshot, frameCounter)

        srcRect.set(0, 0, CONTENT_WIDTH, CONTENT_HEIGHT)
        dstRect.set(lcdRect.left.roundToInt(), lcdRect.top.roundToInt(), lcdRect.right.roundToInt(), lcdRect.bottom.roundToInt())
        phoneCanvas.drawBitmap(contentBitmap, srcRect, dstRect, bitmapPaint)
        phoneCanvas.drawRoundRect(lcdRect, 6f, 6f, shellBorderPaint)
        phoneCanvas.drawText("DIGIVICE V1", PHONE_WIDTH / 2f, 110f, labelPaint)
        phoneCanvas.drawText(hintText(snapshot), 18f, 144f, hintPaint)
        return phoneBitmap
    }

    fun renderContent(snapshot: PhoneVisualSnapshot, frameCounter: Int): Bitmap {
        contentCanvas.drawColor(Color.WHITE)
        when (snapshot.screen) {
            "BOOT" -> drawBoot(snapshot, frameCounter)
            "SELECT" -> drawSelect(snapshot)
            "IDLE" -> drawIdle(snapshot, frameCounter)
            "MENU" -> drawMenu(snapshot)
            "SLOT" -> drawSlot(snapshot)
            "SLOT_RESULT" -> drawSlotResult(snapshot)
            "CARD" -> drawCard(snapshot)
            "CARD_RESULT" -> drawCardResult(snapshot)
            "STATUS" -> drawStatus(snapshot)
            "STATUS_SELECT" -> drawStatusSelect(snapshot)
            "STATUS_MENU" -> drawStatusMenu(snapshot)
            "STATUS_DETAIL" -> drawStatusDetail(snapshot, frameCounter)
            "MAP" -> drawMap(snapshot)
            "MAP_CHANGE" -> drawMapChange(snapshot)
            "FINISH_GAME" -> drawFinishGame(snapshot)
            "FINISH_RETURN" -> drawFinishReturn(snapshot)
            "BATTLE" -> drawBattle(snapshot, frameCounter)
            "RESCUE" -> drawRescue(snapshot)
        }
        return contentBitmap
    }

    fun renderGlyphContent(snapshot: PhoneVisualSnapshot, frameCounter: Int): Bitmap {
        val battle = snapshot.battle
        if (
            snapshot.screen == "BATTLE" &&
            battle != null &&
            battle.phase in setOf("MINE_ATTACK", "ENEMY_ATTACK") &&
            battle.attackTurn >= 46
        ) {
            return renderGlyphBattleHp(snapshot, battle, frameCounter)
        }
        renderContent(snapshot, frameCounter)
        glyphCanvas.drawColor(Color.WHITE)
        srcRect.set(GLYPH_CROP_LEFT, 0, GLYPH_CROP_LEFT + GLYPH_SIZE, CONTENT_HEIGHT)
        dstRect.set(0, GLYPH_CONTENT_TOP, GLYPH_SIZE, GLYPH_CONTENT_TOP + CONTENT_HEIGHT)
        glyphCanvas.drawBitmap(contentBitmap, srcRect, dstRect, bitmapPaint)
        if (snapshot.screen == "IDLE" && snapshot.idleClockEnabled) {
            drawGlyphIdleClock()
        }
        return glyphBitmap
    }

    private fun drawBoot(snapshot: PhoneVisualSnapshot, frameCounter: Int) {
        when (snapshot.bootStage) {
            0 -> drawContentSprite("spr_start", 0, 0, snapshot.bootFrame)
            1 -> drawContentSprite("spr_start_pop", 0, 0, snapshot.bootFrame)
            2 -> {
                if (snapshot.bootVisible) {
                    drawContentSprite(BASE_SPRITES[snapshot.selectedChar], 8, 0, 1)
                }
            }
            3 -> {
                drawContentSprite("spr_status_digivice_v1", 0, 0)
                drawContentSprite(BASE_SPRITES[snapshot.selectedChar], 8, 0, frameCounter / 8)
            }
            4 -> {
                if (snapshot.bootVisible) {
                    drawContentSprite(BASE_SPRITES[snapshot.currentChar], 8, 0, frameCounter / 8)
                }
            }
            5 -> drawContentSprite(BASE_SPRITES[snapshot.currentChar], snapshot.bootSlideX, 0, frameCounter / 8)
            else -> {
                if (snapshot.bootVisible) {
                    drawContentSprite(BASE_SPRITES[snapshot.currentChar], 8, 0, frameCounter / 8)
                } else {
                    drawContentSprite(HAPPY_SPRITES[snapshot.currentChar], 8, 0, frameCounter / 8)
                    drawContentSprite("spr_happy", 24, 0)
                }
            }
        }
    }

    private fun drawSelect(snapshot: PhoneVisualSnapshot) {
        drawContentSprite("spr_status_digivice_v1", 0, 0)
        drawContentSprite(BASE_SPRITES[snapshot.selectedChar], 8, 0)
    }

    private fun drawIdle(snapshot: PhoneVisualSnapshot, frameCounter: Int) {
        val spriteName = when {
            snapshot.happy && !snapshot.happyAnimation -> HAPPY_SPRITES[snapshot.currentChar]
            snapshot.defeat -> DEFEAT_SPRITES[snapshot.currentChar]
            snapshot.stepActive -> STEP_SPRITES[snapshot.currentChar]
            else -> BASE_SPRITES[snapshot.currentChar]
        }
        drawContentSprite(spriteName, 8, 0, frameCounter / 8)
        if (snapshot.happy && !snapshot.happyAnimation) {
            drawContentSprite("spr_happy", 24, 0)
        } else if (snapshot.defeat) {
            drawContentSprite("spr_defeat", 24, 0)
        } else if (snapshot.battlePending || snapshot.lastEncounterType == "boss") {
            drawContentSprite("spr_happy", 24, 0)
        }
    }

    private fun drawMenu(snapshot: PhoneVisualSnapshot) {
        drawContentSprite("spr_menu_digivice_v1", 0, 0, snapshot.menuIndex)
    }

    private fun drawSlot(snapshot: PhoneVisualSnapshot) {
        val slot = snapshot.slot ?: return
        val frame = if (slot.animation) 1 else 0
        drawContentSprite(SLOT_SPRITES[slot.roll1], 0, 0, frame)
        drawContentSprite(SLOT_SPRITES[slot.roll2], 16, 0, frame)
    }

    private fun drawSlotResult(snapshot: PhoneVisualSnapshot) {
        drawContentSprite("spr_menu_stats", 0, 0, 0)
        drawNumberSprite(snapshot.distance, 25, 8)
    }

    private fun drawCard(snapshot: PhoneVisualSnapshot) {
        val card = snapshot.card ?: return
        when (card.phase) {
            "REVEAL" -> {
                drawContentSprite("spr_card_digivice_v1", 0, 0, card.counter)
                drawContentSprite("spr_card_digivice_v1", 16, 0, card.counter)
            }
            "CHOICE" -> {
                drawContentSprite(CARD_SPRITES[card.roll1], 0, 0)
                drawContentSprite(CARD_SPRITES[card.roll2], 16, 0)
            }
            "SCORE_DISPLAY" -> {
                drawCardResultSprite(card.roll1, card.press1, 0, card.animation)
                drawCardResultSprite(card.roll2, card.press2, 16, card.animation)
            }
        }
    }

    private fun drawCardResult(snapshot: PhoneVisualSnapshot) {
        drawContentSprite("spr_menu_stats", 0, 0, 0)
        drawNumberSprite(snapshot.distance, 25, 8)
    }

    private fun drawStatus(snapshot: PhoneVisualSnapshot) {
        if (lastLoggedScreen != "STATUS" || lastLoggedStatusPage != snapshot.statusPage) {
            val sprite = spriteLibrary.getFrame("spr_menu_stats", snapshot.statusPage)
            Log.d(
                "ExactPhoneRenderer",
                "drawStatus page=${snapshot.statusPage} sprite=spr_menu_stats size=${sprite?.width}x${sprite?.height}"
            )
            lastLoggedScreen = "STATUS"
            lastLoggedStatusPage = snapshot.statusPage
        }
        drawContentSprite("spr_menu_stats", 0, 0, snapshot.statusPage)
        when (snapshot.statusPage % 4) {
            0 -> drawNumberSprite(snapshot.distance, 25, 8)
            1 -> drawNumberSprite(snapshot.steps, 25, 8)
            2 -> drawNumberSprite(snapshot.dpower, 21, 8)
            3 -> {
                drawNumberSprite(snapshot.wins, 5, 0)
                drawNumberSprite(snapshot.battles, 26, 0)
                val percentage = if (snapshot.battles > 0) ((snapshot.wins.toFloat() / snapshot.battles.toFloat()) * 100f).roundToInt() else 0
                drawNumberSprite(percentage, 18, 8)
            }
        }
    }

    private fun drawStatusSelect(snapshot: PhoneVisualSnapshot) {
        drawContentSprite("spr_status_digivice_v1", 0, 0)
        drawContentSprite(BASE_SPRITES[snapshot.currentChar], 8, 0)
    }

    private fun drawStatusMenu(snapshot: PhoneVisualSnapshot) {
        if (lastLoggedScreen != "STATUS_MENU" || lastLoggedStatusMode != snapshot.statusMode) {
            val sprite = spriteLibrary.getFrame("spr_menu_stats_digivice_v1", snapshot.statusMode)
            Log.d(
                "ExactPhoneRenderer",
                "drawStatusMenu mode=${snapshot.statusMode} sprite=spr_menu_stats_digivice_v1 size=${sprite?.width}x${sprite?.height}"
            )
            lastLoggedScreen = "STATUS_MENU"
            lastLoggedStatusMode = snapshot.statusMode
        }
        drawContentSprite("spr_menu_stats_digivice_v1", 0, 0, snapshot.statusMode)
    }

    private fun drawStatusDetail(snapshot: PhoneVisualSnapshot, frameCounter: Int) {
        drawContentSprite("spr_hp_bar_digivice_v1", 0, 0, snapshot.statusBarFrame)
        drawContentSprite(
            EVOLUTION_SPRITES[snapshot.currentChar][snapshot.statusDetailPage.coerceIn(0, 2)],
            8,
            0,
            frameCounter / 8
        )
    }

    private fun drawMap(snapshot: PhoneVisualSnapshot) {
        drawContentSprite("spr_map__digivice_v1", 0, 0)
        for (index in MAP_POSITIONS.indices) {
            val pos = MAP_POSITIONS[index]
            if (index == snapshot.mapPreviewArea) {
                if (snapshot.mapBlinkVisible) {
                    drawContentSprite("spr_map_cover", pos[0], pos[1], 1)
                }
            } else if (snapshot.completedAreas.getOrNull(index) == 2) {
                drawContentSprite("spr_map_cover", pos[0], pos[1], 1)
            }
        }
    }

    private fun drawMapChange(snapshot: PhoneVisualSnapshot) {
        drawContentSprite("spr_map_change", 0, 0)
        drawNumberSprite(snapshot.mapTargetDistance, 25, 8)
    }

    private fun drawFinishGame(snapshot: PhoneVisualSnapshot) {
        if (snapshot.finishOffset > -141) {
            val order = finishOrder(snapshot.currentChar)
            order.forEachIndexed { index, charIndex ->
                drawContentSprite(STEP_SPRITES[charIndex], snapshot.finishOffset + (index * 16), 0, snapshot.finishAnimFrame)
            }
        } else {
            drawContentSprite("spr_end_digivice_v1", 0, 0, snapshot.finishAnimFrame)
        }
    }

    private fun drawFinishReturn(snapshot: PhoneVisualSnapshot) {
        drawContentSprite(BASE_SPRITES[snapshot.currentChar], snapshot.finishReturnX, 0)
    }

    private fun drawBattle(snapshot: PhoneVisualSnapshot, frameCounter: Int) {
        val battle = snapshot.battle ?: return
        when (battle.phase) {
            "ALERT" -> {
                if ((frameCounter / 5) % 2 == 0) {
                    drawContentSprite("spr_battle_alert_digivice_v1", 0, 0)
                }
                drawContentSprite(ATTACK_SPRITES[snapshot.currentChar], 8, 0, frameCounter / 8)
            }
            "START" -> {
                if (battle.startBlinkVisible) {
                    drawContentSprite(ENEMY_SPRITES[battle.enemyId], battle.startSlideX, battle.startSlideY)
                }
            }
            "MENU" -> {
                drawContentSprite("spr_battle_menu_digivice_v1", 0, 0, battle.menuIndex)
            }
            "PUSH" -> {
                drawContentSprite("spr_battle_push_digivice_v1", 0, 0, (frameCounter / 6) % 2)
            }
            "EVO" -> Unit
            "READY_GO" -> {
                drawContentSprite("spr_ready_go_d3_v1", 0, 0, battle.readyGoFrame.coerceIn(0, 1))
            }
            "EVO_SEQUENCE" -> drawEvoSequence(snapshot, battle)
            "DEVOLVE" -> drawFinishDevolve(snapshot, battle)
            "SWAP" -> {
                drawContentSprite("spr_battle_card", 0, 0)
                drawContentSprite(ATTACK_SPRITES[battle.swapIndex], 8, 0, frameCounter / 8)
            }
            "MINE_ATTACK" -> drawMineAttack(snapshot, battle)
            "ENEMY_ATTACK" -> drawEnemyAttack(snapshot, battle)
            "FINISH" -> drawBattleFinish(snapshot, battle)
            "RESULT" -> drawBattleResult(snapshot, battle)
        }
    }

    private fun drawRescue(snapshot: PhoneVisualSnapshot) {
        val rescue = snapshot.rescue ?: return
        when {
            rescue.phase == 1 -> {
                drawContentSprite("spr_evo_filter", 0, 0)
            }
            rescue.phase >= 1 -> {
                if (rescue.phase in 17..19) {
                    if (rescue.visible) {
                        drawContentSprite(BASE_SPRITES[rescue.charIndex], 8, 0)
                    } else {
                        drawContentSprite(HAPPY_SPRITES[rescue.charIndex], 8, 0)
                    }
                } else if (rescue.visible) {
                    drawContentSprite(BASE_SPRITES[rescue.charIndex], 8, 0)
                }
                drawContentSprite("spr_evo_filter_digivice_v1", 0, 0, rescue.filter.coerceIn(0, 2))
            }
            else -> {
                drawContentSprite(BASE_SPRITES[rescue.charIndex], 8, 0)
            }
        }
    }

    private fun drawContentSprite(name: String, x: Int, y: Int, frameIndex: Int = 0) {
        val sprite = spriteLibrary.getFrame(name, frameIndex) ?: return
        contentCanvas.drawBitmap(sprite, x.toFloat(), y.toFloat(), bitmapPaint)
    }

    private fun drawCardResultSprite(roll: Int, pressed: Boolean, x: Int, animation: Boolean) {
        val cardId = CARD_IDS[roll]
        when {
            cardId == -1 && pressed -> drawContentSprite("spr_attack_d3_v1_small", x, 0, if (animation) 1 else 0)
            cardId != -1 && pressed -> drawContentSprite(DEFEAT_SPRITES[cardId], x, 0)
            cardId == -1 -> drawContentSprite(CARD_SPRITES[roll], x, 0, if (animation) 1 else 0)
            animation -> drawContentSprite(BASE_SPRITES[cardId], x, 0)
            else -> drawContentSprite(HAPPY_SPRITES[cardId], x, 0)
        }
    }

    private fun drawEnemySprite(name: String, frameIndex: Int = 0) {
        val sprite = spriteLibrary.getFrame(name, frameIndex) ?: return
        val x = if (sprite.width == 24) 0f else 16f
        val y = if (sprite.width == 24) 0f else 0f
        contentCanvas.drawBitmap(sprite, x, y, bitmapPaint)
    }

    private fun drawMineAttack(snapshot: PhoneVisualSnapshot, battle: PhoneBattleSnapshot) {
        val currentSprite = if (battle.currentEvo == 0) ATTACK_SPRITES[snapshot.currentChar] else EVOLUTION_SPRITES[snapshot.currentChar][battle.currentEvo]
        val currentX = if (battle.currentEvo == 0) 16 else 8
        val attackFrame = if (battle.attackAnimation) 1 else 0
        when {
            battle.attackTurn == 0 -> drawContentSprite(currentSprite, currentX, 0, attackFrame)
            battle.attackTurn in 1..17 -> {
                drawContentSprite(currentSprite, currentX, 0, attackFrame)
                val attackX = if (battle.currentEvo == 0) 8 + battle.attackPosX else battle.attackPosX
                drawContentSprite("spr_attack_digivice_v1", attackX, 0)
                if (battle.currentEvo != 0) {
                    drawContentSprite("spr_attack_digivice_v1", attackX, 8)
                }
            }
            battle.attackTurn in 18..34 -> {
                drawContentSprite(ENEMY_SPRITES[battle.enemyId], 0, 0)
                val attackX = 32 + battle.attackPosX
                drawContentSprite("spr_attack_digivice_v1", attackX, 0)
                if (battle.currentEvo != 0) {
                    drawContentSprite("spr_attack_digivice_v1", attackX, 8)
                }
            }
            battle.attackTurn in 35..45 -> {
                val effect = if (battle.boss) "spr_attack_d3_v1" else "spr_attack_d3_v1_small"
                drawContentSprite(effect, 0, 0, if (battle.attackAnimation) 1 else 0)
            }
            else -> {
                drawContentSprite("spr_hp_bar_digivice_v1", 0, 0, battle.enemyHp.coerceIn(0, 8))
                drawContentSprite(ENEMY_SPRITES[battle.enemyId], 4, 0)
            }
        }
    }

    private fun drawEnemyAttack(snapshot: PhoneVisualSnapshot, battle: PhoneBattleSnapshot) {
        val currentSprite = if (battle.currentEvo == 0) BASE_SPRITES[snapshot.currentChar] else EVOLUTION_SPRITES[snapshot.currentChar][battle.currentEvo]
        val currentX = if (battle.currentEvo == 0) 16 else 8
        when {
            battle.attackTurn == 0 -> drawContentSprite(ENEMY_SPRITES[battle.enemyId], 0, 0, if (battle.attackAnimation) 1 else 0)
            battle.attackTurn in 1..17 -> {
                drawContentSprite(ENEMY_SPRITES[battle.enemyId], 0, 0, if (battle.attackAnimation) 1 else 0)
                val projectileX = if (battle.boss) 32 - battle.attackPosX else 24 - battle.attackPosX
                drawContentSpriteMirrored("spr_attack_digivice_v1", projectileX, 0)
                if (battle.boss) {
                    drawContentSpriteMirrored("spr_attack_digivice_v1", projectileX, 8)
                }
            }
            battle.attackTurn in 18..34 -> {
                drawContentSprite(currentSprite, currentX, 0)
                drawContentSpriteMirrored("spr_attack_digivice_v1", battle.attackPosX, 0)
                if (battle.boss) {
                    drawContentSpriteMirrored("spr_attack_digivice_v1", battle.attackPosX, 8)
                }
            }
            battle.attackTurn in 35..45 -> {
                val effect = if (battle.currentEvo != 0) "spr_attack_d3_v1" else "spr_attack_d3_v1_small"
                val effectX = if (battle.currentEvo != 0) 8 else 16
                drawContentSprite(effect, effectX, 0, if (battle.attackAnimation) 1 else 0)
            }
            else -> {
                drawContentSprite("spr_hp_bar_digivice_v1", 0, 0, battle.mineHp.coerceIn(0, 8))
                drawContentSprite(currentSprite, currentX, 0)
            }
        }
    }

    private fun drawEvoSequence(snapshot: PhoneVisualSnapshot, battle: PhoneBattleSnapshot) {
        val currentSprite = EVOLUTION_SPRITES[snapshot.currentChar][battle.currentEvo.coerceIn(0, 2)]
        val nextSprite = EVOLUTION_SPRITES[snapshot.currentChar][(battle.currentEvo + 1).coerceAtMost(2)]
        val currentX = if (spriteWidth(currentSprite) > 16) 4 else 8
        when {
            battle.evoMenu < 11 -> drawContentSprite("spr_evo_digivice_v1", 0, 0, battle.evoMenu.coerceIn(0, 11))
            battle.evoMenu in 11..15 -> {
                if (!battle.evoAnimation) {
                    drawContentSprite(currentSprite, currentX, 0)
                }
            }
            battle.evoMenu in 16..17 -> {
                drawContentSprite(currentSprite, currentX, 0)
                drawContentSprite("spr_evo_filter", 0, 0)
            }
            battle.evoMenu in 18..24 -> {
                if (battle.evoMenu % 2 == 0 && battle.evoSuccess) {
                    drawContentSprite(nextSprite, 4, -8)
                } else {
                    drawContentSprite(currentSprite, currentX, 0)
                }
                drawContentSprite("spr_evo_filter", 0, 0)
            }
            else -> {
                if (battle.evoSuccess) {
                    drawContentSprite(nextSprite, 4, -8 + battle.evoPosY)
                } else {
                    drawContentSprite(currentSprite, currentX, 0)
                }
            }
        }
    }

    private fun drawFinishDevolve(snapshot: PhoneVisualSnapshot, battle: PhoneBattleSnapshot) {
        val spriteName = EVOLUTION_SPRITES[snapshot.currentChar][battle.finishEvo.coerceIn(0, 2)]
        val x = if (battle.finishEvo == 0) 8 else 4
        drawContentSprite(spriteName, x, 0)
        if (battle.finishFilter) {
            drawContentSprite("spr_evo_filter", 0, 0)
        }
    }

    private fun drawBattleFinish(snapshot: PhoneVisualSnapshot, battle: PhoneBattleSnapshot) {
        drawContentSprite(BASE_SPRITES[snapshot.currentChar], battle.finishSlideX, 0)
    }

    private fun drawBattleResult(snapshot: PhoneVisualSnapshot, battle: PhoneBattleSnapshot) {
        val playerWon = battle.resultText == "WIN"
        val spriteName = if (playerWon) HAPPY_SPRITES[snapshot.currentChar] else DEFEAT_SPRITES[snapshot.currentChar]
        if (battle.resultAnimation) {
            drawContentSprite(BASE_SPRITES[snapshot.currentChar], 8, 0)
        } else {
            drawContentSprite(spriteName, 8, 0)
            if (playerWon) {
                drawContentSprite("spr_happy", 24, 0)
            }
        }
    }

    private fun renderGlyphBattleHp(snapshot: PhoneVisualSnapshot, battle: PhoneBattleSnapshot, frameCounter: Int): Bitmap {
        glyphCanvas.drawColor(Color.WHITE)

        val hpValue: Int
        if (battle.phase == "MINE_ATTACK") {
            hpValue = battle.enemyHp.coerceIn(0, 8)
        } else {
            hpValue = battle.mineHp.coerceIn(0, 8)
        }

        renderContent(snapshot, frameCounter)
        srcRect.set(GLYPH_CROP_LEFT, 0, GLYPH_CROP_LEFT + GLYPH_SIZE, CONTENT_HEIGHT)
        dstRect.set(0, GLYPH_CONTENT_TOP, GLYPH_SIZE, GLYPH_CONTENT_TOP + CONTENT_HEIGHT)
        glyphCanvas.drawBitmap(contentBitmap, srcRect, dstRect, bitmapPaint)
        drawGlyphHpOrbs(hpValue)
        return glyphBitmap
    }

    private fun drawGlyphIdleClock() {
        val now = Calendar.getInstance()
        drawGlyphClockValue(now.get(Calendar.HOUR_OF_DAY), GLYPH_CLOCK_TOP)
        drawGlyphClockValue(now.get(Calendar.MINUTE), GLYPH_CLOCK_BOTTOM)
    }

    private fun drawGlyphClockValue(value: Int, y: Int) {
        val normalized = value.coerceIn(0, 99)
        val tens = normalized / 10
        val ones = normalized % 10
        val totalWidth = (GLYPH_CLOCK_DIGIT_WIDTH * 2) + GLYPH_CLOCK_DIGIT_GAP
        val startX = (GLYPH_SIZE - totalWidth) / 2
        drawGlyphClockDigit(tens, startX, y)
        drawGlyphClockDigit(ones, startX + GLYPH_CLOCK_DIGIT_WIDTH + GLYPH_CLOCK_DIGIT_GAP, y)
    }

    private fun drawGlyphClockDigit(digit: Int, x: Int, y: Int) {
        val pattern = GLYPH_CLOCK_DIGITS[digit.coerceIn(0, 9)]
        glyphRingPaint.color = Color.BLACK
        pattern.forEachIndexed { row, rowPattern ->
            rowPattern.forEachIndexed { col, cell ->
                if (cell == '1') {
                    glyphCanvas.drawRect(
                        (x + col).toFloat(),
                        (y + row).toFloat(),
                        (x + col + 1).toFloat(),
                        (y + row + 1).toFloat(),
                        glyphRingPaint
                    )
                }
            }
        }
    }

    private fun drawGlyphHpOrbs(hpValue: Int) {
        val filledOrbs = hpValue.coerceIn(0, 8)
        val orbCenters = arrayOf(
            12 to 1,
            18 to 3,
            22 to 8,
            22 to 15,
            12 to 22,
            3 to 15,
            2 to 8,
            6 to 3
        )

        orbCenters.forEachIndexed { index, (cx, cy) ->
            drawGlyphOrb(cx, cy, filled = index < filledOrbs)
        }
    }

    private fun drawGlyphOrb(cx: Int, cy: Int, filled: Boolean) {
        glyphBitmap.setPixel(cx, cy, Color.BLACK)
        if (!filled) return

        val cluster = arrayOf(
            cx to cy,
            (cx + 1).coerceAtMost(GLYPH_SIZE - 1) to cy,
            cx to (cy + 1).coerceAtMost(GLYPH_SIZE - 1),
            (cx + 1).coerceAtMost(GLYPH_SIZE - 1) to (cy + 1).coerceAtMost(GLYPH_SIZE - 1)
        )
        for ((x, y) in cluster) {
            glyphBitmap.setPixel(x, y, Color.BLACK)
        }
    }

    private fun drawNumberSprite(value: Int, x: Int, y: Int) {
        val raw = value.coerceAtLeast(0).toString()
        val spacing = 1
        raw.reversed().forEachIndexed { index, ch ->
            val digit = ch.digitToIntOrNull() ?: return@forEachIndexed
            val sprite = spriteLibrary.getFrame("spr_numbers", digit) ?: return@forEachIndexed
            val drawX = x - (index * (sprite.width + spacing))
            contentCanvas.drawBitmap(sprite, drawX.toFloat(), y.toFloat(), bitmapPaint)
        }
    }

    private fun drawContentSpriteMirrored(name: String, x: Int, y: Int, frameIndex: Int = 0) {
        val sprite = spriteLibrary.getFrame(name, frameIndex) ?: return
        contentCanvas.save()
        contentCanvas.translate((x + sprite.width).toFloat(), y.toFloat())
        contentCanvas.scale(-1f, 1f)
        contentCanvas.drawBitmap(sprite, 0f, 0f, bitmapPaint)
        contentCanvas.restore()
    }

    private fun spriteWidth(name: String): Int {
        return spriteLibrary.getFrame(name, 0)?.width ?: 16
    }

    private fun finishOrder(currentChar: Int): List<Int> {
        val chars = mutableListOf(currentChar.coerceIn(BASE_SPRITES.indices))
        for (index in BASE_SPRITES.indices) {
            if (index != currentChar) {
                chars += index
            }
        }
        return chars
    }

    private fun hintText(snapshot: PhoneVisualSnapshot): String {
        return when (snapshot.screen) {
            "SLOT" -> "A/B spin-stop   C exit"
            "SLOT_RESULT" -> if (snapshot.slot?.resultApplied == true) "A/B replay   C exit" else "wait for payout"
            "CARD" -> when (snapshot.card?.phase) {
                "CHOICE" -> "A left   B right   C exit"
                "SCORE_DISPLAY" -> "watch result   C exit"
                else -> "watch cards   C exit"
            }
            "CARD_RESULT" -> if (snapshot.card?.resultApplied == true) "A/B replay   C exit" else "wait for payout"
            "STATUS" -> "A idle   B menu   C next"
            "MENU" -> "A select   B next   C idle"
            else -> "A action   B next   C back"
        }
    }
}
