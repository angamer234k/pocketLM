package com.angamer.pocketlm.update

enum class UpdateMode {
    OFF,
    CHECK_ON_START,
    AUTO;

    companion object {
        fun from(raw: String?): UpdateMode = entries.firstOrNull { it.name == raw } ?: OFF
    }
}
