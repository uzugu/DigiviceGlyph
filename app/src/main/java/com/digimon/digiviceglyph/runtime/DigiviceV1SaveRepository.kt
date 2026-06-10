package com.digimon.digiviceglyph.runtime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DigiviceV1SaveRepository(context: Context) {
    companion object {
        private const val SAVE_FILE = "digivice_v1_eu.dat"
        private const val BACKUP_FILE = "digivice_v1_eu_back.dat"
        private const val SAVE_KEY = "D1g1W0rld_S4v3_K3y_2025"
    }

    private val filesDir: File = context.filesDir

    fun load(): DigiviceV1State? {
        return loadFile(File(filesDir, SAVE_FILE)) ?: loadBackup()
    }

    fun save(state: DigiviceV1State): Boolean {
        val saveFile = File(filesDir, SAVE_FILE)
        val backupFile = File(filesDir, BACKUP_FILE)
        return runCatching {
            if (saveFile.exists()) {
                saveFile.copyTo(backupFile, overwrite = true)
            }
            saveFile.writeText(buildSaveJson(state).toString())
            true
        }.getOrDefault(false)
    }

    private fun loadBackup(): DigiviceV1State? {
        val backupFile = File(filesDir, BACKUP_FILE)
        if (!backupFile.exists()) return null
        val saveFile = File(filesDir, SAVE_FILE)
        return runCatching {
            backupFile.copyTo(saveFile, overwrite = true)
            loadFile(saveFile)
        }.getOrNull()
    }

    private fun loadFile(file: File): DigiviceV1State? {
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText())
            DigiviceV1State(
                startSequencePending = loadBoolean(root, "start_digivice_v1", true),
                soundEnabled = loadBoolean(root, "sound_digivice_v1", true),
                gridEnabled = loadBoolean(root, "grid_digivice_v1", false),
                scale = loadInt(root, "scale_digivice_v1", 0),
                vista = loadInt(root, "vista_digivice_v1", 0),
                distance = loadInt(root, "distance_digivice_v1", 10000),
                steps = loadInt(root, "steps_digivice_v1", 0),
                dpower = loadInt(root, "dpower_digivice_v1", 0).coerceIn(0, 99),
                battles = loadInt(root, "battle_digivice_v1", 0),
                wins = loadInt(root, "wins_digivice_v1", 0),
                currentChar = loadInt(root, "char_digivice_v1", 0).coerceIn(0, 7),
                evoLevel = inferEvolutionLevel(loadInt(root, "wins_digivice_v1", 0)),
                defeat = loadBoolean(root, "defeat_digivice_v1", false),
                battlePending = loadBoolean(root, "battle_start_digivice_v1", false),
                eventPending = false,
                connectMode = false,
                autorun = false,
                area = loadInt(root, "area_digivice_v1", 0).coerceIn(0, 6),
                areas = loadIntArray(root, "areas_digivice_v1", intArrayOf(1, 1, 1, 1, 1, 1, 1), 7),
                unlockedChars = loadBooleanArray(root, "chars_digivice_v1", BooleanArray(8), 8)
            ).also { state ->
                state.lastEncounter = calculateMilestone(state.distance, state.steps, state.dpower)
            }
        }.getOrNull()
    }

    private fun buildSaveJson(state: DigiviceV1State): JSONObject {
        val saveDate = System.currentTimeMillis().toString()
        val checksum = "${state.steps}${state.currentChar}${state.area}$saveDate"
        return JSONObject().apply {
            putEncrypted("distance_digivice_v1", state.distance)
            putEncrypted("steps_digivice_v1", state.steps)
            putEncrypted("dpower_digivice_v1", state.dpower)
            putEncrypted("battle_digivice_v1", state.battles)
            putEncrypted("wins_digivice_v1", state.wins)
            putEncrypted("char_digivice_v1", state.currentChar)
            putEncrypted("defeat_digivice_v1", state.defeat)
            putEncrypted("battle_start_digivice_v1", state.battlePending)
            putEncrypted("chars_digivice_v1", JSONArray(state.unlockedChars.map { it }.toList()))
            putEncrypted("area_digivice_v1", state.area)
            putEncrypted("areas_digivice_v1", JSONArray(state.areas.map { it }.toList()))
            putEncrypted("start_digivice_v1", state.startSequencePending)
            putEncrypted("sound_digivice_v1", state.soundEnabled)
            putEncrypted("scale_digivice_v1", state.scale)
            putEncrypted("vista_digivice_v1", state.vista)
            putEncrypted("grid_digivice_v1", state.gridEnabled)
            put("save_date", encrypt(saveDate))
            put("checksum", encrypt(checksum))
            put("game_version", encrypt("1.0.0"))
        }
    }

    private fun JSONObject.putEncrypted(name: String, value: Any) {
        val raw = when (value) {
            is JSONArray -> value.toString()
            else -> value.toString()
        }
        put(name, encrypt(raw))
    }

    private fun loadInt(root: JSONObject, name: String, fallback: Int): Int {
        return decryptOrNull(root, name)?.toIntOrNull() ?: fallback
    }

    private fun loadBoolean(root: JSONObject, name: String, fallback: Boolean): Boolean {
        return when (decryptOrNull(root, name)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> fallback
        }
    }

    private fun loadIntArray(root: JSONObject, name: String, fallback: IntArray, size: Int): IntArray {
        return runCatching {
            val decoded = decryptOrNull(root, name) ?: return fallback
            val array = JSONArray(decoded)
            IntArray(size) { index -> array.optInt(index, fallback.getOrElse(index) { 0 }) }
        }.getOrDefault(fallback)
    }

    private fun loadBooleanArray(root: JSONObject, name: String, fallback: BooleanArray, size: Int): BooleanArray {
        return runCatching {
            val decoded = decryptOrNull(root, name) ?: return fallback
            val array = JSONArray(decoded)
            BooleanArray(size) { index -> array.optBoolean(index, fallback.getOrElse(index) { false }) }
        }.getOrDefault(fallback)
    }

    private fun decryptOrNull(root: JSONObject, name: String): String? {
        if (!root.has(name)) return null
        return decrypt(root.optString(name, ""))
    }

    private fun encrypt(raw: String): String {
        return raw.mapIndexed { index, char ->
            val keyChar = SAVE_KEY[index % SAVE_KEY.length]
            (char.code xor keyChar.code).toString()
        }.joinToString("-")
    }

    private fun decrypt(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.split('-')
            .mapIndexedNotNull { index, token ->
                token.toIntOrNull()?.let { value ->
                    val keyChar = SAVE_KEY[index % SAVE_KEY.length]
                    (value xor keyChar.code).toChar()
                }
            }
            .joinToString(separator = "")
    }

    private fun inferEvolutionLevel(wins: Int): Int {
        return when {
            wins >= 8 -> 2
            wins >= 3 -> 1
            else -> 0
        }
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
            type = "boss"
            nextSteps = steps + stepsToZero
            nextEnergy = (dpower + (stepsToZero / 100)).coerceAtMost(99)
            if (distance == 0) {
                nextSteps = steps
            }
        }
        return DigiviceEncounter(type = type, distance = nextDistance, steps = nextSteps, energy = nextEnergy)
    }
}
