package com.digimon.digiviceglyph.runtime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.digimon.digiviceglyph.input.GlyphButton
import com.digimon.digiviceglyph.input.GlyphButtonSink
import com.digimon.digiviceglyph.input.GlyphMotionMode
import kotlin.math.roundToInt
import kotlin.random.Random

class DigiviceV1Runtime(context: Context) : GlyphButtonSink {
    private enum class Screen {
        BOOT,
        SELECT,
        IDLE,
        MENU,
        SLOT,
        SLOT_RESULT,
        CARD,
        CARD_RESULT,
        STATUS,
        STATUS_SELECT,
        STATUS_MENU,
        STATUS_DETAIL,
        MAP,
        MAP_CHANGE,
        FINISH_GAME,
        FINISH_RETURN,
        BATTLE,
        RESCUE
    }

    private enum class BattlePhase {
        ALERT,
        MENU,
        PUSH,
        EVO,
        READY_GO,
        EVO_SEQUENCE,
        SWAP,
        MINE_ATTACK,
        ENEMY_ATTACK,
        DEVOLVE,
        FINISH,
        RESULT
    }

    private enum class SlotPhase {
        READY,
        SPINNING,
        RESULT_WAIT,
        RESULT_SCREEN
    }

    private enum class CardPhase {
        REVEAL,
        CHOICE,
        SCORE_DISPLAY,
        RESULT_SCREEN
    }

    private data class EnemyProfile(
        val name: String,
        val hp: Int,
        val attack: Int
    )

    private data class BattleSession(
        val enemyId: Int,
        val enemyName: String,
        val boss: Boolean,
        var mineHp: Int,
        var enemyHp: Int,
        var currentEvo: Int = 0,
        var turn: Int = 0,
        var phase: BattlePhase = BattlePhase.ALERT,
        var menuIndex: Int = 0,
        var pushPress: Int = 0,
        var pushAlarm: Int = 0,
        var evoCharge: Int = 0,
        var swapIndex: Int = 0,
        var resultText: String = "",
        var escaped: Boolean = false,
        var phaseStartedAtMs: Long = System.currentTimeMillis(),
        var evoSuccess: Boolean = false,
        var pendingMineHp: Int? = null,
        var pendingEnemyHp: Int? = null,
        var pendingTurn: Int? = null,
        var defenseSoundPlayed: Boolean = false,
        var damageSoundPlayed: Boolean = false
    )

    internal data class AttackTimelineState(
        val turn: Int,
        val posX: Int,
        val animation: Boolean,
        val hitTriggered: Boolean,
        val damageApplied: Boolean,
        val finished: Boolean
    )

    internal data class ReadyGoState(
        val frame: Int,
        val finished: Boolean
    )

    internal data class PushState(
        val remainingTicks: Int,
        val finished: Boolean
    )

    internal data class EvoSequenceState(
        val currentMenu: Int,
        val animation: Boolean,
        val posY: Int,
        val finished: Boolean
    )

    internal data class FinishDevolveState(
        val displayedEvo: Int,
        val drawFilter: Boolean,
        val finished: Boolean
    )

    internal data class FinishSlideState(
        val posX: Int,
        val finished: Boolean
    )

    internal data class ResultBlinkState(
        val animation: Boolean,
        val finished: Boolean
    )

    private data class RescueSession(
        val charIndex: Int,
        var charge: Int = 0,
        var completed: Boolean = false,
        var phase: Int = 0,
        var visible: Boolean = true,
        var filter: Int = 0,
        var phaseStartedAtMs: Long = System.currentTimeMillis()
    )

    private data class SlotSession(
        var roll1: Int,
        var roll2: Int,
        var phase: SlotPhase = SlotPhase.READY,
        var stop1: Boolean = false,
        var stop2: Boolean = false,
        var counter1: Int = 0,
        var counter2: Int = 0,
        var animation: Boolean = false,
        var resultDistance: Int = 0,
        var resultApplied: Boolean = false,
        var nextReel1AtMs: Long = 0L,
        var nextReel2AtMs: Long = 0L,
        var nextAnimationAtMs: Long = 0L,
        var nextPhaseAtMs: Long = 0L
    )

    private data class CardSession(
        var roundIndex: Int,
        var totalPointUnits: Int,
        var roll1: Int,
        var roll2: Int,
        var press1: Boolean = false,
        var press2: Boolean = false,
        var animation: Boolean = false,
        var counter: Int = 0,
        var phase: CardPhase = CardPhase.REVEAL,
        var roundPointUnits: Int = 0,
        var resultDistance: Int = 0,
        var resultApplied: Boolean = false,
        var nextAdvanceAtMs: Long = 0L,
        var nextAnimationAtMs: Long = 0L,
        var nextPhaseAtMs: Long = 0L
    )

    companion object {
        private const val SIZE = 25
        private const val PHONE_WIDTH = 160
        private const val PHONE_HEIGHT = 160
        private const val ON = Color.WHITE
        private const val OFF = Color.BLACK
        private const val AUTORUN_STEP_INTERVAL_MS = 1000L
        private const val SLOT_FAST_MS = 200L
        private const val SLOT_SLOW_MS = 800L
        private const val SLOT_RESULT_DELAY_MS = 8000L
        private const val SLOT_BLINK_MS = 1000L
        private const val RESULT_APPLY_DELAY_MS = 4000L
        private const val CARD_REVEAL_TICK_MS = 667L
        private const val CARD_DECISION_DELAY_MS = 4000L
        private const val CARD_RESULT_DELAY_MS = 8000L
        private const val CARD_BLINK_MS = 1000L
        private const val PUSH_ALARM_TICKS = 300
        private const val PUSH_WINDOW_MS = 10_000L
        private const val DEFAULT_FRAME_INTERVAL_MS = 90L
        private const val PRECISE_SCENE_FRAME_INTERVAL_MS = 50L
        private val STEP_MULTIPLIERS = intArrayOf(1, 3, 5, 10, 25, 50)
        private val MENU_ITEMS = listOf("STATE", "MAP", "SLOT", "CARD", "MED", "VS")
        private val BATTLE_ITEMS = listOf("ATK", "EVO", "SWP", "RUN")
        private val BASE_SPRITES = arrayOf("spr_agu", "spr_gabu", "spr_biyo", "spr_pal", "spr_tento", "spr_goma", "spr_pata", "spr_gato")
        private val ATTACK_SPRITES = arrayOf("spr_agu_attack", "spr_gabu_attack", "spr_biyo_attack", "spr_pal_attack", "spr_tento_attack", "spr_goma_attack", "spr_pata_attack", "spr_gato_attack")
        private val HAPPY_SPRITES = arrayOf("spr_agu_happy", "spr_gabu_happy", "spr_biyo_happy", "spr_pal_happy", "spr_tento_happy", "spr_goma_happy", "spr_pata_happy", "spr_gato_happy")
        private val DEFEAT_SPRITES = arrayOf("spr_agu_defeat", "spr_gabu_defeat", "spr_biyo_defeat", "spr_pal_defeat", "spr_tento_defeat", "spr_goma_defeat", "spr_pata_defeat", "spr_gato_defeat")
        private val STEP_SPRITES = arrayOf("spr_agu_step", "spr_gabu_step", "spr_biyo_step", "spr_pal_step", "spr_tento_step", "spr_goma_step", "spr_pata_step", "spr_gato_step")
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
        private val MAP_DISTANCES = intArrayOf(10000, 12000, 14000, 16000, 18000, 20000, 22000)
        private val SLOT_POINTS = intArrayOf(1, -1, -1, 1, 1, -1, 1, 1, -1, 1, -1, -1, -1, 1, -1, 1)
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
        private val MAP_ENCOUNTERS = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(3, 0, 1),
            intArrayOf(4, 5, 3),
            intArrayOf(6, 7, 5),
            intArrayOf(8, 6, 4),
            intArrayOf(9, 10, 8),
            intArrayOf(11, 9, 10)
        )
        private val BOSS_ENEMY_IDS = intArrayOf(12, 13, 14, 15, 16, 17, 18)
        private val ENEMIES = listOf(
            EnemyProfile("Scumon", 3, 1),
            EnemyProfile("Numemon", 2, 1),
            EnemyProfile("Shellmon", 2, 1),
            EnemyProfile("Bakemon", 4, 1),
            EnemyProfile("PicoDevimon", 4, 2),
            EnemyProfile("Gazimon", 4, 1),
            EnemyProfile("Hangyomon", 4, 2),
            EnemyProfile("Anomalocarimon", 4, 2),
            EnemyProfile("Tyranomon", 5, 2),
            EnemyProfile("Phantomon", 5, 3),
            EnemyProfile("Megadramon", 5, 2),
            EnemyProfile("WaruMonzaemon", 6, 3),
            EnemyProfile("Devimon", 4, 2),
            EnemyProfile("Etemon", 5, 2),
            EnemyProfile("Myotismon", 6, 3),
            EnemyProfile("MetalSeadramon", 6, 3),
            EnemyProfile("Puppetmon", 7, 3),
            EnemyProfile("Mugendramon", 7, 4),
            EnemyProfile("Piedmon", 8, 5)
        )
        private val ENEMY_SPRITES = arrayOf(
            "spr_scumon",
            "spr_numemon",
            "spr_shellmon",
            "spr_bakemon",
            "spr_picodevimon",
            "spr_gazimon",
            "spr_hangyomon",
            "spr_anomalocarimon",
            "spr_tyranomon",
            "spr_phantomon",
            "spr_megadramon_s",
            "spr_warumonzaemon",
            "spr_devimon_digivice",
            "spr_etemon",
            "spr_myotismon",
            "spr_metalseadramon",
            "spr_puppetmon",
            "spr_mugendramon",
            "spr_piedmon"
        )
        private val FONT = mapOf(
            '0' to listOf("111", "101", "101", "101", "111"),
            '1' to listOf("010", "110", "010", "010", "111"),
            '2' to listOf("111", "001", "111", "100", "111"),
            '3' to listOf("111", "001", "111", "001", "111"),
            '4' to listOf("101", "101", "111", "001", "001"),
            '5' to listOf("111", "100", "111", "001", "111"),
            '6' to listOf("111", "100", "111", "101", "111"),
            '7' to listOf("111", "001", "010", "100", "100"),
            '8' to listOf("111", "101", "111", "101", "111"),
            '9' to listOf("111", "101", "111", "001", "111"),
            'A' to listOf("010", "101", "111", "101", "101"),
            'B' to listOf("110", "101", "110", "101", "110"),
            'C' to listOf("011", "100", "100", "100", "011"),
            'D' to listOf("110", "101", "101", "101", "110"),
            'E' to listOf("111", "100", "110", "100", "111"),
            'F' to listOf("111", "100", "110", "100", "100"),
            'G' to listOf("011", "100", "101", "101", "011"),
            'H' to listOf("101", "101", "111", "101", "101"),
            'I' to listOf("111", "010", "010", "010", "111"),
            'K' to listOf("101", "101", "110", "101", "101"),
            'L' to listOf("100", "100", "100", "100", "111"),
            'M' to listOf("101", "111", "111", "101", "101"),
            'N' to listOf("101", "111", "111", "111", "101"),
            'O' to listOf("111", "101", "101", "101", "111"),
            'P' to listOf("110", "101", "110", "100", "100"),
            'R' to listOf("110", "101", "110", "101", "101"),
            'S' to listOf("011", "100", "010", "001", "110"),
            'T' to listOf("111", "010", "010", "010", "010"),
            'U' to listOf("101", "101", "101", "101", "111"),
            'V' to listOf("101", "101", "101", "101", "010"),
            'W' to listOf("101", "101", "111", "111", "101"),
            'X' to listOf("101", "101", "010", "101", "101"),
            'Y' to listOf("101", "101", "010", "010", "010")
        )

        internal fun computeSlotResultDistance(leftValue: Int, rightValue: Int, sameRoll: Boolean): Int {
            val resultPoints = when {
                leftValue == 1 && rightValue == 1 -> if (sameRoll) 4 else 2
                leftValue == -1 && rightValue == -1 -> if (sameRoll) -4 else -2
                (leftValue == 2 && rightValue == -1) || (leftValue == -1 && rightValue == 2) -> -1
                (leftValue == 2 && rightValue == 1) || (leftValue == 1 && rightValue == 2) -> 3
                (leftValue == 1 && rightValue == 3) || (leftValue == 3 && rightValue == 1) -> 4
                (leftValue == 3 && rightValue == -1) || (leftValue == -1 && rightValue == 3) -> -2
                leftValue == 2 && rightValue == 2 -> 0
                else -> 0
            }
            return resultPoints * 50
        }

        internal fun scoreCardSelection(isEnemyCard: Boolean, pressed: Boolean): Int {
            return when {
                isEnemyCard && pressed -> 1
                !isEnemyCard && !pressed -> 1
                else -> -1
            }
        }

        internal fun computeAttackTimeline(
            elapsedMs: Long,
            currentEvo: Int,
            boss: Boolean,
            mineAttack: Boolean
        ): AttackTimelineState {
            var remainingMs = elapsedMs.coerceAtLeast(0L)
            var turn = 0
            var posX = 0
            var animation = false
            var hitTriggered = false
            var damageApplied = false

            if (remainingMs < 1_000L) {
                return AttackTimelineState(turn, posX, animation, hitTriggered, damageApplied, finished = false)
            }

            remainingMs -= 1_000L
            animation = true
            turn = 1

            val firstStepMs = if (mineAttack) {
                if (currentEvo > 0) 400L else 200L
            } else {
                if (boss) 400L else 200L
            }
            val firstTurnIncrement = if (mineAttack) {
                if (currentEvo > 0) 2 else 1
            } else {
                if (boss) 2 else 1
            }

            while (turn in 1..16 && remainingMs >= firstStepMs) {
                if (turn == 3) {
                    hitTriggered = true
                }
                posX -= 1
                turn += firstTurnIncrement
                if (turn >= 8) {
                    animation = false
                }
                remainingMs -= firstStepMs
            }

            if (turn == 17 && remainingMs >= 200L) {
                remainingMs -= 200L
                turn = 18
                posX = 0
            }

            val secondStepMs = if (mineAttack) {
                if (boss) 400L else 200L
            } else {
                if (currentEvo != 0) 400L else 200L
            }
            val secondTurnIncrement = if (mineAttack) {
                if (boss) 2 else 1
            } else {
                if (currentEvo != 0) 2 else 1
            }

            while (turn in 18..36 && remainingMs >= secondStepMs) {
                posX += if (mineAttack) -1 else 1
                turn += secondTurnIncrement
                remainingMs -= secondStepMs
            }

            while (turn in 37..45 && remainingMs >= 400L) {
                animation = !animation
                turn += 1
                remainingMs -= 400L
            }

            if (turn == 46 && remainingMs >= 1_000L) {
                remainingMs -= 1_000L
                turn = 47
            }
            if (turn == 47 && remainingMs >= 1_000L) {
                remainingMs -= 1_000L
                damageApplied = true
                turn = 48
            }
            if (turn == 48 && remainingMs >= 1_000L) {
                turn = 49
                return AttackTimelineState(turn, posX, animation, hitTriggered, damageApplied = true, finished = true)
            }

            if (turn >= 48) {
                damageApplied = true
            }

            return AttackTimelineState(turn, posX, animation, hitTriggered, damageApplied, finished = false)
        }

