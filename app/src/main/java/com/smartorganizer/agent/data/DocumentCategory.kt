package com.smartorganizer.agent.data

enum class DocumentCategory(val folderName: String, val label: String) {
    RESUME("Resume", "Resume"),
    TICKET("Ticket", "Ticket"),
    BILL("Bill", "Bill"),
    ID("ID", "ID"),
    NOTES("Notes", "Notes"),
    SCREENSHOT("Screenshot", "Screenshot"),
    PHOTO("Photo", "Photo"),
    OTHER("Other", "Other"),
    REVIEW("Review", "Needs review");

    companion object {
        val modelChoices = entries.filter { it != REVIEW }

        fun fromModelLabel(raw: String?): DocumentCategory {
            val normalized = raw.orEmpty().trim()
            return entries.firstOrNull { it.folderName.equals(normalized, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                ?: OTHER
        }
    }
}
