package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.ui.PrivateDraftStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class PrivateDraftStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun bundleHandleIsOpaqueAndOnlyMatchingUnexpiredRevisionRestores() {
        var time = 0L
        val directory = temporary.newFolder()
        val store = PrivateDraftStore(directory) { time }
        val id = UUID.randomUUID().toString()
        val handle = store.save(id, "account:2", listOf("synthetic-private-draft", "1234.56"))
        assertEquals(id, handle)
        assertFalse(handle.contains("synthetic-private-draft"))
        assertEquals(listOf("synthetic-private-draft", "1234.56"), store.restore(handle, "account:2"))
        assertNull(store.restore(handle, "account:3"))
        store.save(id, "account:2", listOf("replacement"))
        time = 86_400_000L
        assertNull(store.restore(id, "account:2"))
        assertNull(store.restore("../escape", "account:2"))
    }
}
