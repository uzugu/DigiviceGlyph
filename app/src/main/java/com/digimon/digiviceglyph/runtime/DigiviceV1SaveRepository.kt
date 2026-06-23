package com.digimon.digiviceglyph.runtime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DigiviceV1SaveRepository(context: Context) {
    companion object {
        private const val COMPAT_SAVE_FILE = "digivice_v1_eu.dat"
        private const val COMPAT_BACKUP_FILE = "digivice_v1_eu_back.dat"
        private const val LEGACY_SAVE_FILE = "dpower_v1_eu.dat"
        private const val LEGACY_BACKUP_FILE = "dpower_v1_eu_back.dat"
        private const val SAVE_KEY = "D1g1W0rld_S4v3_K3y_2025"
    }

    private val filesDir: File = context.filesDir

    fun load(): DigiviceV1State? {
        loadFile(File(filesDir, COMPAT_SAVE_FILE))?.let { return it }
        loadFile(File(filesDir, LEGACY_SAVE_FILE))?.let { return it }
        loadBackup(File(filesDir, COMPAT_BACKUP_FILE), File(filesDir, COMPAT_SAVE_FILE))?.let { return it }
        return loadBackup(File(filesDir, LEGACY_BACKUP_FILE), File(filesDir, LEGACY_SAVE_FILE))
    }

    fun save(state: DigiviceV1State): Boolean {
        val payload = buildSaveJson(state).toString()
        val compatSaved = writeSaveFile(
            File(filesDir, COMPAT_SAVE_FILE),
            File(filesDir, COMPAT_BACKUP_FILE),
            payload
        )
        val legacySaved = writeSaveFile(
            File(filesDir, LEGACY_SAVE_FILE),
            File(filesDir, LEGACY_BACKUP_FILE),
            payload
        )
        return compatSaved && legacySaved
    }

    private fun writeSaveFile(saveFile: File, backupFile: File, payload: String): Boolean {
        return runCatching {
            if (saveFile.exists()) {
                saveFile.copyTo(backupFile, overwrite = true)
            }
            saveFile.writeText(payload)
            true
        }.getOrDefault(false)
    }

    private fun loadBackup(backupFile: File, saveFile: File): DigiviceV1State? {
        if (!backupFile.exists()) return null
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
                startSequencePending = loadBoolean(true, root, "start_digivice_v1", "start_dpower_v1"),
                soundEnabled = loadBoolean(true, root, "sound_digivice_v1", "sound_dpower_v1"),
                gridEnabled = loadBoolean(false, root, "grid_digivice_v1", "grid_dpower_v1"),
                scale = loadInt(0, root, "scale_digivice_v1", "scale_dpower_v1"),
                vista = loadInt(0, root, "vista_digivice_v1", "vista_dpower_v1"),
                distance = loadInt(10000, root, "distance_digivice_v1", "distance_dpower_v1"),
                steps = loadInt(0, root, "steps_digivice_v1", "steps_dpower_v1"),
                dpower = loadInt(0, root, "dpower_digivice_v1", "dpower_dpower_v1").coerceIn(0, 99),
                battles = loadInt(0, root, "battle_digivice_v1", "battle_dpower_v1"),
                wins = loadInt(0, root, "wins_digivice_v1", "wins_dpower_v1"),
                currentChar = loadInt(0, root, "char_digivice_v1", "char_dpower_v1").coerceIn(0, 7),
                evoLevel = 0,
                defeat = loadBoolean(false, root, "defeat_digivice_v1", "defeat_dpower_v1"),
                battlePending = loadBoolean(false, root, "battle_start_digivice_v1", "battle_start_dpower_v1"),
                eventPending = false,
                connectMode = false,
                autorun = false,
                area = loadInt(0, root, "area_digivice_v1", "area_dpower_v1").coerceIn(0, 6),
                areas = loadIntArray(intArrayOf(1, 1, 1, 1, 1, 1, 1), 7, root, "areas_digivice_v1", "areas_dpower_v1"),
                perAreaDistances = loadIntArray(intArrayOf(10000, 12000, 14000, 16000, 18000, 20000, 22000), 7, root, "map_distance_digivice_v1", "map_distance_dpower_v1"),
                unlockedChars = loadBooleanArray(BooleanArray(8), 8, root, "chars_digivice_v1", "chars_dpower_v1"),
                notificationsEnabled = loadBoolean(true, root, "notifications_digivice_v1", "notifications_dpower_v1"),
                soundStyle = loadString("original", root, "sound_style_digivice_v1", "sound_style_dpower_v1")
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
            putEncrypted("map_distance_digivice_v1", JSONArray(state.perAreaDistances.map { it }.toList()))
            putEncrypted("start_digivice_v1", state.startSequencePending)
            putEncrypted("sound_digivice_v1", state.soundEnabled)
            putEncrypted("scale_digivice_v1", state.scale)
            putEncrypted("vista_digivice_v1", state.vista)
            putEncrypted("grid_digivice_v1", state.gridEnabled)
            putEncrypted("notifications_digivice_v1", state.notificationsEnabled)
            putEncrypted("sound_style_digivice_v1", state.soundStyle)
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

    private fun loadInt(fallback: Int, root: JSONObject, vararg names: String): Int {
        for (name in names) {
            val raw = decryptOrNull(root, name)?.toIntOrNull()
            if (raw != null) return raw
        }
        return fallback
    }

    private fun loadBoolean(fallback: Boolean, root: JSONObject, vararg names: String): Boolean {
        for (name in names) {
            val raw = decryptOrNull(root, name)?.lowercase()
            when (raw) {
                "true" -> return true
                "false" -> return false
            }
        }
        return fallback
    }

    private fun loadString(fallback: String, root: JSONObject, vararg names: String): String {
        for (name in names) {
            val raw = decryptOrNull(root, name)
            if (!raw.isNullOrEmpty()) return raw
        }
        return fallback
    }

    private fun loadIntArray(fallback: IntArray, size: Int, root: JSONObject, vararg names: String): IntArray {
        for (name in names) {
            val decoded = decryptOrNull(root, name) ?: continue
            try {
                val array = JSONArray(decoded)
                return IntArray(size) { index -> array.optInt(index, fallback.getOrElse(index) { 0 }) }
            } catch (e: Exception) {
                continue
            }
        }
        return fallback
    }

    private fun loadBooleanArray(fallback: BooleanArray, size: Int, root: JSONObject, vararg names: String): BooleanArray {
        for (name in names) {
            val decoded = decryptOrNull(root, name) ?: continue
            try {
                val array = JSONArray(decoded)
                return BooleanArray(size) { index -> array.optBoolean(index, fallback.getOrElse(index) { false }) }
            } catch (e: Exception) {
                continue
            }
        }
        return fallback
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
