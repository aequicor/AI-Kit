package io.aeqicor.aikit.pdf

data class TocEntry(
    val title: String,
    val pageIndex: Int,
    val depth: Int
)

enum class SidebarTab { THUMBNAILS, BOOKMARKS }
