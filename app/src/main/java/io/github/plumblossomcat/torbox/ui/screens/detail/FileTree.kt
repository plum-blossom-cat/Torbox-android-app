package io.github.plumblossomcat.torbox.ui.screens.detail

import io.github.plumblossomcat.torbox.core.model.TransferFile

/** A flattened, render-ready row of the file tree. */
sealed interface TreeRow {
    val key: String
    val depth: Int

    data class Folder(
        override val key: String,
        override val depth: Int,
        val name: String,
        val size: Long,
        val fileCount: Int,
        val expanded: Boolean,
    ) : TreeRow

    data class Leaf(
        override val key: String,
        override val depth: Int,
        val file: TransferFile,
    ) : TreeRow
}

private class Node(val name: String, val path: String) {
    val folders = linkedMapOf<String, Node>()
    val files = mutableListOf<TransferFile>()
    val size: Long get() = files.sumOf { it.size } + folders.values.sumOf { it.size }
    val count: Int get() = files.size + folders.values.sumOf { it.count }
}

object FileTree {
    /**
     * Builds folder rows from file paths. A single top-level folder shared by every file (usually
     * the download's own name) is dropped, since the screen title already says it.
     */
    fun rows(files: List<TransferFile>, collapsed: Set<String>): List<TreeRow> {
        if (files.isEmpty()) return emptyList()
        val split = files.map { it to it.path.trim('/').split('/').filter(String::isNotEmpty) }
        val firstSegments = split.map { (_, parts) -> if (parts.size > 1) parts.first() else null }.distinct()
        val dropRoot = firstSegments.size == 1 && firstSegments.first() != null
        val root = Node("", "")
        split.forEach { (file, rawParts) ->
            val parts = if (dropRoot) rawParts.drop(1) else rawParts
            var node = root
            parts.dropLast(1).forEach { segment ->
                node = node.folders.getOrPut(segment) { Node(segment, node.path + "/" + segment) }
            }
            node.files += file
        }
        val out = mutableListOf<TreeRow>()
        fun walk(node: Node, depth: Int) {
            node.folders.values.sortedBy { it.name.lowercase() }.forEach { folder ->
                val expanded = folder.path !in collapsed
                out += TreeRow.Folder(folder.path, depth, folder.name, folder.size, folder.count, expanded)
                if (expanded) walk(folder, depth + 1)
            }
            node.files.sortedBy { it.name.lowercase() }.forEach { out += TreeRow.Leaf("f${it.id}", depth, it) }
        }
        walk(root, 0)
        return out
    }
}
