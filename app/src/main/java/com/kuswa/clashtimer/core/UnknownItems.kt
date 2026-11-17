package com.kuswa.clashtimer.core

import kotlinx.serialization.Serializable

/** Generic icon drawn when an item has no icon file (plan 1.1 §4), instead of initials. */
enum class FallbackIcon { BUILDING, TROOP, SQUAD, SPELL, PET }

object Fallbacks {
    /**
     * Icon category for an item: unknown troops are TH19 squads; heroes, guardians and siege machines use
     * the troop icon; buildings, crafted-defense modules and traps the building icon.
     */
    fun icon(kind: ItemKind, named: Boolean): FallbackIcon = when (kind) {
        ItemKind.BUILDING, ItemKind.MODULE, ItemKind.TRAP -> FallbackIcon.BUILDING
        ItemKind.TROOP -> if (named) FallbackIcon.TROOP else FallbackIcon.SQUAD
        ItemKind.HERO, ItemKind.GUARDIAN, ItemKind.SIEGE_MACHINE -> FallbackIcon.TROOP
        ItemKind.SPELL -> FallbackIcon.SPELL
        ItemKind.PET -> FallbackIcon.PET
    }

    fun kindLabel(kind: ItemKind): String = when (kind) {
        ItemKind.BUILDING -> "Building"
        ItemKind.MODULE -> "Crafted defense module"
        ItemKind.TRAP -> "Trap"
        ItemKind.HERO -> "Hero"
        ItemKind.GUARDIAN -> "Guardian"
        ItemKind.TROOP -> "Troop / squad"
        ItemKind.SPELL -> "Spell"
        ItemKind.SIEGE_MACHINE -> "Siege machine"
        ItemKind.PET -> "Pet"
    }
}

/**
 * What the player added on the "Unknown items" screen, so a game update doesn't need a new app
 * release. Stored on the phone only. Icons are files (`files/user_icons/<id>.webp`), listed in [icons].
 */
@Serializable
data class UserItems(
    val names: Map<Int, String> = emptyMap(),
    val icons: Set<Int> = emptySet(),
) {
    fun withName(id: Int, name: String): UserItems {
        val clean = name.trim().take(MAX_NAME)
        return copy(names = if (clean.isEmpty()) names - id else names + (id to clean))
    }

    fun encode(): String = AppState.JSON.encodeToString(serializer(), this)

    companion object {
        const val MAX_NAME = 32

        fun decode(text: String?): UserItems =
            text?.let { runCatching { AppState.JSON.decodeFromString(serializer(), it) }.getOrNull() } ?: UserItems()
    }
}

/** One line on the "Unknown items" screen. */
data class UnknownItem(
    val item: ExportItem,
    /** The app has no name for it (the widget shows `New building`, `New squad`, …). */
    val missingName: Boolean,
    /** No bundled icon for it (or, for a module, its defense): the widget shows a category icon. */
    val missingIcon: Boolean,
    val userName: String?,
    val hasUserIcon: Boolean,
)

object UnknownItems {
    /**
     * Every item in [exports] (the latest export of each account) that the app has no name or no icon
     * for, each ID once. Player entries don't remove an item from the list, so they can be edited.
     *
     * @param bundled names without the player's own ([NameMap.hasBundled] is used)
     * @param hasBundledIcon whether `assets/icons/<id>.webp` (or `.png`) exists
     */
    fun find(
        exports: List<VillageExport>,
        bundled: NameMap,
        hasBundledIcon: (Int) -> Boolean,
        user: UserItems,
    ): List<UnknownItem> =
        exports.flatMap { it.items }
            .distinctBy { it.dataId }
            .mapNotNull { item ->
                val missingName = !bundled.hasBundled(item.dataId)
                val missingIcon = listOfNotNull(item.dataId, item.parentDataId).none(hasBundledIcon)
                if (!missingName && !missingIcon) return@mapNotNull null
                UnknownItem(item, missingName, missingIcon, user.names[item.dataId], item.dataId in user.icons)
            }
            .sortedWith(compareBy({ it.item.kind }, { it.item.dataId }))
}