        internal fun computeReadyGoState(elapsedMs: Long): ReadyGoState {
            return when {
                elapsedMs < 6_000L -> ReadyGoState(frame = 0, finished = false)
                elapsedMs < 12_000L -> ReadyGoState(frame = 1, finished = false)
                else -> ReadyGoState(frame = 1, finished = true)
            }
        }

        internal fun computePushState(elapsedMs: Long): PushState {
            val clampedElapsedMs = elapsedMs.coerceAtLeast(0L)
            val finished = clampedElapsedMs >= PUSH_WINDOW_MS
            val elapsedRatio = (clampedElapsedMs.toDouble() / PUSH_WINDOW_MS.toDouble()).coerceIn(0.0, 1.0)
            val elapsedTicks = kotlin.math.floor(elapsedRatio * PUSH_ALARM_TICKS).toInt()
            return PushState(
                remainingTicks = (PUSH_ALARM_TICKS - elapsedTicks).coerceAtLeast(0),
                finished = finished
            )
        }

        internal fun computeEvoSequenceState(elapsedMs: Long): EvoSequenceState {
            var remainingMs = elapsedMs.coerceAtLeast(0L)
            var currentMenu = 0
            var animation = false
            var posY = 0

            while (true) {
                when {
                    currentMenu < 11 -> {
                        if (remainingMs < 400L) return EvoSequenceState(currentMenu, animation, posY, finished = false)
                        remainingMs -= 400L
                        currentMenu += 1
                    }
                    currentMenu in 11..15 -> {
                        if (remainingMs < 1_000L) return EvoSequenceState(currentMenu, animation, posY, finished = false)
                        remainingMs -= 1_000L
                        animation = !animation
                        currentMenu += 1
                    }
                    currentMenu in 16..18 -> {
                        if (remainingMs < 2_000L) return EvoSequenceState(currentMenu, animation, posY, finished = false)
                        remainingMs -= 2_000L
                        currentMenu += 1
                    }
                    currentMenu in 19..24 -> {
                        if (remainingMs < 400L) return EvoSequenceState(currentMenu, animation, posY, finished = false)
                        remainingMs -= 400L
                        animation = !animation
                        currentMenu += 1
                    }
                    currentMenu == 25 -> {
                        if (remainingMs < 2_000L) return EvoSequenceState(currentMenu, animation, posY, finished = false)
                        remainingMs -= 2_000L
                        currentMenu = 26
                    }
                    currentMenu in 26..27 -> {
                        if (remainingMs < 2_000L) return EvoSequenceState(currentMenu, animation, posY, finished = false)
                        remainingMs -= 2_000L
                        posY += 4
                        currentMenu += 1
                    }
                    currentMenu == 28 -> {
                        if (remainingMs < 2_000L) return EvoSequenceState(currentMenu, animation, posY, finished = false)
                        remainingMs -= 2_000L
                        currentMenu = 29
                    }
                    else -> return EvoSequenceState(currentMenu, animation, posY, finished = true)
                }
            }
        }

        internal fun computeFinishDevolveState(elapsedMs: Long, currentEvo: Int): FinishDevolveState {
            if (currentEvo <= 0) {
                return FinishDevolveState(displayedEvo = 0, drawFilter = false, finished = true)
            }

            var remainingMs = elapsedMs.coerceAtLeast(0L)
            val stages = listOf(
                Triple(1_000L, currentEvo, false),
                Triple(1_000L, currentEvo, true),
                Triple(1_000L, currentEvo, false),
                Triple(4_000L, currentEvo, true),
                Triple(500L, 0, true),
                Triple(500L, currentEvo, true),
                Triple(500L, 0, true),
                Triple(500L, currentEvo, true),
                Triple(500L, 0, false),
                Triple(500L, 0, true),
                Triple(500L, 0, false),
                Triple(500L, 0, true),
                Triple(500L, 0, false),
                Triple(500L, 0, true),
                Triple(500L, 0, false)
            )

            for ((durationMs, displayedEvo, drawFilter) in stages) {
                if (remainingMs < durationMs) {
                    return FinishDevolveState(displayedEvo, drawFilter, finished = false)
                }
                remainingMs -= durationMs
            }

            return FinishDevolveState(displayedEvo = 0, drawFilter = false, finished = true)
        }

        internal fun computeFinishSlideState(elapsedMs: Long): FinishSlideState {
            val clampedElapsedMs = elapsedMs.coerceAtLeast(0L)
            val steps = (clampedElapsedMs / 100L).toInt().coerceAtMost(24)
            return FinishSlideState(
                posX = 32 - steps,
                finished = steps >= 24
            )
        }

