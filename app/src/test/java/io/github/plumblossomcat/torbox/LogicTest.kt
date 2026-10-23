package io.github.plumblossomcat.torbox

import com.google.common.truth.Truth.assertThat
import io.github.plumblossomcat.torbox.core.model.TransferFile
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.data.IncomingIntent
import io.github.plumblossomcat.torbox.ui.screens.changelog.renderMarkdown
import io.github.plumblossomcat.torbox.ui.screens.detail.FileTree
import io.github.plumblossomcat.torbox.ui.screens.detail.TreeRow
import org.junit.Test

class LogicTest {
    private fun f(id: Long, path: String) = TransferFile(id, path, path.substringAfterLast('/'), 100, null, false)

    @Test
    fun sharedTextIsClassified() {
        val magnet = IncomingIntent.fromText("Check this out magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=x thanks")!!
        assertThat(magnet.kind).isEqualTo(TransferKind.TORRENT)
        assertThat(magnet.text).startsWith("magnet:?xt=urn:btih:dd82")

        val web = IncomingIntent.fromText("https://example.com/a.zip\nhttps://example.com/b.zip")!!
        assertThat(web.kind).isEqualTo(TransferKind.WEB)
        assertThat(web.text!!.lines()).hasSize(2)

        assertThat(IncomingIntent.fromText("https://indexer.example/get/123.nzb?apikey=x")!!.kind).isEqualTo(TransferKind.USENET)
        assertThat(IncomingIntent.fromText("DD8255ECDC7CA55FB0BBF81323D87062DB1F6D1C")!!.kind).isEqualTo(TransferKind.TORRENT)
        assertThat(IncomingIntent.fromText("just some words")).isNull()
    }

    @Test
    fun fileTreeDropsSharedRootAndNestsFolders() {
        val rows = FileTree.rows(
            listOf(f(1, "Show/S01/E01.mkv"), f(2, "Show/S01/E02.mkv"), f(3, "Show/poster.jpg")),
            collapsed = emptySet(),
        )
        assertThat(rows.map { it.depth to (it as? TreeRow.Folder)?.name ?: (it as TreeRow.Leaf).file.name })
            .containsExactly(0 to "S01", 1 to "E01.mkv", 1 to "E02.mkv", 0 to "poster.jpg").inOrder()
        val folder = rows.first() as TreeRow.Folder
        assertThat(folder.fileCount).isEqualTo(2)
        assertThat(folder.size).isEqualTo(200)

        val collapsed = FileTree.rows(listOf(f(1, "Show/S01/E01.mkv"), f(3, "Show/poster.jpg")), collapsed = setOf(folder.key))
        assertThat(collapsed).hasSize(2)
    }

    @Test
    fun singleFileHasNoFolders() {
        val rows = FileTree.rows(listOf(f(1, "ubuntu.iso")), emptySet())
        assertThat(rows).hasSize(1)
        assertThat(rows[0]).isInstanceOf(TreeRow.Leaf::class.java)
    }

    @Test
    fun markdownRendersReadably() {
        val text = renderMarkdown("## New\n- **Faster** checks\n- See [docs](https://x)\n\nThanks `all`").text
        assertThat(text).isEqualTo("New\n•  Faster checks\n•  See docs\n\nThanks all")
    }
}
