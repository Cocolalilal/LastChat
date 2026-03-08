package me.rerere.rikkahub.service

enum class ChatPersistenceMode(
    val routeValue: String,
) {
    NORMAL("normal"),
    TEMPORARY("temporary"),
    PERSIST_ON_REPLY("persist_on_reply");

    companion object {
        fun fromRouteValue(value: String?): ChatPersistenceMode? {
            return entries.firstOrNull { it.routeValue == value }
        }
    }
}