        internal fun computeResultBlinkState(elapsedMs: Long, playerWon: Boolean): ResultBlinkState {
            val durationMs = if (playerWon) 8_000L else 9_000L
            if (elapsedMs >= durationMs) {
                return ResultBlinkState(animation = false, finished = true)
            }
            val animation = ((elapsedMs / 1_000L) % 2L) == 1L
            return ResultBlinkState(animation = animation, finished = false)
        }
    }

    private val pixels = IntArray(SIZE * SIZE)
    private val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val phoneBitmap = Bitmap.createBitmap(PHONE_WIDTH, PHONE_HEIGHT, Bitmap.Config.ARGB_8888)
    private val phoneCanvas = Canvas(phoneBitmap)
    private val phoneBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val phoneFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val phoneTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f
        isFakeBoldText = true
    }
    private val phoneSmallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f
    }
    private val phoneSpritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }
    private val phoneSrcRect = Rect()
    private val phoneDstRect = Rect()
    private val saveRepository = DigiviceV1SaveRepository(context.applicationContext)
    private val spriteLibrary = GlyphSpriteLibrary(context.applicationContext)
    private val exactPhoneRenderer = ExactPhoneRenderer(spriteLibrary)
    private val audioManager = DigiviceAudioManager(context.applicationContext)

    private var state: DigiviceV1State = saveRepository.load() ?: DigiviceV1State().also {
        it.lastEncounter = calculateMilestone(it.distance, it.steps, it.dpower)
    }
    private var screen: Screen =
        if (state.startSequencePending) Screen.BOOT else if (state.battlePending) Screen.BATTLE else Screen.IDLE
    private var selectedChar = state.currentChar
    private var menuIndex = 0
    private var statusPage = 0
    private var statusMode = 0
    private var statusDetailPage = 0
    private var statusReturnScreen = Screen.MENU
    private var mapPreviewArea = state.area
    private var frameCounter = 0
    private var lastStepAtMs = 0L
    private var lastActionLabel = "BOOT"
    private var lastActionAtMs = 0L
    private var bootStage = if (state.startSequencePending) 0 else 7
    private var bootFrame = 0
    private var bootVisible = false
    private var bootSlideX = 32
    private var bootNextAtMs = if (state.startSequencePending) System.currentTimeMillis() + 100L else 0L
    private var finishOffsetX = 32
    private var finishAnimFrame = 0
    private var finishNextAtMs = 0L
    private var finishReturnX = 32
    private var finishReturnNextAtMs = 0L
    private var happyStartedAtMs = 0L
    private var happyEndsAtMs = 0L
    private var stepPoseEndsAtMs = 0L
    private var medicalRecoveryPending = false
    private var stepMultiplierIndex = 0
    private var battleSession: BattleSession? = null
    private var rescueSession: RescueSession? = null
    private var slotSession: SlotSession? = null
    private var cardSession: CardSession? = null
    private var idleClockEnabled = false
    private var lastBackPressAtMs: Long = 0L
    private val ALERT_FLEE_WINDOW_MS = 600L
    private val STEP_POSE_DURATION_MS = 6_000L

    override fun onButtonDown(button: GlyphButton) {
        lastActionLabel = button.name
        lastActionAtMs = System.currentTimeMillis()
        Log.d(
            "DigiviceV1Runtime",
            "onButtonDown: button=$button screen=$screen menuIndex=$menuIndex statusPage=$statusPage statusMode=$statusMode statusDetailPage=$statusDetailPage"
        )
        when (button) {
            GlyphButton.A -> handleConfirm()
            GlyphButton.B -> handleAdvance()
            GlyphButton.C -> handleBack()
            GlyphButton.BACK -> handleBack()
        }
    }

    override fun onButtonUp(button: GlyphButton) {
    }

    fun renderFrame(): Bitmap {
        frameCounter++
        maybeAutorun()
        maybeAdvanceBootSequence()
        maybeAdvanceSlotGame()
        maybeAdvanceCardGame()
        ensureBattleSessionIfNeeded()
        maybeAdvanceBattleAnimations()
        maybeAdvanceRescueSequence()
        maybeAdvanceFinishSequence()
        maybeAdvanceFinishReturn()
        maybeAdvanceHappyState()
        pixels.fill(OFF)
        drawFrame()
        drawHeader()
        when (screen) {
            Screen.BOOT -> drawBootScreen()
            Screen.SELECT -> drawSelectScreen()
            Screen.IDLE -> drawIdleScreen()
            Screen.MENU -> drawMenuScreen()
            Screen.SLOT -> drawSlotScreen()
            Screen.SLOT_RESULT -> drawSlotResultScreen()
            Screen.CARD -> drawCardScreen()
            Screen.CARD_RESULT -> drawCardResultScreen()
            Screen.STATUS -> drawStatusPagerScreen()
            Screen.STATUS_SELECT -> drawSelectScreen()
            Screen.STATUS_MENU -> drawStatusMenuScreen()
            Screen.STATUS_DETAIL -> drawStatusDetailScreen()
            Screen.MAP -> drawMapScreen()
            Screen.MAP_CHANGE -> drawMapScreen()
            Screen.FINISH_GAME -> drawIdleScreen()
            Screen.FINISH_RETURN -> drawFinishReturnScreen()
            Screen.BATTLE -> drawBattleScreen()
            Screen.RESCUE -> drawRescueScreen()
        }
        bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return bitmap
    }

    fun renderPhoneFrame(): Bitmap {
        frameCounter++
        maybeAutorun()
        maybeAdvanceBootSequence()
        maybeAdvanceSlotGame()
        maybeAdvanceCardGame()
        ensureBattleSessionIfNeeded()
        maybeAdvanceBattleAnimations()
        maybeAdvanceRescueSequence()
        maybeAdvanceFinishSequence()
        return exactPhoneRenderer.render(buildPhoneSnapshot(), frameCounter)
    }

    fun renderGlyphContentFrame(): Bitmap {
        frameCounter++
        maybeAutorun()
        maybeAdvanceBootSequence()
        maybeAdvanceSlotGame()
        maybeAdvanceCardGame()
        ensureBattleSessionIfNeeded()
        maybeAdvanceBattleAnimations()
        maybeAdvanceRescueSequence()
        maybeAdvanceFinishSequence()
        maybeAdvanceFinishReturn()
        maybeAdvanceHappyState()
        return exactPhoneRenderer.renderGlyphContent(buildPhoneSnapshot(), frameCounter)
    }

    private fun handleConfirm() {
        when (screen) {
            Screen.BOOT -> handleBootConfirm()
            Screen.SELECT -> {
                playSound(DigiviceAudioManager.Cue.START)
                confirmStarterSelection()
            }
            Screen.IDLE -> Unit
            Screen.MENU -> handleMainMenuConfirm()
            Screen.SLOT -> handleSlotConfirm()
            Screen.SLOT_RESULT -> handleSlotResultConfirm()
            Screen.CARD -> handleCardConfirm()
            Screen.CARD_RESULT -> handleCardResultConfirm()
            Screen.STATUS -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                screen = Screen.IDLE
            }
            Screen.STATUS_SELECT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                statusMode = 0
                statusDetailPage = 0
                screen = Screen.STATUS_MENU
            }
            Screen.STATUS_MENU -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                statusDetailPage = 0
                screen = Screen.STATUS_DETAIL
            }
            Screen.STATUS_DETAIL -> Unit
            Screen.MAP -> {
                if (!isGameComplete()) return
                playSound(DigiviceAudioManager.Cue.SELECT)
                screen = Screen.MAP_CHANGE
            }
            Screen.MAP_CHANGE -> {
                state.area = mapPreviewArea
                state.distance = currentMapTargetDistance()
                state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
                saveState()
                playSound(DigiviceAudioManager.Cue.CHANGE)
                screen = Screen.IDLE
            }
            Screen.FINISH_GAME, Screen.FINISH_RETURN -> Unit
            Screen.BATTLE -> handleBattleConfirm()
            Screen.RESCUE -> resolveRescue()
        }
        logRuntimeState("afterConfirm")
    }

    private fun handleAdvance() {
        Log.d(
            "DigiviceV1Runtime",
            "handleAdvance: screen=$screen menuIndex=$menuIndex statusPage=$statusPage statusMode=$statusMode statusDetailPage=$statusDetailPage"
        )
        when (screen) {
            Screen.BOOT -> handleBootAdvance()
            Screen.SELECT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                selectedChar = (selectedChar + 1) % DigiviceV1State.DIGIMON_PROFILES.size
            }
            Screen.IDLE -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                screen = Screen.MENU
            }
            Screen.MENU -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                menuIndex = (menuIndex + 1) % MENU_ITEMS.size
                Log.d("DigiviceV1Runtime", "menuIndex advanced to $menuIndex (${MENU_ITEMS[menuIndex]})")
            }
            Screen.SLOT -> handleSlotAdvance()
            Screen.SLOT_RESULT -> handleSlotResultAdvance()
            Screen.CARD -> handleCardAdvance()
            Screen.CARD_RESULT -> handleCardResultAdvance()
            Screen.STATUS -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                screen = Screen.MENU
            }
            Screen.STATUS_SELECT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                state.currentChar = nextUnlockedChar(state.currentChar)
                saveState()
            }
            Screen.STATUS_MENU -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                statusMode = (statusMode + 1) % 2
            }
            Screen.STATUS_DETAIL -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                statusDetailPage = (statusDetailPage + 1) % 3
            }
            Screen.MAP -> {
                if (isGameComplete()) {
                    playSound(DigiviceAudioManager.Cue.SELECT)
                    mapPreviewArea = (mapPreviewArea + 1) % MAP_DISTANCES.size
                }
            }
            Screen.MAP_CHANGE -> Unit
            Screen.FINISH_GAME, Screen.FINISH_RETURN -> Unit
            Screen.BATTLE -> handleBattleAdvance()
            Screen.RESCUE -> {
                val rescue = rescueSession ?: return
                if (rescue.phase == 8) {
                    rescue.charge = (rescue.charge + 2).coerceAtMost(20)
                    rescue.filter = when {
                        rescue.charge > 12 -> 2
                        rescue.charge > 6 -> 1
                        else -> 0
                    }
                }
            }
        }
        logRuntimeState("afterAdvance")
    }

    private fun handleBack() {
        when (screen) {
            Screen.BOOT -> handleBootBack()
            Screen.SELECT -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                screen = Screen.BOOT
            }
            Screen.IDLE -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                statusPage = 0
                statusReturnScreen = Screen.IDLE
                screen = Screen.STATUS
            }
            Screen.MENU -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                screen = Screen.IDLE
            }
            Screen.SLOT -> handleSlotBack()
            Screen.SLOT_RESULT -> handleSlotResultBack()
            Screen.CARD -> handleCardBack()
            Screen.CARD_RESULT -> handleCardResultBack()
            Screen.STATUS -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                statusPage = (statusPage + 1) % 4
            }
            Screen.STATUS_SELECT -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                screen = Screen.IDLE
            }
            Screen.STATUS_MENU -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                screen = Screen.STATUS_SELECT
            }
            Screen.STATUS_DETAIL -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                screen = Screen.IDLE
            }
            Screen.MAP -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                screen = Screen.MENU
            }
            Screen.MAP_CHANGE -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                screen = Screen.MAP
            }
            Screen.FINISH_GAME, Screen.FINISH_RETURN -> Unit
            Screen.BATTLE -> handleBattleBack()
            Screen.RESCUE -> Unit
        }
        logRuntimeState("afterBack")
    }

    private fun confirmStarterSelection() {
        selectedChar = selectedChar.coerceIn(0, DigiviceV1State.DIGIMON_PROFILES.lastIndex)
        state.currentChar = selectedChar
        state.unlockedChars[selectedChar] = true
        state.startSequencePending = false
        state.defeat = false
        state.distance = MAP_DISTANCES[state.area]
        state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
        screen = if (state.battlePending) Screen.BATTLE else Screen.IDLE
        saveState()
    }

    private fun handleBootConfirm() {
        when (bootStage) {
            0 -> {
                playSound(DigiviceAudioManager.Cue.START)
                bootStage = 1
                bootFrame = 0
                bootVisible = false
                bootNextAtMs = System.currentTimeMillis() + 100L
            }
            3 -> {
                playSound(DigiviceAudioManager.Cue.START)
                selectedChar = selectedChar.coerceIn(0, DigiviceV1State.DIGIMON_PROFILES.lastIndex)
                state.currentChar = selectedChar
                state.unlockedChars[selectedChar] = true
                bootStage = 4
                bootFrame = 0
                bootVisible = false
                bootNextAtMs = System.currentTimeMillis() + 100L
            }
        }
    }

    private fun handleBootAdvance() {
        if (bootStage == 3) {
            playSound(DigiviceAudioManager.Cue.SELECT)
            selectedChar = (selectedChar + 1) % DigiviceV1State.DIGIMON_PROFILES.size
        }
    }

    private fun handleBootBack() {
        if (bootStage == 3) {
            playSound(DigiviceAudioManager.Cue.CANCEL)
            resetBootSequence()
        }
    }

    private fun handleMainMenuConfirm() {
        Log.d(
            "DigiviceV1Runtime",
            "handleMainMenuConfirm: menuIndex=$menuIndex item=${MENU_ITEMS[menuIndex]} defeat=${state.defeat}"
        )
        when (menuIndex) {
            0 -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                screen = Screen.STATUS_SELECT
            }
            1 -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                mapPreviewArea = state.area
                screen = Screen.MAP
            }
            2 -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                startSlotGame()
            }
            3 -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                startCardGame(resetProgress = true)
            }
            4 -> {
                if (state.defeat) {
                    medicalRecoveryPending = true
                    activateHappyIdle(durationMs = 8_000L)
                    screen = Screen.IDLE
                    saveState()
                } else {
                    playSound(DigiviceAudioManager.Cue.CANCEL)
                }
            }
            5 -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                resetProgress()
            }
        }
    }

    private fun logRuntimeState(reason: String) {
        Log.d(
            "DigiviceV1Runtime",
            "state[$reason]: screen=$screen menuIndex=$menuIndex item=${MENU_ITEMS.getOrNull(menuIndex)} statusPage=$statusPage statusMode=$statusMode statusDetailPage=$statusDetailPage char=${state.currentChar} steps=${state.steps} distance=${state.distance} dpower=${state.dpower}"
        )
    }

    private fun startSlotGame() {
        val now = System.currentTimeMillis()
        slotSession = SlotSession(
            roll1 = Random.nextInt(SLOT_SPRITES.size),
            roll2 = Random.nextInt(SLOT_SPRITES.size),
            nextAnimationAtMs = now + SLOT_BLINK_MS
        )
        cardSession = null
        screen = Screen.SLOT
    }

    private fun startCardGame(resetProgress: Boolean) {
        val now = System.currentTimeMillis()
        val totalPointUnits = if (resetProgress) 0 else (cardSession?.totalPointUnits ?: 0)
        val roundIndex = if (resetProgress) 0 else (cardSession?.roundIndex ?: 0)
        cardSession = CardSession(
            roundIndex = roundIndex,
            totalPointUnits = totalPointUnits,
            roll1 = Random.nextInt(CARD_SPRITES.size),
            roll2 = Random.nextInt(CARD_SPRITES.size),
            nextAdvanceAtMs = now + CARD_REVEAL_TICK_MS
        )
        slotSession = null
        screen = Screen.CARD
    }

    private fun handleSlotConfirm() {
        val session = slotSession ?: return
        playSound(DigiviceAudioManager.Cue.SELECT)
        when (session.phase) {
            SlotPhase.READY -> {
                session.phase = SlotPhase.SPINNING
                val now = System.currentTimeMillis()
                session.nextReel1AtMs = now + SLOT_FAST_MS
                session.nextReel2AtMs = now + SLOT_FAST_MS
            }
            SlotPhase.SPINNING -> {
                if (!session.stop1) {
                    session.stop1 = true
                } else if (!session.stop2) {
                    session.stop2 = true
                }
            }
            SlotPhase.RESULT_WAIT, SlotPhase.RESULT_SCREEN -> Unit
        }
    }

    private fun handleSlotAdvance() = handleSlotConfirm()

    private fun handleSlotBack() {
        playSound(DigiviceAudioManager.Cue.CANCEL)
        menuIndex = 2
        slotSession = null
        screen = Screen.MENU
    }

    private fun handleSlotResultConfirm() {
        val session = slotSession ?: return
        if (!session.resultApplied) return
        playSound(DigiviceAudioManager.Cue.SELECT)
        startSlotGame()
    }

    private fun handleSlotResultAdvance() = handleSlotResultConfirm()

    private fun handleSlotResultBack() {
        playSound(DigiviceAudioManager.Cue.CANCEL)
        menuIndex = 2
        slotSession = null
        screen = Screen.MENU
    }

    private fun handleCardConfirm() {
        val session = cardSession ?: return
        if (session.phase != CardPhase.CHOICE) return
        playSound(DigiviceAudioManager.Cue.SELECT)
        session.press1 = true
    }

    private fun handleCardAdvance() {
        val session = cardSession ?: return
        if (session.phase != CardPhase.CHOICE) return
        playSound(DigiviceAudioManager.Cue.SELECT)
        session.press2 = true
    }

    private fun handleCardBack() {
        playSound(DigiviceAudioManager.Cue.CANCEL)
        menuIndex = 3
        cardSession = null
        screen = Screen.MENU
    }

    private fun handleCardResultConfirm() {
        val session = cardSession ?: return
        if (!session.resultApplied) return
        playSound(DigiviceAudioManager.Cue.SELECT)
        startCardGame(resetProgress = true)
    }

    private fun handleCardResultAdvance() = handleCardResultConfirm()

    private fun handleCardResultBack() {
        playSound(DigiviceAudioManager.Cue.CANCEL)
        menuIndex = 3
        cardSession = null
        screen = Screen.MENU
    }

    private fun maybeAdvanceSlotGame() {
        val session = slotSession ?: return
        val now = System.currentTimeMillis()
        when (session.phase) {
            SlotPhase.READY -> Unit
            SlotPhase.SPINNING -> {
                if (session.nextReel1AtMs != 0L && now >= session.nextReel1AtMs && session.counter1 < 3) {
                    session.roll1 = (session.roll1 + 1) % SLOT_SPRITES.size
                    if (session.stop1) {
                        session.counter1 += 1
                        session.nextReel1AtMs = if (session.counter1 >= 3) 0L else now + SLOT_SLOW_MS
                    } else {
                        session.nextReel1AtMs = now + SLOT_FAST_MS
                    }
                }
                if (session.nextReel2AtMs != 0L && now >= session.nextReel2AtMs && session.counter2 < 3) {
                    session.roll2 = (session.roll2 + 1) % SLOT_SPRITES.size
                    if (session.stop2) {
                        session.counter2 += 1
                        session.nextReel2AtMs = if (session.counter2 >= 3) 0L else now + SLOT_SLOW_MS
                        playSound(DigiviceAudioManager.Cue.SELECT)
                    } else {
                        session.nextReel2AtMs = now + SLOT_FAST_MS
                    }
                }
                if (session.counter1 == 3 && session.counter2 == 3) {
                    session.resultDistance = computeSlotResultDistance(
                        SLOT_POINTS[session.roll1],
                        SLOT_POINTS[session.roll2],
                        session.roll1 == session.roll2
                    )
                    session.phase = SlotPhase.RESULT_WAIT
                    session.nextAnimationAtMs = now + SLOT_BLINK_MS
                    session.nextPhaseAtMs = now + SLOT_RESULT_DELAY_MS
                    playResultCue(session.resultDistance)
                }
            }
            SlotPhase.RESULT_WAIT -> {
                if (now >= session.nextAnimationAtMs) {
                    session.animation = !session.animation
                    session.nextAnimationAtMs = now + SLOT_BLINK_MS
                }
                if (now >= session.nextPhaseAtMs) {
                    session.phase = SlotPhase.RESULT_SCREEN
                    session.nextPhaseAtMs = now + RESULT_APPLY_DELAY_MS
                    session.nextAnimationAtMs = 0L
                    screen = Screen.SLOT_RESULT
                    Log.d("DigiviceV1Runtime", "slot result pending distance=${session.resultDistance}")
                }
            }
            SlotPhase.RESULT_SCREEN -> {
                if (!session.resultApplied && now >= session.nextPhaseAtMs) {
                    applyDistanceReward(session.resultDistance)
                    session.resultApplied = true
                    playSound(DigiviceAudioManager.Cue.SELECT)
                    Log.d("DigiviceV1Runtime", "slot result applied distance=${session.resultDistance} current=${state.distance}")
                }
            }
        }
    }

    private fun maybeAdvanceCardGame() {
        val session = cardSession ?: return
        val now = System.currentTimeMillis()
        when (session.phase) {
            CardPhase.REVEAL -> {
                if (now >= session.nextAdvanceAtMs) {
                    if (session.counter < 16) {
                        session.counter += 1
                        session.nextAdvanceAtMs = now + CARD_REVEAL_TICK_MS
                    } else {
                        session.phase = CardPhase.CHOICE
                        session.nextPhaseAtMs = now + CARD_DECISION_DELAY_MS
                    }
                }
            }
            CardPhase.CHOICE -> {
                if (now >= session.nextPhaseAtMs) {
                    session.phase = CardPhase.SCORE_DISPLAY
                    session.counter = 17
                    session.roundPointUnits = computeCardRoundPointUnits(session)
                    session.nextPhaseAtMs = now + CARD_RESULT_DELAY_MS
                    session.nextAnimationAtMs = now + CARD_BLINK_MS
                    playResultCue(session.roundPointUnits * 25)
                    Log.d(
                        "DigiviceV1Runtime",
                        "card round scored round=${session.roundIndex} units=${session.roundPointUnits} press1=${session.press1} press2=${session.press2}"
                    )
                }
            }
            CardPhase.SCORE_DISPLAY -> {
                if (now >= session.nextAnimationAtMs) {
                    session.animation = !session.animation
                    session.nextAnimationAtMs = now + CARD_BLINK_MS
                }
                if (now >= session.nextPhaseAtMs) {
                    session.totalPointUnits += session.roundPointUnits
                    if (session.roundIndex >= 3) {
                        session.phase = CardPhase.RESULT_SCREEN
                        session.resultDistance = session.totalPointUnits * 25
                        session.nextPhaseAtMs = now + RESULT_APPLY_DELAY_MS
                        screen = Screen.CARD_RESULT
                        Log.d(
                            "DigiviceV1Runtime",
                            "card result pending totalUnits=${session.totalPointUnits} distance=${session.resultDistance}"
                        )
                    } else {
                        startCardGame(resetProgress = false, roundIndex = session.roundIndex + 1, totalPointUnits = session.totalPointUnits)
                    }
                }
            }
            CardPhase.RESULT_SCREEN -> {
                if (!session.resultApplied && now >= session.nextPhaseAtMs) {
                    applyDistanceReward(session.resultDistance)
                    session.resultApplied = true
                    playSound(DigiviceAudioManager.Cue.SELECT)
                    Log.d("DigiviceV1Runtime", "card result applied distance=${session.resultDistance} current=${state.distance}")
                }
            }
        }
    }

    private fun startCardGame(resetProgress: Boolean, roundIndex: Int, totalPointUnits: Int) {
        val now = System.currentTimeMillis()
        cardSession = CardSession(
            roundIndex = roundIndex,
            totalPointUnits = totalPointUnits,
            roll1 = Random.nextInt(CARD_SPRITES.size),
            roll2 = Random.nextInt(CARD_SPRITES.size),
            nextAdvanceAtMs = now + CARD_REVEAL_TICK_MS
        )
        screen = Screen.CARD
    }

    private fun computeCardRoundPointUnits(session: CardSession): Int {
        val leftEnemy = CARD_IDS[session.roll1] == -1
        val rightEnemy = CARD_IDS[session.roll2] == -1
        return scoreCardSelection(leftEnemy, session.press1) + scoreCardSelection(rightEnemy, session.press2)
    }

    private fun applyDistanceReward(rewardDistance: Int) {
        state.distance = (state.distance - rewardDistance).coerceAtLeast(0)
        state.perAreaDistances[state.area.coerceIn(state.perAreaDistances.indices)] = state.distance
        state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
        saveState()
    }

    private fun playResultCue(value: Int) {
        when {
            value > 0 -> playSound(DigiviceAudioManager.Cue.START)
            value < 0 -> playSound(DigiviceAudioManager.Cue.SAD_SMALL)
            else -> playSound(DigiviceAudioManager.Cue.SAD_HAPPY)
        }
    }

    private fun handleBattleConfirm() {
        val session = battleSession ?: return
        when (session.phase) {
            BattlePhase.ALERT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                setBattlePhase(session, BattlePhase.MENU)
            }
            BattlePhase.EVO -> Unit
            BattlePhase.MENU -> {
                when (BATTLE_ITEMS[session.menuIndex]) {
                    "ATK" -> {
                        playSound(DigiviceAudioManager.Cue.SELECT)
                        setBattlePhase(session, BattlePhase.PUSH)
                        session.pushPress = 0
                        session.pushAlarm = PUSH_ALARM_TICKS
                    }
                    "EVO" -> {
                        if (canEvolveInBattle(session)) {
                            playSound(DigiviceAudioManager.Cue.SELECT)
                            state.dpower -= evolutionCost(session.currentEvo)
                            setBattlePhase(session, BattlePhase.READY_GO)
                            session.evoCharge = 0
                            saveState()
                        } else {
                            playSound(DigiviceAudioManager.Cue.CANCEL)
                        }
                    }
                    "SWP" -> {
                        if (hasSwappablePartyMember()) {
                            playSound(DigiviceAudioManager.Cue.SELECT)
                            setBattlePhase(session, BattlePhase.SWAP)
                            session.swapIndex = nextSwappableIndex(state.currentChar)
                        } else {
                            playSound(DigiviceAudioManager.Cue.CANCEL)
                        }
                    }
                    "RUN" -> {
                        playSound(DigiviceAudioManager.Cue.CANCEL)
                        resolveRun()
                    }
                }
            }
            BattlePhase.PUSH -> {
                addPushPress(session)
            }
            BattlePhase.READY_GO -> addReadyGoEnergy(session)
            BattlePhase.SWAP -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                commitSwap()
            }
            BattlePhase.EVO_SEQUENCE, BattlePhase.MINE_ATTACK, BattlePhase.ENEMY_ATTACK, BattlePhase.DEVOLVE -> {
                // No button input during attack
                playSound(DigiviceAudioManager.Cue.SELECT)
            }
            BattlePhase.FINISH, BattlePhase.RESULT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                finalizeBattleResult()
            }
        }
    }

    private fun handleBattleAdvance() {
        val session = battleSession ?: return
        when (session.phase) {
            BattlePhase.ALERT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                setBattlePhase(session, BattlePhase.MENU)
            }
            BattlePhase.MENU -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                session.menuIndex = (session.menuIndex + 1) % BATTLE_ITEMS.size
            }
            BattlePhase.PUSH -> {
                addPushPress(session)
            }
            BattlePhase.READY_GO -> addReadyGoEnergy(session)
            BattlePhase.EVO -> Unit
            BattlePhase.SWAP -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                session.swapIndex = nextSwappableIndex(session.swapIndex)
            }
            BattlePhase.EVO_SEQUENCE -> Unit
            BattlePhase.MINE_ATTACK, BattlePhase.ENEMY_ATTACK, BattlePhase.DEVOLVE, BattlePhase.FINISH -> {
                // No button input during attack
                playSound(DigiviceAudioManager.Cue.SELECT)
            }
            BattlePhase.RESULT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                finalizeBattleResult()
            }
        }
    }

    private fun handleBattleBack() {
        val session = battleSession ?: return
        val now = System.currentTimeMillis()
        when (session.phase) {
            BattlePhase.ALERT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                if (now - lastBackPressAtMs < ALERT_FLEE_WINDOW_MS) {
                    // Second press within window — flee
                    resolveRun()
                } else {
                    // First press — advance to MENU
                    setBattlePhase(session, BattlePhase.MENU)
                }
                lastBackPressAtMs = now
            }
            BattlePhase.MENU -> {
                // C does nothing in MENU — only A confirms RUN
                playSound(DigiviceAudioManager.Cue.SELECT)
            }
            BattlePhase.PUSH -> {
                addPushPress(session)
            }
            BattlePhase.EVO -> Unit
            BattlePhase.READY_GO -> addReadyGoEnergy(session)
            BattlePhase.SWAP -> {
                playSound(DigiviceAudioManager.Cue.CANCEL)
                setBattlePhase(session, BattlePhase.MENU)
            }
            BattlePhase.EVO_SEQUENCE, BattlePhase.MINE_ATTACK, BattlePhase.ENEMY_ATTACK, BattlePhase.DEVOLVE -> {
                // No button input during attack
                playSound(DigiviceAudioManager.Cue.SELECT)
            }
            BattlePhase.FINISH, BattlePhase.RESULT -> {
                playSound(DigiviceAudioManager.Cue.SELECT)
                finalizeBattleResult()
            }
        }
    }

    private fun addReadyGoEnergy(session: BattleSession) {
        if (currentReadyGoState(session).finished) return
        session.evoCharge += Random.nextInt(0, 3)
        if (session.evoCharge > 10) {
            session.evoCharge = 3
        }
        Log.d(
            "DigiviceV1Runtime",
            "READY_GO input accepted evoCharge=${session.evoCharge} phase=${session.phase}"
        )
        playSound(DigiviceAudioManager.Cue.SHAKE)
    }

    private fun addPushPress(session: BattleSession) {
        if (currentPushState(session).finished) return
        session.pushPress += 2
        Log.d(
            "DigiviceV1Runtime",
            "PUSH input accepted pushPress=${session.pushPress} remainingTicks=${currentPushState(session).remainingTicks}"
        )
        playSound(DigiviceAudioManager.Cue.SHAKE)
    }

    private fun maybeAutorun() {
        if (!state.autorun || screen != Screen.IDLE) return
        val now = System.currentTimeMillis()
        if (now - lastStepAtMs >= AUTORUN_STEP_INTERVAL_MS) {
            triggerStep()
        }
    }

    private fun acceptsStepProgressOnCurrentScreen(): Boolean {
        return when (screen) {
            Screen.IDLE,
            Screen.MENU,
            Screen.STATUS,
            Screen.STATUS_SELECT,
            Screen.STATUS_MENU,
            Screen.STATUS_DETAIL,
            Screen.MAP,
            Screen.MAP_CHANGE -> true
            else -> false
        }
    }

    private fun maybeAdvanceBootSequence() {
        if (screen != Screen.BOOT || !state.startSequencePending) return
        val now = System.currentTimeMillis()
        if (bootNextAtMs == 0L || now < bootNextAtMs) return

        when (bootStage) {
            0 -> {
                bootFrame += 1
                if (bootFrame > 8) {
                    bootFrame = 0
                }
                bootNextAtMs = now + 100L
            }
            1 -> {
                if (bootFrame >= 2) {
                    bootStage = 2
                    bootFrame = 0
                    bootVisible = false
                    bootNextAtMs = now + 100L
                } else {
                    bootFrame += 1
                    bootNextAtMs = now + 100L
                }
            }
            2 -> {
                bootVisible = !bootVisible
                bootFrame += 1
                if (bootFrame > 8) {
                    bootStage = 3
                    bootFrame = 0
                    bootVisible = true
                    bootNextAtMs = 0L
                } else {
                    bootNextAtMs = now + 100L
                }
            }
            4 -> {
                bootVisible = !bootVisible
                bootFrame += 1
                if (bootFrame > 9) {
                    bootStage = 5
                    bootFrame = 0
                    bootSlideX = 32
                    bootNextAtMs = now + 60L
                } else {
                    bootNextAtMs = now + 100L
                }
            }
            5 -> {
                bootSlideX -= 1
                if (bootSlideX <= 8) {
                    bootSlideX = 8
                    bootStage = 6
                    bootFrame = 0
                    bootVisible = false
                    bootNextAtMs = now + 600L
                } else {
                    bootNextAtMs = now + 60L
                }
            }
            6 -> {
                bootVisible = !bootVisible
                bootFrame += 1
                if (bootFrame == 5) {
                    finalizeBootSequence()
                } else {
                    bootNextAtMs = now + 600L
                }
            }
        }
    }

    private fun maybeAdvanceBattleAnimations() {
        val session = battleSession ?: return
        if (screen != Screen.BATTLE) return
        when (session.phase) {
            BattlePhase.PUSH -> {
                val pushState = currentPushState(session)
                session.pushAlarm = pushState.remainingTicks
                if (pushState.finished) {
                    resolvePush()
                }
            }
            BattlePhase.EVO_SEQUENCE -> {
                if (currentEvoSequenceState(session).finished) {
                    completeEvolutionSequence()
                }
            }
            BattlePhase.DEVOLVE -> {
                if (currentFinishDevolveState(session).finished) {
                    setBattlePhase(session, BattlePhase.FINISH)
                }
            }
            BattlePhase.READY_GO -> {
                if (currentReadyGoState(session).finished) {
                    beginEvolutionSequence()
                }
            }
            BattlePhase.MINE_ATTACK, BattlePhase.ENEMY_ATTACK -> {
                val timeline = attackTimeline(session) ?: return
                if (!session.defenseSoundPlayed && timeline.turn >= 18) {
                    session.defenseSoundPlayed = true
                    playSound(DigiviceAudioManager.Cue.ATTACK_DEFENSE)
                }
                if (!session.damageSoundPlayed && timeline.damageApplied) {
                    session.damageSoundPlayed = true
                    playSound(DigiviceAudioManager.Cue.ATTACK_HIT)
                }
                if (timeline.finished) {
                    completeAttackAnimation()
                }
            }
            BattlePhase.FINISH -> {
                if (currentFinishSlideState(session).finished) {
                    if (session.resultText == "ESC") {
                        finalizeBattleResult()
                    } else {
                        setBattlePhase(session, BattlePhase.RESULT)
                    }
                }
            }
            BattlePhase.RESULT -> {
                if (currentResultBlinkState(session).finished) {
                    finalizeBattleResult()
                }
            }
            else -> Unit
        }
    }

    private fun maybeAdvanceRescueSequence() {
        val rescue = rescueSession ?: return
        if (screen != Screen.RESCUE) return
        val ticks = rescuePhaseTicks(rescue)
        when (rescue.phase) {
            0 -> {
                if (ticks >= 1) {
                    advanceRescuePhase(rescue, 1)
                }
            }
            1 -> {
                if (ticks >= 1) {
                    advanceRescuePhase(rescue, 2)
                }
            }
            in 2..7 -> {
                if (ticks >= 1) {
                    rescue.visible = !rescue.visible
                    advanceRescuePhase(rescue, rescue.phase + 1)
                }
            }
            8 -> {
                if (ticks >= 1) {
                    advanceRescuePhase(rescue, 9)
                }
            }
            in 9..16 -> {
                if (ticks >= 1) {
                    rescue.visible = !rescue.visible
                    advanceRescuePhase(rescue, rescue.phase + 1)
                }
            }
            in 17..19 -> {
                if (ticks >= 1) {
                    if (rescue.charge > 12) {
                        rescue.visible = !rescue.visible
                    }
                    advanceRescuePhase(rescue, rescue.phase + 1)
                }
            }
            20 -> {
                if (ticks >= 1) {
                    advanceRescuePhase(rescue, 21)
                }
            }
            21 -> finishRescue(unlocked = rescue.charge > 12)
        }
    }

    private fun maybeAdvanceFinishSequence() {
        if (screen != Screen.FINISH_GAME) return
        val now = System.currentTimeMillis()
        if (finishNextAtMs == 0L || now < finishNextAtMs) return

        when {
            finishOffsetX >= -140 -> {
                finishAnimFrame = 1 - finishAnimFrame
                finishOffsetX -= 1
                finishNextAtMs = now + 12L
            }
            finishOffsetX == -141 -> {
                finishNextAtMs = now + 120L
                finishOffsetX -= 1
            }
            finishOffsetX == -142 -> {
                finishAnimFrame = 1 - finishAnimFrame
                finishNextAtMs = now + 360L
                finishOffsetX -= 1
            }
            else -> {
                state.distance = 22000
                state.defeat = false
                state.area = MAP_DISTANCES.lastIndex
                state.battlePending = false
                state.eventPending = false
                state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
                finishOffsetX = 32
                finishAnimFrame = 0
                finishNextAtMs = 0L
                startFinishReturn()
            }
        }
    }

    private fun startFinishReturn() {
        finishReturnX = 32
        finishReturnNextAtMs = System.currentTimeMillis() + 100L
        screen = Screen.FINISH_RETURN
    }

    private fun maybeAdvanceFinishReturn() {
        if (screen != Screen.FINISH_RETURN) return
        val now = System.currentTimeMillis()
        if (finishReturnNextAtMs == 0L || now < finishReturnNextAtMs) return

        if (finishReturnX != 8) {
            finishReturnX -= 1
            finishReturnNextAtMs = now + 100L
            return
        }

        finishReturnX = 32
        finishReturnNextAtMs = 0L
        activateHappyIdle(durationMs = 8_000L)
        screen = Screen.IDLE
        saveState()
    }

    private fun activateHappyIdle(durationMs: Long) {
        happyStartedAtMs = System.currentTimeMillis()
        happyEndsAtMs = happyStartedAtMs + durationMs
        playSound(DigiviceAudioManager.Cue.HAPPY)
    }

    private fun maybeAdvanceHappyState() {
        if (happyEndsAtMs == 0L) return
        if (System.currentTimeMillis() < happyEndsAtMs) return
        happyStartedAtMs = 0L
        happyEndsAtMs = 0L
        if (medicalRecoveryPending) {
            medicalRecoveryPending = false
            state.defeat = false
            saveState()
        }
    }

    private fun isHappyIdleActive(): Boolean = happyEndsAtMs != 0L && System.currentTimeMillis() < happyEndsAtMs

    private fun isHappyIdleAnimationFrame(): Boolean {
        if (!isHappyIdleActive()) return false
        val elapsedMs = System.currentTimeMillis() - happyStartedAtMs
        return ((elapsedMs / 1_000L) % 2L) == 1L
    }

    private fun isStepPoseActive(): Boolean = stepPoseEndsAtMs != 0L && System.currentTimeMillis() < stepPoseEndsAtMs

    private fun performSingleStep() {
        if (!acceptsStepProgressOnCurrentScreen() || state.defeat || state.connectMode || state.battlePending) return
        val now = System.currentTimeMillis()
        lastStepAtMs = now
        stepPoseEndsAtMs = now + STEP_POSE_DURATION_MS
        state.steps = (state.steps + 1) % 1_000_000
        if (state.distance > 0) {
            state.distance -= 1
        }
        if (state.steps % 100 == 0 && state.dpower < 99) {
            state.dpower += 1
        }
        state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
        if (state.distance == 0 || state.steps % 500 == 0) {
            state.battlePending = true
            battleSession = null
            playSound(DigiviceAudioManager.Cue.SHAKE)
            screen = Screen.BATTLE
        }
        saveState()
    }

    private fun triggerStepsInternal(count: Int) {
        repeat(count.coerceAtLeast(0)) {
            performSingleStep()
            if (!acceptsStepProgressOnCurrentScreen() || state.battlePending || screen == Screen.BATTLE) {
                return
            }
        }
    }

    fun toggleAutorun() {
        state.autorun = !state.autorun
        saveState()
    }

    fun isAutorunEnabled(): Boolean = state.autorun

    fun toggleSound() {
        state.soundEnabled = !state.soundEnabled
        saveState()
    }

    fun isSoundEnabled(): Boolean = state.soundEnabled

    fun toggleIdleClock() {
        idleClockEnabled = !idleClockEnabled
    }

    fun isIdleClockEnabled(): Boolean = idleClockEnabled

    override fun triggerStep() {
        triggerStepsInternal(currentStepMultiplier())
    }

    fun triggerRawSteps(count: Int) {
        triggerStepsInternal(count)
    }

    fun cycleStepMultiplier(): Int {
        stepMultiplierIndex = (stepMultiplierIndex + 1) % STEP_MULTIPLIERS.size
        return currentStepMultiplier()
    }

    fun currentStepMultiplier(): Int = STEP_MULTIPLIERS[stepMultiplierIndex]

    override fun motionInputMode(): GlyphMotionMode {
        return if (screen == Screen.BATTLE && battleSession?.phase in setOf(BattlePhase.PUSH, BattlePhase.READY_GO)) {
            GlyphMotionMode.PUSH_ALL_DIRECTIONS
        } else {
            GlyphMotionMode.FOUR_WAY_LOCK
        }
    }

    override fun acceptsPassiveWalking(): Boolean {
        return acceptsStepProgressOnCurrentScreen() && !state.defeat && !state.connectMode && !state.battlePending
    }

    override fun requiresIdleControlWake(): Boolean {
        return screen == Screen.IDLE
    }

    fun preferredFrameIntervalMs(): Long {
        return if (usesPreciseSceneTiming()) PRECISE_SCENE_FRAME_INTERVAL_MS else DEFAULT_FRAME_INTERVAL_MS
    }

    private fun usesPreciseSceneTiming(): Boolean {
        return screen == Screen.BATTLE && battleSession?.phase in setOf(
            BattlePhase.EVO_SEQUENCE,
            BattlePhase.MINE_ATTACK,
            BattlePhase.ENEMY_ATTACK,
            BattlePhase.DEVOLVE,
            BattlePhase.FINISH,
            BattlePhase.RESULT
        )
    }

    private fun ensureBattleSessionIfNeeded() {
        if (screen != Screen.BATTLE || !state.battlePending || battleSession != null) return
        val encounter = state.lastEncounter ?: calculateMilestone(state.distance, state.steps, state.dpower)
        val boss = state.distance == 0 || encounter.type == "boss"
        val enemyId = if (boss) {
            BOSS_ENEMY_IDS[state.area.coerceIn(BOSS_ENEMY_IDS.indices)]
        } else {
            val pool = MAP_ENCOUNTERS[state.area.coerceIn(MAP_ENCOUNTERS.indices)]
            pool[Random.nextInt(pool.size)]
        }
        val enemy = ENEMIES[enemyId]
        battleSession = BattleSession(
            enemyId = enemyId,
            enemyName = enemy.name,
            boss = boss,
            mineHp = state.currentProfile().evolutions.first().hp,
            enemyHp = enemy.hp,
            swapIndex = nextSwappableIndex(state.currentChar)
        )
        playSound(DigiviceAudioManager.Cue.ENCOUNTER)
        playSound(DigiviceAudioManager.Cue.ALERT_OLD)
    }

    private fun resolvePush() {
        val session = battleSession ?: return
        val chance = session.pushPress * 3
        val roll = Random.nextInt(0, 101)
        Log.d(
            "DigiviceV1Runtime",
            "resolvePush pushPress=${session.pushPress} chance=$chance roll=$roll"
        )
        if (chance > roll) {
            startMineAttack()
        } else {
            startEnemyAttack()
        }
    }

    private fun beginEvolutionSequence() {
        val session = battleSession ?: return
        session.evoSuccess = session.evoCharge * 10 > Random.nextInt(0, 101)
        setBattlePhase(session, BattlePhase.EVO_SEQUENCE)
    }

    private fun commitSwap() {
        val session = battleSession ?: return
        state.currentChar = session.swapIndex
        session.currentEvo = 0
        session.mineHp = state.currentProfile().evolutions.first().hp
        setBattlePhase(session, BattlePhase.PUSH)
        session.pushPress = 0
        session.pushAlarm = PUSH_ALARM_TICKS
        saveState()
    }

    private fun resolveRun() {
        val session = battleSession ?: return
        state.battlePending = false
        state.eventPending = false
        if (!session.boss && Random.nextInt(0, 100) < 20) {
            session.escaped = true
            session.resultText = "ESC"
            state.defeat = false
        } else {
            session.escaped = false
            session.resultText = "FAIL"
        }
        setBattlePhase(session, BattlePhase.FINISH)
        saveState()
    }

    private fun startMineAttack() {
        val session = battleSession ?: return
        val nextEnemyHp = (session.enemyHp - currentMineAttack(session)).coerceAtLeast(0)
        val nextTurn = session.turn + 1
        session.pendingEnemyHp = nextEnemyHp
        session.pendingTurn = nextTurn
        session.resultText = if (shouldFinishBattle(session, nextTurn, session.mineHp, nextEnemyHp)) {
            if (session.mineHp >= nextEnemyHp) "WIN" else "LOSE"
        } else {
            ""
        }
        setBattlePhase(session, BattlePhase.MINE_ATTACK)
    }

    private fun startEnemyAttack() {
        val session = battleSession ?: return
        val nextMineHp = (session.mineHp - currentEnemyAttack(session)).coerceAtLeast(0)
        val nextTurn = session.turn + 1
        session.pendingMineHp = nextMineHp
        session.pendingTurn = nextTurn
        session.resultText = if (shouldFinishBattle(session, nextTurn, nextMineHp, session.enemyHp)) {
            if (nextMineHp >= session.enemyHp) "WIN" else "LOSE"
        } else {
            ""
        }
        setBattlePhase(session, BattlePhase.ENEMY_ATTACK)
    }

    private fun shouldFinishBattle(session: BattleSession, turn: Int = session.turn, mineHp: Int = session.mineHp, enemyHp: Int = session.enemyHp): Boolean {
        val maxTurns = if (session.boss) 5 else 3
        return turn >= maxTurns || mineHp == 0 || enemyHp == 0
    }

    private fun finalizeBattleResult() {
        val session = battleSession ?: return
        if (session.escaped) {
            state.battlePending = false
            battleSession = null
            screen = Screen.IDLE
            saveState()
            return
        }

        state.battles += 1
        state.battlePending = false
        state.eventPending = false
        val playerWon = session.resultText == "WIN"
        if (playerWon) {
            state.wins += 1
            state.defeat = false
            if (session.boss) {
                state.areas[state.area.coerceIn(state.areas.indices)] = 2
                state.perAreaDistances[state.area.coerceIn(state.perAreaDistances.indices)] = state.distance
                if (state.area == MAP_DISTANCES.lastIndex) {
                    startFinishGameSequence()
                } else {
                    state.area = (state.area + 1).coerceAtMost(MAP_DISTANCES.lastIndex)
                    state.distance = MAP_DISTANCES[state.area]
                }
            }
        } else {
            state.distance += 500
            state.dpower = (state.dpower - 2).coerceAtLeast(0)
            state.defeat = Random.nextBoolean()
        }

        state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
        battleSession = null

        if (screen == Screen.FINISH_GAME) {
            saveState()
            return
        }

        if (playerWon && !session.boss && shouldStartRescue()) {
            screen = Screen.RESCUE
        } else {
            screen = Screen.IDLE
        }
        saveState()
    }

    private fun completeEvolutionSequence() {
        val session = battleSession ?: return
        if (session.evoSuccess && session.currentEvo < 2) {
            session.currentEvo += 1
            session.mineHp = state.currentProfile().evolutions[session.currentEvo].hp
        }
        session.pushPress = 0
        session.pushAlarm = PUSH_ALARM_TICKS
        setBattlePhase(session, BattlePhase.PUSH)
    }

    private fun completeAttackAnimation() {
        val session = battleSession ?: return
        session.pendingMineHp?.let { session.mineHp = it }
        session.pendingEnemyHp?.let { session.enemyHp = it }
        session.pendingTurn?.let { session.turn = it }
        session.pendingMineHp = null
        session.pendingEnemyHp = null
        session.pendingTurn = null
        if (session.resultText.isNotEmpty()) {
            if (session.currentEvo > 0) {
                setBattlePhase(session, BattlePhase.DEVOLVE)
            } else {
                setBattlePhase(session, BattlePhase.FINISH)
            }
        } else {
            session.menuIndex = 0
            session.resultText = ""
            setBattlePhase(session, BattlePhase.MENU)
        }
    }

    private fun shouldStartRescue(): Boolean {
        if (state.unlockedChars.all { it }) return false
        val lockedIndex = randomLockedCharIndex() ?: return false
        return if (Random.nextInt(0, 100) < 10) {
            rescueSession = RescueSession(
                charIndex = lockedIndex,
                phase = 0,
                visible = true,
                filter = 0,
                phaseStartedAtMs = System.currentTimeMillis()
            )
            true
        } else {
            false
        }
    }

    private fun resolveRescue() {
        val rescue = rescueSession ?: return
        if (rescue.phase == 8) {
            rescue.charge = (rescue.charge + 2).coerceAtMost(20)
            rescue.filter = when {
                rescue.charge > 12 -> 2
                rescue.charge > 6 -> 1
                else -> 0
            }
        }
    }

    private fun finishRescue(unlocked: Boolean) {
        val rescue = rescueSession
        if (unlocked && rescue != null) {
            state.unlockedChars[rescue.charIndex] = true
            playSound(DigiviceAudioManager.Cue.START)
            saveState()
        } else {
            playSound(DigiviceAudioManager.Cue.SAD_SMALL)
        }
        rescueSession = null
        screen = Screen.IDLE
    }

    private fun advanceRescuePhase(rescue: RescueSession, phase: Int) {
        rescue.phase = phase
        rescue.phaseStartedAtMs = System.currentTimeMillis()
        if (phase == 8) {
            rescue.visible = true
        }
    }

    private fun rescuePhaseTicks(rescue: RescueSession): Int {
        val elapsedMs = (System.currentTimeMillis() - rescue.phaseStartedAtMs).coerceAtLeast(0L)
        val tickMs = when (rescue.phase) {
            0, 1, 20 -> 60L
            in 2..7 -> 30L
            8 -> 3000L
            in 9..16 -> 12L
            in 17..19 -> 30L
            else -> 60L
        }
        return (elapsedMs / tickMs).toInt()
    }

    private fun canEvolveInBattle(session: BattleSession): Boolean {
        if (session.currentEvo >= 2) return false
        val required = evolutionCost(session.currentEvo)
        return state.dpower >= required
    }

    private fun evolutionCost(currentEvo: Int): Int {
        return when (currentEvo) {
            0 -> 3
            1 -> 6
            else -> Int.MAX_VALUE
        }
    }

    private fun hasSwappablePartyMember(): Boolean {
        return state.unlockedChars.indices.any { it != state.currentChar && state.unlockedChars[it] }
    }

    private fun nextSwappableIndex(from: Int): Int {
        if (!hasSwappablePartyMember()) return state.currentChar
        for (offset in 1..state.unlockedChars.size) {
            val candidate = (from + offset) % state.unlockedChars.size
            if (candidate != state.currentChar && state.unlockedChars[candidate]) {
                return candidate
            }
        }
        return state.currentChar
    }

    private fun currentMineAttack(session: BattleSession): Int {
        var attack = state.currentProfile().evolutions[session.currentEvo].attack
        val char = state.currentChar
        val area = state.area
        if (((char == 3 || char == 5) && area == 2) ||
            ((char == 1 || char == 4) && area == 3) ||
            ((char == 6 || char == 7) && area == 4) ||
            ((char == 0 || char == 2) && area == 5)
        ) {
            attack += 1
        }
        if (session.boss) {
            attack -= 1
        }
        return attack.coerceAtLeast(1)
    }

    private fun currentEnemyAttack(session: BattleSession): Int {
        return ENEMIES[session.enemyId].attack
    }

    private fun setBattlePhase(session: BattleSession, phase: BattlePhase) {
        session.phase = phase
        session.phaseStartedAtMs = System.currentTimeMillis()
        session.defenseSoundPlayed = false
        session.damageSoundPlayed = false
        if (phase != BattlePhase.MINE_ATTACK) {
            session.pendingEnemyHp = null
        }
        if (phase != BattlePhase.ENEMY_ATTACK) {
            session.pendingMineHp = null
        }
        if (phase != BattlePhase.MINE_ATTACK && phase != BattlePhase.ENEMY_ATTACK) {
            session.pendingTurn = null
        }
        when (phase) {
            BattlePhase.ALERT -> playSound(DigiviceAudioManager.Cue.ALERT_OLD)
            BattlePhase.READY_GO -> playSound(DigiviceAudioManager.Cue.READY_GO)
            BattlePhase.EVO_SEQUENCE -> playSound(DigiviceAudioManager.Cue.EVO)
            BattlePhase.MINE_ATTACK, BattlePhase.ENEMY_ATTACK -> playSound(DigiviceAudioManager.Cue.ATTACK)
            BattlePhase.RESULT -> {
                if (session.resultText == "WIN") {
                    playSound(DigiviceAudioManager.Cue.HAPPY)
                } else if (session.resultText != "ESC") {
                    playSound(DigiviceAudioManager.Cue.SAD)
                }
            }
            else -> Unit
        }
    }

    private fun attackTimeline(session: BattleSession): AttackTimelineState? {
        val mineAttack = when (session.phase) {
            BattlePhase.MINE_ATTACK -> true
            BattlePhase.ENEMY_ATTACK -> false
            else -> return null
        }
        return computeAttackTimeline(
            elapsedMs = System.currentTimeMillis() - session.phaseStartedAtMs,
            currentEvo = session.currentEvo,
            boss = session.boss,
            mineAttack = mineAttack
        )
    }

    private fun currentReadyGoState(session: BattleSession): ReadyGoState {
        return computeReadyGoState(System.currentTimeMillis() - session.phaseStartedAtMs)
    }

    private fun currentPushState(session: BattleSession): PushState {
        return computePushState(System.currentTimeMillis() - session.phaseStartedAtMs)
    }

    private fun currentEvoSequenceState(session: BattleSession): EvoSequenceState {
        return computeEvoSequenceState(System.currentTimeMillis() - session.phaseStartedAtMs)
    }

    private fun currentFinishDevolveState(session: BattleSession): FinishDevolveState {
        return computeFinishDevolveState(
            elapsedMs = System.currentTimeMillis() - session.phaseStartedAtMs,
            currentEvo = session.currentEvo
        )
    }

    private fun currentFinishSlideState(session: BattleSession): FinishSlideState {
        return computeFinishSlideState(System.currentTimeMillis() - session.phaseStartedAtMs)
    }

    private fun currentResultBlinkState(session: BattleSession): ResultBlinkState {
        return computeResultBlinkState(
            elapsedMs = System.currentTimeMillis() - session.phaseStartedAtMs,
            playerWon = session.resultText == "WIN"
        )
    }

    private fun battlePhaseTicks(session: BattleSession): Int {
        val elapsedMs = (System.currentTimeMillis() - session.phaseStartedAtMs).coerceAtLeast(0L)
        val stepMs = when (session.phase) {
            BattlePhase.READY_GO -> 6_000L
            BattlePhase.EVO_SEQUENCE -> 70L
            BattlePhase.MINE_ATTACK, BattlePhase.ENEMY_ATTACK -> 40L
            BattlePhase.DEVOLVE -> 500L
            BattlePhase.FINISH -> 100L
            BattlePhase.RESULT -> 1_000L
            else -> 100L
        }
        return (elapsedMs / stepMs).toInt()
    }

    private fun displayedMineHp(session: BattleSession, attackTimeline: AttackTimelineState?): Int {
        return if (session.phase == BattlePhase.ENEMY_ATTACK && attackTimeline?.damageApplied == true) {
            session.pendingMineHp ?: session.mineHp
        } else {
            session.mineHp
        }
    }

    private fun displayedEnemyHp(session: BattleSession, attackTimeline: AttackTimelineState?): Int {
        return if (session.phase == BattlePhase.MINE_ATTACK && attackTimeline?.damageApplied == true) {
            session.pendingEnemyHp ?: session.enemyHp
        } else {
            session.enemyHp
        }
    }

    private fun randomLockedCharIndex(): Int? {
        val locked = state.unlockedChars.indices.filter { !state.unlockedChars[it] }
        if (locked.isEmpty()) return null
        return locked[Random.nextInt(locked.size)]
    }

    private fun finalizeBootSequence() {
        state.currentChar = selectedChar.coerceIn(0, DigiviceV1State.DIGIMON_PROFILES.lastIndex)
        state.unlockedChars[state.currentChar] = true
        state.startSequencePending = false
        state.defeat = false
        state.distance = MAP_DISTANCES[state.area]
        state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
        bootStage = 7
        bootFrame = 0
        bootVisible = true
        bootNextAtMs = 0L
        playSound(DigiviceAudioManager.Cue.HAPPY)
        screen = if (state.battlePending) Screen.BATTLE else Screen.IDLE
        saveState()
    }

    private fun startFinishGameSequence() {
        finishOffsetX = 32
        finishAnimFrame = 1
        finishNextAtMs = System.currentTimeMillis() + 20L
        finishReturnX = 32
        finishReturnNextAtMs = 0L
        happyStartedAtMs = 0L
        happyEndsAtMs = 0L
        medicalRecoveryPending = false
        playSound(DigiviceAudioManager.Cue.FINISH)
        screen = Screen.FINISH_GAME
    }

    private fun resetBootSequence() {
        bootStage = 0
        bootFrame = 0
        bootVisible = false
        bootSlideX = 32
        bootNextAtMs = System.currentTimeMillis() + 100L
        selectedChar = state.currentChar
    }

    private fun resetProgress() {
        state = DigiviceV1State().also {
            it.lastEncounter = calculateMilestone(it.distance, it.steps, it.dpower)
        }
        selectedChar = state.currentChar
        menuIndex = 0
        statusPage = 0
        statusMode = 0
        statusDetailPage = 0
        mapPreviewArea = state.area
        finishOffsetX = 32
        finishAnimFrame = 0
        finishNextAtMs = 0L
        finishReturnX = 32
        finishReturnNextAtMs = 0L
        happyStartedAtMs = 0L
        happyEndsAtMs = 0L
        medicalRecoveryPending = false
        resetBootSequence()
        battleSession = null
        rescueSession = null
        slotSession = null
        cardSession = null
        screen = Screen.BOOT
        saveState()
    }

    private fun playSound(cue: DigiviceAudioManager.Cue) {
        audioManager.play(cue, state.soundEnabled)
    }

    private fun buildPhoneSnapshot(): PhoneVisualSnapshot {
        val encounterType = state.lastEncounter?.type ?: "none"
        return PhoneVisualSnapshot(
            screen = screen.name,
            idleClockEnabled = idleClockEnabled,
            currentChar = state.currentChar.coerceIn(BASE_SPRITES.indices),
            selectedChar = selectedChar.coerceIn(BASE_SPRITES.indices),
            menuIndex = menuIndex,
            bootStage = bootStage,
            bootFrame = bootFrame,
            bootVisible = bootVisible,
            bootSlideX = bootSlideX,
            distance = state.distance,
            steps = state.steps,
            dpower = state.dpower,
            wins = state.wins,
            battles = state.battles,
            area = state.area,
            mapPreviewArea = mapPreviewArea,
            mapBlinkVisible = currentMapBlinkVisible(),
            mapTargetDistance = currentMapTargetDistance(),
            completedAreas = state.areas.copyOf(),
            finishOffset = finishOffsetX,
            finishAnimFrame = finishAnimFrame,
            statusPage = statusPage,
            statusMode = statusMode,
            statusDetailPage = statusDetailPage,
            statusBarFrame = currentStatusBarFrame(),
            autorun = state.autorun,
            stepActive = isStepPoseActive(),
            happy = isHappyIdleActive(),
            happyAnimation = isHappyIdleAnimationFrame(),
            defeat = state.defeat,
            battlePending = state.battlePending,
            lastEncounterType = encounterType,
            finishReturnX = finishReturnX,
            slot = slotSession?.let { session ->
                PhoneSlotSnapshot(
                    roll1 = session.roll1,
                    roll2 = session.roll2,
                    phase = session.phase.name,
                    stop1 = session.stop1,
                    stop2 = session.stop2,
                    counter1 = session.counter1,
                    counter2 = session.counter2,
                    animation = session.animation,
                    resultDistance = session.resultDistance,
                    resultApplied = session.resultApplied
                )
            },
            card = cardSession?.let { session ->
                PhoneCardSnapshot(
                    roundIndex = session.roundIndex,
                    totalPointUnits = session.totalPointUnits,
                    roll1 = session.roll1,
                    roll2 = session.roll2,
                    press1 = session.press1,
                    press2 = session.press2,
                    counter = session.counter,
                    phase = session.phase.name,
                    animation = session.animation,
                    roundPointUnits = session.roundPointUnits,
                    resultDistance = session.resultDistance,
                    resultApplied = session.resultApplied
                )
            },
            battle = battleSession?.let { session ->
                val phaseTicks = battlePhaseTicks(session)
                val attackTimeline = attackTimeline(session)
                val readyGoState = if (session.phase == BattlePhase.READY_GO) currentReadyGoState(session) else null
                val evoSequenceState = if (session.phase == BattlePhase.EVO_SEQUENCE) currentEvoSequenceState(session) else null
                val finishDevolveState = if (session.phase == BattlePhase.DEVOLVE) currentFinishDevolveState(session) else null
                val finishSlideState = if (session.phase == BattlePhase.FINISH) currentFinishSlideState(session) else null
                val resultBlinkState = if (session.phase == BattlePhase.RESULT) currentResultBlinkState(session) else null
                PhoneBattleSnapshot(
                    enemyId = session.enemyId,
                    enemyName = session.enemyName,
                    boss = session.boss,
                    mineHp = displayedMineHp(session, attackTimeline),
                    enemyHp = displayedEnemyHp(session, attackTimeline),
                    currentEvo = session.currentEvo,
                    turn = session.pendingTurn ?: session.turn,
                    phase = session.phase.name,
                    phaseTicks = phaseTicks,
                    menuIndex = session.menuIndex,
                    pushPress = session.pushPress,
                    evoCharge = session.evoCharge,
                    swapIndex = session.swapIndex,
                    evoSuccess = session.evoSuccess,
                    resultText = session.resultText,
                    readyGoFrame = readyGoState?.frame ?: 0,
                    evoMenu = evoSequenceState?.currentMenu ?: 0,
                    evoPosY = evoSequenceState?.posY ?: 0,
                    evoAnimation = evoSequenceState?.animation ?: false,
                    finishEvo = finishDevolveState?.displayedEvo ?: 0,
                    finishFilter = finishDevolveState?.drawFilter ?: false,
                    finishSlideX = finishSlideState?.posX ?: 8,
                    resultAnimation = resultBlinkState?.animation ?: false,
                    attackTurn = attackTimeline?.turn ?: 0,
                    attackPosX = attackTimeline?.posX ?: 0,
                    attackAnimation = attackTimeline?.animation ?: false
                )
            },
            rescue = rescueSession?.let { rescue ->
                PhoneRescueSnapshot(
                    charIndex = rescue.charIndex,
                    charge = rescue.charge,
                    phase = rescue.phase,
                    visible = rescue.visible,
                    filter = rescue.filter
                )
            }
        )
    }

    private fun currentMapBlinkVisible(): Boolean {
        return ((System.currentTimeMillis() / 500L) % 2L) == 0L
    }

    private fun currentMapTargetDistance(): Int {
        return if (mapPreviewArea == state.area) state.distance else state.perAreaDistances[mapPreviewArea.coerceIn(state.perAreaDistances.indices)]
    }

    private fun isGameComplete(): Boolean {
        return state.areas.all { it == 2 }
    }

    private fun currentStatusBarFrame(): Int {
        val evolution = state.currentProfile().evolutions[statusDetailPage.coerceIn(0, 2)]
        val frame = if (statusMode == 0) {
            evolution.hp
        } else {
            var attack = evolution.attack
            val char = state.currentChar
            val area = state.area
            if (((char == 3 || char == 5) && area == 2) ||
                ((char == 1 || char == 4) && area == 3) ||
                ((char == 6 || char == 7) && area == 4) ||
                ((char == 0 || char == 2) && area == 5)
            ) {
                attack += 1
            }
            attack
        }
        return frame.coerceIn(0, 8)
    }

    private fun nextUnlockedChar(from: Int): Int {
        for (offset in 1..state.unlockedChars.size) {
            val candidate = (from + offset) % state.unlockedChars.size
            if (state.unlockedChars[candidate]) {
                return candidate
            }
        }
        return from
    }

    private fun saveState() {
        if (state.lastEncounter == null) {
            state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
        }
        saveRepository.save(state)
    }

    private fun calculateMilestone(distance: Int, steps: Int, dpower: Int): DigiviceEncounter {
        val currentStepMod = steps % 500
        val stepsToNext = if (currentStepMod == 0) 500 else 500 - currentStepMod
        var nextSteps = steps + stepsToNext
        var nextDistance = (distance - stepsToNext).coerceAtLeast(0)
        var nextEnergy = (dpower + (stepsToNext / 100)).coerceAtMost(99)
        var type = "battle"
        if (distance <= stepsToNext) {
            val stepsToZero = distance.coerceAtLeast(0)
            nextDistance = 0
            nextSteps = steps + stepsToZero
            nextEnergy = (dpower + (stepsToZero / 100)).coerceAtMost(99)
            type = "boss"
            if (distance == 0) {
                nextSteps = steps
            }
        }
        return DigiviceEncounter(type = type, distance = nextDistance, steps = nextSteps, energy = nextEnergy)
    }

    private fun drawFrame() {
        for (x in 0 until SIZE) {
            setPixel(x, 0)
            setPixel(x, SIZE - 1)
        }
        for (y in 0 until SIZE) {
            setPixel(0, y)
            setPixel(SIZE - 1, y)
        }
    }

    private fun drawHeader() {
        drawText("DV1", 2, 2)
        drawTinyBars(15, 2, state.dpower.coerceIn(0, 99) / 20 + 1)
    }

    private fun drawBootScreen() {
        val frame = if ((frameCounter / 10) % 2 == 0) "spr_start" else "spr_start_pop"
        drawAssetSprite(frame, 2, 6, 21, 11)
        drawText("AGO", 7, 18)
    }

    private fun drawSelectScreen() {
        val profile = DigiviceV1State.DIGIMON_PROFILES[selectedChar]
        drawText("PICK", 4, 6)
        drawText(profile.shortCode, 4, 13)
        drawAssetSprite(BASE_SPRITES[selectedChar], 15, 8, 9, 9)
        drawText("${selectedChar + 1}", 18, 18)
    }

    private fun drawIdleScreen() {
        val profile = state.currentProfile()
        val encounter = state.lastEncounter ?: calculateMilestone(state.distance, state.steps, state.dpower)
        val happy = isHappyIdleActive()
        val happyAnimation = isHappyIdleAnimationFrame()
        drawText(profile.shortCode, 3, 6)
        val spriteName = when {
            happy && !happyAnimation -> HAPPY_SPRITES[state.currentChar]
            state.defeat -> DEFEAT_SPRITES[state.currentChar]
            isStepPoseActive() -> STEP_SPRITES[state.currentChar]
            else -> BASE_SPRITES[state.currentChar]
        }
        drawAssetSprite(spriteName, 14, 7, 10, 10, frameCounter / 8)
        drawText(state.distance.coerceAtLeast(0).toString().padStart(4, '0').takeLast(4), 3, 13)
        drawText("P${state.dpower.toString().padStart(2, '0')}", 3, 19)
        val marker = when {
            happy && !happyAnimation -> "HAP"
            state.defeat -> "DMG"
            state.battlePending -> "BAT"
            encounter.type == "boss" -> "BOS"
            else -> "GO"
        }
        drawText(marker, 14, 19)
    }

    private fun drawMenuScreen() {
        drawText("MENU", 3, 6)
        drawText(MENU_ITEMS[menuIndex], 5, 13)
    }

    private fun drawSlotScreen() {
        val session = slotSession ?: return
        drawAssetSprite(SLOT_SPRITES[session.roll1], 0, 5, 16, 16, if (session.animation) 1 else 0)
        drawAssetSprite(SLOT_SPRITES[session.roll2], 16, 5, 16, 16, if (session.animation) 1 else 0)
    }

    private fun drawSlotResultScreen() {
        drawAssetSprite("spr_menu_stats", 0, 5, 25, 16, 0)
        drawText(state.distance.toString(), 11, 13)
    }

    private fun drawCardScreen() {
        val session = cardSession ?: return
        when (session.phase) {
            CardPhase.REVEAL -> {
                drawAssetSprite("spr_card_digivice_v1", 0, 5, 16, 16, session.counter)
                drawAssetSprite("spr_card_digivice_v1", 16, 5, 16, 16, session.counter)
            }
            CardPhase.CHOICE -> {
                drawAssetSprite(CARD_SPRITES[session.roll1], 0, 5, 16, 16, 0)
                drawAssetSprite(CARD_SPRITES[session.roll2], 16, 5, 16, 16, 0)
            }
            CardPhase.SCORE_DISPLAY -> {
                drawCardResultSprite(session.roll1, session.press1, 0, session.animation)
                drawCardResultSprite(session.roll2, session.press2, 16, session.animation)
            }
            CardPhase.RESULT_SCREEN -> Unit
        }
    }

    private fun drawCardResultScreen() {
        drawAssetSprite("spr_menu_stats", 0, 5, 25, 16, 0)
        drawText(state.distance.toString(), 11, 13)
    }

    private fun drawFinishReturnScreen() {
        drawAssetSprite(BASE_SPRITES[state.currentChar], finishReturnX.coerceIn(0, 32), 7, 10, 10)
    }

    private fun drawCardResultSprite(roll: Int, pressed: Boolean, x: Int, animation: Boolean) {
        val cardId = CARD_IDS[roll]
        when {
            cardId == -1 && pressed -> drawAssetSprite("spr_attack_d3_v1_small", x, 5, 16, 16, if (animation) 1 else 0)
            cardId != -1 && pressed -> drawAssetSprite(DEFEAT_SPRITES[cardId], x, 5, 16, 16)
            cardId == -1 -> drawAssetSprite(CARD_SPRITES[roll], x, 5, 16, 16, if (animation) 1 else 0)
            animation -> drawAssetSprite(BASE_SPRITES[cardId], x, 5, 16, 16)
            else -> drawAssetSprite(HAPPY_SPRITES[cardId], x, 5, 16, 16)
        }
    }

    private fun drawStatusPagerScreen() {
        drawAssetSprite("spr_menu_stats", 0, 5, 25, 16, statusPage)
        when (statusPage % 4) {
            0 -> drawText(state.distance.toString(), 11, 13)
            1 -> drawText(state.steps.toString(), 11, 13)
            2 -> drawText(state.dpower.toString(), 14, 13)
            3 -> {
                drawText(state.wins.toString(), 3, 7)
                drawText(state.battles.toString(), 17, 7)
                val percentage = if (state.battles > 0) ((state.wins * 100f) / state.battles.toFloat()).toInt() else 0
                drawText(percentage.toString(), 11, 13)
            }
        }
    }

    private fun drawStatusMenuScreen() {
        drawAssetSprite("spr_menu_stats_digivice_v1", 0, 5, 25, 16, statusMode)
    }

    private fun drawStatusDetailScreen() {
        drawAssetSprite("spr_hp_bar_digivice_v1", 0, 5, 25, 16, currentStatusBarFrame())
        drawAssetSprite(
            EVOLUTION_SPRITES[state.currentChar][statusDetailPage.coerceIn(0, 2)],
            8,
            7,
            16,
            16,
            frameCounter / 12
        )
    }

    private fun drawMapScreen() {
        drawText("AREA", 3, 6)
        drawText("${mapPreviewArea + 1}", 10, 13)
        drawText("DIST:${state.perAreaDistances[mapPreviewArea.coerceIn(state.perAreaDistances.indices)]}", 3, 19)
        for (index in MAP_DISTANCES.indices) {
            val y = 20
            val x = 3 + index * 3
            if (index <= mapPreviewArea) {
                setPixel(x, y)
                setPixel(x + 1, y)
            } else {
                setPixel(x, y)
            }
        }
    }

    private fun drawBattleScreen() {
        val session = battleSession ?: return
        drawText(if (session.boss) "BOSS" else "BTTL", 2, 5)
        when (session.phase) {
            BattlePhase.ALERT -> {
                drawAssetSprite("spr_battle_alert_digivice_v1", 2, 9, 20, 7)
                drawAssetSprite(ATTACK_SPRITES[state.currentChar], 9, 15, 8, 8, frameCounter / 10)
            }
            BattlePhase.EVO -> Unit
            BattlePhase.MENU -> {
                drawText(BATTLE_ITEMS[session.menuIndex], 2, 12)
                drawText("M${session.mineHp}", 2, 19)
                drawText("E${session.enemyHp}", 13, 19)
                drawAssetSprite(ATTACK_SPRITES[state.currentChar], 1, 8, 9, 9, frameCounter / 10)
                drawAssetSprite(ENEMY_SPRITES[session.enemyId], 11, 8, 10, 10, frameCounter / 10)
            }
            BattlePhase.PUSH -> {
                drawText("PUSH", 2, 11)
                drawText(session.pushPress.toString().padStart(2, '0'), 8, 18)
                drawChargeBar(16, 18, session.pushPress / 4)
                drawAssetSprite(ATTACK_SPRITES[state.currentChar], 11, 8, 10, 10, frameCounter / 6)
            }
            BattlePhase.READY_GO -> {
                drawAssetSprite("spr_ready_go_d3_v1", 0, 8, 25, 16, currentReadyGoState(session).frame.coerceIn(0, 1))
            }
            BattlePhase.EVO_SEQUENCE -> {
                drawEvoSequencePreview(session)
            }
            BattlePhase.DEVOLVE -> {
                drawFinishDevolvePreview(session)
            }
            BattlePhase.SWAP -> {
                drawText("SWAP", 2, 11)
                drawText(DigiviceV1State.DIGIMON_PROFILES[session.swapIndex].shortCode, 4, 18)
                drawAssetSprite(BASE_SPRITES[session.swapIndex], 15, 9, 10, 10, frameCounter / 10)
            }
            BattlePhase.MINE_ATTACK -> drawMineAttackPreview(session)
            BattlePhase.ENEMY_ATTACK -> drawEnemyAttackPreview(session)
            BattlePhase.FINISH -> drawFinishBattlePreview(session)
            BattlePhase.RESULT -> drawResultBattlePreview(session)
        }
    }

    private fun drawMineAttackPreview(session: BattleSession) {
        val timeline = attackTimeline(session) ?: return
        val currentSprite = if (session.currentEvo == 0) ATTACK_SPRITES[state.currentChar] else EVOLUTION_SPRITES[state.currentChar][session.currentEvo]
        val currentX = if (session.currentEvo == 0) 16 else 8
        val attackFrame = if (timeline.animation) 1 else 0
        drawText("ATK", 4, 11)
        when {
            timeline.turn == 0 -> {
                drawAssetSprite(currentSprite, currentX, 8, 10, 10, attackFrame)
            }
            timeline.turn in 1..17 -> {
                drawAssetSprite(currentSprite, currentX, 8, 10, 10, attackFrame)
                val projectileX = if (session.currentEvo == 0) 8 + timeline.posX else timeline.posX
                drawAssetSprite("spr_attack_digivice_v1", projectileX, 8, 8, 8)
                if (session.currentEvo != 0) {
                    drawAssetSprite("spr_attack_digivice_v1", projectileX, 16, 8, 8)
                }
            }
            timeline.turn in 18..34 -> {
                drawAssetSprite(ENEMY_SPRITES[session.enemyId], 0, 8, 10, 10)
                val projectileX = 32 + timeline.posX
                drawAssetSprite("spr_attack_digivice_v1", projectileX, 8, 8, 8)
                if (session.currentEvo != 0) {
                    drawAssetSprite("spr_attack_digivice_v1", projectileX, 16, 8, 8)
                }
            }
            timeline.turn in 35..45 -> {
                val effect = if (session.boss) "spr_attack_d3_v1" else "spr_attack_d3_v1_small"
                drawAssetSprite(effect, 0, 8, 16, 16, if (timeline.animation) 1 else 0)
            }
            else -> {
                drawAssetSprite("spr_hp_bar_digivice_v1", 0, 8, 25, 16, displayedEnemyHp(session, timeline).coerceIn(0, 8))
                drawAssetSprite(ENEMY_SPRITES[session.enemyId], 4, 8, 10, 10)
            }
        }
    }

    private fun drawEnemyAttackPreview(session: BattleSession) {
        val timeline = attackTimeline(session) ?: return
        val currentSprite = if (session.currentEvo == 0) BASE_SPRITES[state.currentChar] else EVOLUTION_SPRITES[state.currentChar][session.currentEvo]
        val currentX = if (session.currentEvo == 0) 16 else 8
        drawText("ATK", 4, 11)
        when {
            timeline.turn == 0 -> {
                drawAssetSprite(ENEMY_SPRITES[session.enemyId], 0, 8, 10, 10, if (timeline.animation) 1 else 0)
            }
            timeline.turn in 1..17 -> {
                drawAssetSprite(ENEMY_SPRITES[session.enemyId], 0, 8, 10, 10, if (timeline.animation) 1 else 0)
                val projectileX = if (session.boss) 32 - timeline.posX else 24 - timeline.posX
                drawAssetSpriteMirrored("spr_attack_digivice_v1", projectileX, 8, 8, 8)
                if (session.boss) {
                    drawAssetSpriteMirrored("spr_attack_digivice_v1", projectileX, 16, 8, 8)
                }
            }
            timeline.turn in 18..34 -> {
                drawAssetSprite(currentSprite, currentX, 8, 10, 10)
                drawAssetSpriteMirrored("spr_attack_digivice_v1", timeline.posX, 8, 8, 8)
                if (session.boss) {
                    drawAssetSpriteMirrored("spr_attack_digivice_v1", timeline.posX, 16, 8, 8)
                }
            }
            timeline.turn in 35..45 -> {
                val effect = if (session.currentEvo != 0) "spr_attack_d3_v1" else "spr_attack_d3_v1_small"
                val effectX = if (session.currentEvo != 0) 8 else 16
                drawAssetSprite(effect, effectX, 8, 16, 16, if (timeline.animation) 1 else 0)
            }
            else -> {
                drawAssetSprite("spr_hp_bar_digivice_v1", 0, 8, 25, 16, displayedMineHp(session, timeline).coerceIn(0, 8))
                drawAssetSprite(currentSprite, currentX, 8, 10, 10)
            }
        }
    }

    private fun drawEvoSequencePreview(session: BattleSession) {
        val evoState = currentEvoSequenceState(session)
        val currentSprite = EVOLUTION_SPRITES[this.state.currentChar][session.currentEvo.coerceIn(0, 2)]
        val nextSprite = EVOLUTION_SPRITES[this.state.currentChar][(session.currentEvo + 1).coerceAtMost(2)]
        val currentX = if ((spriteLibrary.getFrame(currentSprite, 0)?.width ?: 16) > 16) 4 else 8
        when {
            evoState.currentMenu < 11 -> {
                drawAssetSprite("spr_evo_digivice_v1", 0, 8, 25, 16, evoState.currentMenu.coerceIn(0, 11))
            }
            evoState.currentMenu in 11..15 -> {
                if (!evoState.animation) {
                    drawAssetSprite(currentSprite, currentX, 8, 16, 16)
                }
            }
            evoState.currentMenu in 16..17 -> {
                drawAssetSprite(currentSprite, currentX, 8, 16, 16)
                drawAssetSprite("spr_evo_filter", 0, 8, 25, 16)
            }
            evoState.currentMenu in 18..24 -> {
                if (session.evoSuccess && evoState.currentMenu % 2 == 0) {
                    drawAssetSprite(nextSprite, 4, 0, 24, 24)
                } else {
                    drawAssetSprite(currentSprite, currentX, 8, 16, 16)
                }
                drawAssetSprite("spr_evo_filter", 0, 8, 25, 16)
            }
            else -> {
                if (session.evoSuccess) {
                    drawAssetSprite(nextSprite, 4, evoState.posY, 24, 24)
                } else {
                    drawAssetSprite(currentSprite, currentX, 8, 16, 16)
                }
            }
        }
    }

    private fun drawFinishDevolvePreview(session: BattleSession) {
        val finishState = currentFinishDevolveState(session)
        val spriteName = EVOLUTION_SPRITES[state.currentChar][finishState.displayedEvo.coerceIn(0, 2)]
        val x = if (finishState.displayedEvo == 0) 15 else 11
        val size = if (finishState.displayedEvo == 0) 10 else 14
        drawAssetSprite(spriteName, x, 8, size, 10)
        if (finishState.drawFilter) {
            drawAssetSprite("spr_evo_filter", 0, 8, 25, 16)
        }
    }

    private fun drawFinishBattlePreview(session: BattleSession) {
        val finishState = currentFinishSlideState(session)
        drawAssetSprite(BASE_SPRITES[state.currentChar], finishState.posX.coerceIn(0, 25), 8, 10, 10)
    }

    private fun drawResultBattlePreview(session: BattleSession) {
        val blinkState = currentResultBlinkState(session)
        val playerWon = session.resultText == "WIN"
        val spriteName = if (playerWon) HAPPY_SPRITES[state.currentChar] else DEFEAT_SPRITES[state.currentChar]
        val visibleSprite = if (blinkState.animation) BASE_SPRITES[state.currentChar] else spriteName
        drawAssetSprite(visibleSprite, 15, 8, 10, 10)
        if (playerWon && !blinkState.animation) {
            drawAssetSprite("spr_happy", 0, 8, 8, 8)
        }
        drawText(session.resultText, 3, 19)
    }

    private fun drawRescueScreen() {
        val rescue = rescueSession ?: return
        val profile = DigiviceV1State.DIGIMON_PROFILES[rescue.charIndex]
        drawText("SAVE", 2, 6)
        drawText(profile.shortCode, 4, 12)
        drawAssetSprite(BASE_SPRITES[rescue.charIndex], 15, 9, 10, 10, frameCounter / 10)
        drawChargeBar(4, 20, rescue.charge)
    }

    private fun drawPhoneBoot(screenRect: RectF) {
        drawPhoneAssetSprite("spr_start", screenRect.left.toInt() + 12, screenRect.top.toInt() + 12, 112, 40)
        phoneCanvas.drawText("Press A to begin", screenRect.left + 18f, screenRect.bottom - 18f, phoneSmallTextPaint)
    }

    private fun drawPhoneSelect(screenRect: RectF) {
        val profile = DigiviceV1State.DIGIMON_PROFILES[selectedChar]
        phoneCanvas.drawText("SELECT PARTNER", screenRect.left + 14f, screenRect.top + 20f, phoneSmallTextPaint)
        phoneCanvas.drawText(profile.name, screenRect.left + 14f, screenRect.top + 42f, phoneTextPaint)
        drawPhoneAssetSprite(BASE_SPRITES[selectedChar], screenRect.left.toInt() + 84, screenRect.top.toInt() + 24, 40, 40, frameCounter / 10)
        phoneCanvas.drawText("B: cycle", screenRect.left + 14f, screenRect.bottom - 28f, phoneSmallTextPaint)
        phoneCanvas.drawText("A: confirm", screenRect.left + 14f, screenRect.bottom - 12f, phoneSmallTextPaint)
    }

    private fun drawPhoneIdle(screenRect: RectF) {
        val encounter = state.lastEncounter ?: calculateMilestone(state.distance, state.steps, state.dpower)
        val happy = isHappyIdleActive()
        val happyAnimation = isHappyIdleAnimationFrame()
        val spriteName = when {
            happy && !happyAnimation -> HAPPY_SPRITES[state.currentChar]
            state.defeat -> DEFEAT_SPRITES[state.currentChar]
            isStepPoseActive() -> STEP_SPRITES[state.currentChar]
            else -> BASE_SPRITES[state.currentChar]
        }
        drawPhoneAssetSprite(spriteName, screenRect.left.toInt() + 88, screenRect.top.toInt() + 20, 40, 40, frameCounter / 8)
        phoneCanvas.drawText(state.currentProfile().name, screenRect.left + 14f, screenRect.top + 20f, phoneTextPaint)
        phoneCanvas.drawText("Distance ${state.distance}", screenRect.left + 14f, screenRect.top + 46f, phoneSmallTextPaint)
        phoneCanvas.drawText("Steps ${state.steps}", screenRect.left + 14f, screenRect.top + 62f, phoneSmallTextPaint)
        phoneCanvas.drawText("D-Power ${state.dpower}", screenRect.left + 14f, screenRect.top + 78f, phoneSmallTextPaint)
        val label = when {
            happy && !happyAnimation -> "Happy"
            state.battlePending -> "Battle ready"
            encounter.type == "boss" -> "Boss ahead"
            else -> "Walking"
        }
        phoneCanvas.drawText(label, screenRect.left + 14f, screenRect.bottom - 18f, phoneSmallTextPaint)
    }

    private fun drawPhoneMenu(screenRect: RectF) {
        phoneCanvas.drawText("MENU", screenRect.left + 14f, screenRect.top + 20f, phoneTextPaint)
        for (index in MENU_ITEMS.indices) {
            val prefix = if (index == menuIndex) "> " else "  "
            phoneCanvas.drawText(prefix + MENU_ITEMS[index], screenRect.left + 14f, screenRect.top + 42f + (index * 16f), phoneSmallTextPaint)
        }
    }

    private fun drawPhoneStatus(screenRect: RectF) {
        val evoIndex = statusDetailPage.coerceIn(0, 2)
        val evo = state.currentProfile().evolutions[evoIndex]
        phoneCanvas.drawText(state.currentProfile().name, screenRect.left + 14f, screenRect.top + 20f, phoneTextPaint)
        phoneCanvas.drawText("Form ${evo.name}", screenRect.left + 14f, screenRect.top + 42f, phoneSmallTextPaint)
        phoneCanvas.drawText("Level ${evo.level}", screenRect.left + 14f, screenRect.top + 58f, phoneSmallTextPaint)
        phoneCanvas.drawText("HP ${evo.hp}", screenRect.left + 14f, screenRect.top + 74f, phoneSmallTextPaint)
        phoneCanvas.drawText("ATK ${evo.attack}", screenRect.left + 14f, screenRect.top + 90f, phoneSmallTextPaint)
        drawPhoneAssetSprite(EVOLUTION_SPRITES[state.currentChar][evoIndex], screenRect.left.toInt() + 88, screenRect.top.toInt() + 24, 40, 40, frameCounter / 10)
    }

    private fun drawPhoneMap(screenRect: RectF) {
        phoneCanvas.drawText("MAP", screenRect.left + 14f, screenRect.top + 20f, phoneTextPaint)
        phoneCanvas.drawText("Area ${mapPreviewArea + 1}", screenRect.left + 14f, screenRect.top + 42f, phoneSmallTextPaint)
        phoneCanvas.drawText("Distance ${state.perAreaDistances[mapPreviewArea.coerceIn(state.perAreaDistances.indices)]}", screenRect.left + 14f, screenRect.top + 58f, phoneSmallTextPaint)
        for (index in MAP_DISTANCES.indices) {
            val x = screenRect.left + 16f + index * 16f
            val y = screenRect.top + 86f
            val active = index <= mapPreviewArea
            if (active) {
                phoneCanvas.drawCircle(x, y, 4f, phoneFramePaint.apply { style = Paint.Style.FILL })
            } else {
                phoneCanvas.drawCircle(x, y, 3f, phoneFramePaint.apply { style = Paint.Style.STROKE })
            }
        }
        phoneFramePaint.style = Paint.Style.STROKE
    }

    private fun drawPhoneBattle(screenRect: RectF) {
        val session = battleSession ?: return
        phoneCanvas.drawText(if (session.boss) "BOSS BATTLE" else "BATTLE", screenRect.left + 14f, screenRect.top + 20f, phoneTextPaint)
        phoneCanvas.drawText(session.enemyName, screenRect.left + 14f, screenRect.top + 42f, phoneSmallTextPaint)
        phoneCanvas.drawText("You ${session.mineHp}HP", screenRect.left + 14f, screenRect.top + 58f, phoneSmallTextPaint)
        phoneCanvas.drawText("Enemy ${session.enemyHp}HP", screenRect.left + 14f, screenRect.top + 74f, phoneSmallTextPaint)
        phoneCanvas.drawText("Turn ${session.turn + 1}", screenRect.left + 14f, screenRect.top + 90f, phoneSmallTextPaint)
        drawPhoneAssetSprite(ATTACK_SPRITES[state.currentChar], screenRect.left.toInt() + 90, screenRect.top.toInt() + 18, 28, 28, frameCounter / 8)
        drawPhoneAssetSprite(ENEMY_SPRITES[session.enemyId], screenRect.left.toInt() + 112, screenRect.top.toInt() + 18, 24, 24, frameCounter / 10)
        val footer = when (session.phase) {
            BattlePhase.ALERT -> "A to continue"
            BattlePhase.MENU -> BATTLE_ITEMS[session.menuIndex]
            BattlePhase.PUSH -> "Mash B ${session.pushPress}"
            BattlePhase.EVO -> "Charge ${session.evoCharge}"
            BattlePhase.READY_GO -> "READY ${session.evoCharge}"
            BattlePhase.EVO_SEQUENCE -> "EVOLVE"
            BattlePhase.DEVOLVE -> "DEVOLVE"
            BattlePhase.SWAP -> "Swap ${DigiviceV1State.DIGIMON_PROFILES[session.swapIndex].name}"
            BattlePhase.MINE_ATTACK -> "ATTACK"
            BattlePhase.ENEMY_ATTACK -> "DAMAGE"
            BattlePhase.FINISH, BattlePhase.RESULT -> session.resultText
        }
        phoneCanvas.drawText(footer, screenRect.left + 14f, screenRect.bottom - 18f, phoneSmallTextPaint)
    }

    private fun drawPhoneRescue(screenRect: RectF) {
        val rescue = rescueSession ?: return
        val profile = DigiviceV1State.DIGIMON_PROFILES[rescue.charIndex]
        phoneCanvas.drawText("RESCUE", screenRect.left + 14f, screenRect.top + 20f, phoneTextPaint)
        phoneCanvas.drawText(profile.name, screenRect.left + 14f, screenRect.top + 42f, phoneSmallTextPaint)
        drawPhoneAssetSprite(BASE_SPRITES[rescue.charIndex], screenRect.left.toInt() + 88, screenRect.top.toInt() + 24, 40, 40, frameCounter / 10)
        phoneCanvas.drawText("Mash B to save", screenRect.left + 14f, screenRect.top + 74f, phoneSmallTextPaint)
        phoneCanvas.drawText("Charge ${rescue.charge}", screenRect.left + 14f, screenRect.top + 90f, phoneSmallTextPaint)
        for (index in 0 until 10) {
            val x = screenRect.left + 14f + index * 10f
            val filled = index < rescue.charge.coerceAtMost(20) / 2
            if (filled) {
                phoneCanvas.drawRect(x, screenRect.bottom - 18f, x + 6f, screenRect.bottom - 10f, phoneFramePaint.apply { style = Paint.Style.FILL })
            } else {
                phoneCanvas.drawRect(x, screenRect.bottom - 18f, x + 6f, screenRect.bottom - 10f, phoneFramePaint.apply { style = Paint.Style.STROKE })
            }
        }
        phoneFramePaint.style = Paint.Style.STROKE
    }

    private fun drawPulse(x: Int, y: Int) {
        val radius = 1 + (frameCounter % 4)
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= radius * radius) {
                    setPixel(x + dx, y + dy)
                }
            }
        }
    }

    private fun drawTinyBars(left: Int, top: Int, count: Int) {
        for (i in 0 until count.coerceIn(0, 5)) {
            setPixel(left + i * 2, top)
            setPixel(left + i * 2, top + 1)
        }
    }

    private fun drawChargeBar(left: Int, top: Int, count: Int) {
        for (i in 0 until count.coerceIn(0, 10)) {
            setPixel(left + i, top)
            if (i % 2 == 0) {
                setPixel(left + i, top - 1)
            }
        }
    }

    private fun drawSpriteForIndex(index: Int, left: Int, top: Int) {
        val pattern = when (index % 4) {
            0 -> listOf(
                ".111.",
                "11111",
                "10101",
                ".111.",
                ".1.1."
            )
            1 -> listOf(
                ".111.",
                "11011",
                "11111",
                ".111.",
                "1...1"
            )
            2 -> listOf(
                ".111.",
                "11111",
                "01110",
                ".111.",
                "1.1.1"
            )
            else -> listOf(
                "11111",
                "10101",
                "11111",
                ".111.",
                "..1.."
            )
        }
        drawPattern(pattern, left, top)
    }

    private fun drawAssetSprite(name: String, left: Int, top: Int, maxWidth: Int, maxHeight: Int, frameIndex: Int = 0) {
        val sprite = spriteLibrary.getFrame(name, frameIndex) ?: return
        val srcWidth = sprite.width
        val srcHeight = sprite.height
        if (srcWidth <= 0 || srcHeight <= 0) return

        val scale = minOf(
            maxWidth.toFloat() / srcWidth.toFloat(),
            maxHeight.toFloat() / srcHeight.toFloat(),
            1f
        )
        val targetWidth = (srcWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (srcHeight * scale).roundToInt().coerceAtLeast(1)
        val offsetX = left + ((maxWidth - targetWidth) / 2)
        val offsetY = top + ((maxHeight - targetHeight) / 2)

        for (y in 0 until targetHeight) {
            val srcY = (y * srcHeight) / targetHeight
            for (x in 0 until targetWidth) {
                val srcX = (x * srcWidth) / targetWidth
                val pixel = sprite.getPixel(srcX, srcY)
                if (Color.alpha(pixel) > 32) {
                    setPixel(offsetX + x, offsetY + y)
                }
            }
        }
    }

    private fun drawAssetSpriteMirrored(name: String, left: Int, top: Int, maxWidth: Int, maxHeight: Int, frameIndex: Int = 0) {
        val sprite = spriteLibrary.getFrame(name, frameIndex) ?: return
        val srcWidth = sprite.width
        val srcHeight = sprite.height
        if (srcWidth <= 0 || srcHeight <= 0) return

        val scale = minOf(
            maxWidth.toFloat() / srcWidth.toFloat(),
            maxHeight.toFloat() / srcHeight.toFloat(),
            1f
        )
        val targetWidth = (srcWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (srcHeight * scale).roundToInt().coerceAtLeast(1)
        val offsetX = left + ((maxWidth - targetWidth) / 2)
        val offsetY = top + ((maxHeight - targetHeight) / 2)

        for (y in 0 until targetHeight) {
            val srcY = (y * srcHeight) / targetHeight
            for (x in 0 until targetWidth) {
                val srcX = srcWidth - 1 - ((x * srcWidth) / targetWidth)
                val pixel = sprite.getPixel(srcX, srcY)
                if (Color.alpha(pixel) > 32) {
                    setPixel(offsetX + x, offsetY + y)
                }
            }
        }
    }

    private fun drawPhoneAssetSprite(name: String, left: Int, top: Int, maxWidth: Int, maxHeight: Int, frameIndex: Int = 0) {
        val sprite = spriteLibrary.getFrame(name, frameIndex) ?: return
        val srcWidth = sprite.width
        val srcHeight = sprite.height
        if (srcWidth <= 0 || srcHeight <= 0) return

        val scale = minOf(
            maxWidth.toFloat() / srcWidth.toFloat(),
            maxHeight.toFloat() / srcHeight.toFloat(),
            1f
        )
        val targetWidth = (srcWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (srcHeight * scale).roundToInt().coerceAtLeast(1)
        phoneSrcRect.set(0, 0, srcWidth, srcHeight)
        phoneDstRect.set(left, top, left + targetWidth, top + targetHeight)
        phoneCanvas.drawBitmap(sprite, phoneSrcRect, phoneDstRect, phoneSpritePaint)
    }

    private fun drawText(text: String, left: Int, top: Int) {
        var x = left
        text.uppercase().forEach { char ->
            drawGlyphChar(char, x, top)
            x += 4
        }
    }

    private fun drawGlyphChar(char: Char, left: Int, top: Int) {
        val pattern = FONT[char] ?: return
        drawPattern(pattern, left, top)
    }

    private fun drawPattern(pattern: List<String>, left: Int, top: Int) {
        for (row in pattern.indices) {
            for (col in pattern[row].indices) {
                if (pattern[row][col] != '.' && pattern[row][col] != '0') {
                    setPixel(left + col, top + row)
                }
            }
        }
    }

    private fun setPixel(x: Int, y: Int) {
        if (x !in 0 until SIZE || y !in 0 until SIZE) return
        pixels[y * SIZE + x] = ON
    }
}
