package org.familytools.educationtracker.services

import org.familytools.educationtracker.data.Child

/** Matches an OCR-extracted name against enrolled children so scanned
 * documents can auto-select the right child without the parent re-typing
 * a name that's already on screen in the photo. */
object NameMatcher {
    fun findBestMatch(children: List<Child>, ocrName: String): Child? {
        val target = normalize(ocrName)
        if (target.isBlank()) return null

        children.firstOrNull { normalize(it.fullName) == target }?.let { return it }

        val targetTokens = target.split(" ").filter { it.isNotBlank() }.toSet()
        if (targetTokens.isEmpty()) return null

        return children
            .map { child -> child to targetTokens.intersect(normalize(child.fullName).split(" ").toSet()).size }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun normalize(name: String): String =
        name.trim().uppercase().replace(Regex("[^A-Z ]"), "").replace(Regex("\\s+"), " ").trim()
}
