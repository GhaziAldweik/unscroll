package dev.ghazi.igfocus.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureCollectorTest {

    private class FakeNode(
        override val viewId: String? = null,
        override val isVisible: Boolean = true,
        private val children: List<FakeNode?> = emptyList(),
    ) : NodeView {
        override val childCount: Int get() = children.size
        override fun child(index: Int): NodeView? = children[index]
    }

    private fun ig(name: String) = "com.instagram.android:id/$name"

    @Test
    fun `ids are shortened, distinct and sorted`() {
        val root = FakeNode(
            viewId = ig("tab_bar"),
            children = listOf(
                FakeNode(ig("feed_tab")),
                FakeNode(ig("clips_tab")),
                FakeNode(ig("feed_tab")),
            ),
        )
        assertEquals(
            listOf("clips_tab", "feed_tab", "tab_bar"),
            SignatureCollector.collect(listOf(root)).ids,
        )
    }

    @Test
    fun `invisible nodes are left out but their visible children are kept`() {
        val root = FakeNode(
            viewId = ig("hidden_parent"),
            isVisible = false,
            children = listOf(
                FakeNode(ig("visible_child")),
                FakeNode(ig("hidden_child"), isVisible = false),
            ),
        )
        assertEquals(listOf("visible_child"), SignatureCollector.collect(listOf(root)).ids)
    }

    @Test
    fun `nodes without an id and missing children are skipped`() {
        val root = FakeNode(
            children = listOf(null, FakeNode(viewId = ""), FakeNode(ig("action_bar_title"))),
        )
        assertEquals(listOf("action_bar_title"), SignatureCollector.collect(listOf(root)).ids)
    }

    @Test
    fun `ids from other packages keep their full name`() {
        val root = FakeNode(viewId = "android:id/content", children = listOf(FakeNode(ig("tab_bar"))))
        assertEquals(
            listOf("android:id/content", "tab_bar"),
            SignatureCollector.collect(listOf(root)).ids,
        )
    }

    @Test
    fun `several windows are merged into one signature`() {
        val screen = FakeNode(ig("tab_bar"))
        val dialog = FakeNode(ig("dialog_container"))
        assertEquals(
            listOf("dialog_container", "tab_bar"),
            SignatureCollector.collect(listOf(screen, dialog)).ids,
        )
    }

    @Test
    fun `the node limit stops a runaway tree`() {
        val wide = FakeNode(children = List(100) { FakeNode(ig("row_$it")) })
        val signature = SignatureCollector.collect(listOf(wide), maxNodes = 10)
        assertTrue(signature.ids.size < 10)
    }

    @Test
    fun `no windows gives an empty signature`() {
        assertEquals(emptyList<String>(), SignatureCollector.collect(emptyList()).ids)
    }
}
