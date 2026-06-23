package com.digimon.digiviceglyph.runtime

data class DigiviceEncounter(
    val type: String = "none",
    val distance: Int,
    val steps: Int,
    val energy: Int
)

data class DigimonProfile(
    val name: String,
    val shortCode: String,
    val attackSprites: Int = 0,
    val evolutions: List<EvolutionProfile>
)

data class EvolutionProfile(
    val level: Int,
    val name: String,
    val hp: Int,
    val attack: Int
)

data class DigiviceV1State(
    var startSequencePending: Boolean = true,
    var soundEnabled: Boolean = true,
    var gridEnabled: Boolean = false,
    var scale: Int = 0,
    var vista: Int = 0,
    var distance: Int = 10000,
    var steps: Int = 0,
    var dpower: Int = 0,
    var battles: Int = 0,
    var wins: Int = 0,
    var currentChar: Int = 0,
    var evoLevel: Int = 0,
    var defeat: Boolean = false,
    var battlePending: Boolean = false,
    var eventPending: Boolean = false,
    var connectMode: Boolean = false,
    var autorun: Boolean = false,
    var notificationsEnabled: Boolean = true,
    var area: Int = 0,
    var areas: IntArray = intArrayOf(1, 1, 1, 1, 1, 1, 1),
    var perAreaDistances: IntArray = intArrayOf(10000, 12000, 14000, 16000, 18000, 20000, 22000),
    var unlockedChars: BooleanArray = BooleanArray(8),
    var lastEncounter: DigiviceEncounter? = null
) {
    fun currentProfile(): DigimonProfile = DIGIMON_PROFILES[currentChar.coerceIn(DIGIMON_PROFILES.indices)]

    companion object {
        val DIGIMON_PROFILES: List<DigimonProfile> = listOf(
            DigimonProfile(
                name = "Agumon",
                shortCode = "AGU",
                evolutions = listOf(
                    EvolutionProfile(0, "Agumon", 3, 1),
                    EvolutionProfile(1, "Greymon", 5, 2),
                    EvolutionProfile(2, "MetalGreymon", 7, 4)
                )
            ),
            DigimonProfile(
                name = "Gabumon",
                shortCode = "GAB",
                evolutions = listOf(
                    EvolutionProfile(0, "Gabumon", 3, 1),
                    EvolutionProfile(1, "Garurumon", 5, 2),
                    EvolutionProfile(2, "WereGarurumon", 7, 4)
                )
            ),
            DigimonProfile(
                name = "Biyomon",
                shortCode = "BIY",
                evolutions = listOf(
                    EvolutionProfile(0, "Biyomon", 3, 1),
                    EvolutionProfile(1, "Birdramon", 5, 2),
                    EvolutionProfile(2, "Garudamon", 7, 4)
                )
            ),
            DigimonProfile(
                name = "Palmon",
                shortCode = "PAL",
                evolutions = listOf(
                    EvolutionProfile(0, "Palmon", 3, 1),
                    EvolutionProfile(1, "Togemon", 5, 2),
                    EvolutionProfile(2, "Lilimon", 7, 4)
                )
            ),
            DigimonProfile(
                name = "Tentomon",
                shortCode = "TEN",
                evolutions = listOf(
                    EvolutionProfile(0, "Tentomon", 3, 1),
                    EvolutionProfile(1, "Kabuterimon", 5, 2),
                    EvolutionProfile(2, "MegaKabuterimon", 7, 4)
                )
            ),
            DigimonProfile(
                name = "Gomamon",
                shortCode = "GOM",
                evolutions = listOf(
                    EvolutionProfile(0, "Gomamon", 3, 1),
                    EvolutionProfile(1, "Ikkakumon", 5, 2),
                    EvolutionProfile(2, "Zudomon", 7, 4)
                )
            ),
            DigimonProfile(
                name = "Patamon",
                shortCode = "PAT",
                evolutions = listOf(
                    EvolutionProfile(0, "Patamon", 3, 1),
                    EvolutionProfile(1, "Angemon", 5, 2),
                    EvolutionProfile(2, "MagnaAngemon", 7, 4)
                )
            ),
            DigimonProfile(
                name = "Gatomon",
                shortCode = "GAT",
                evolutions = listOf(
                    EvolutionProfile(0, "Gatomon", 3, 1),
                    EvolutionProfile(1, "Angewomon", 5, 2),
                    EvolutionProfile(2, "Magnadramon", 7, 4)
                )
            )
        )
    }
}
