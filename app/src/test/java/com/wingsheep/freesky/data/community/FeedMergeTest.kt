package com.wingsheep.freesky.data.community

import com.wingsheep.encrypt.identity.Identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeedMergeTest {

    private fun post(id: Long, parentId: Long? = null) = DecryptedPost(
        id = id,
        content = "post $id",
        authorIdentity = Identity(name = "tester", color = 0),
        timestamp = id,
        mlsEpoch = 1,
        parentId = parentId
    )

    private fun ids(posts: List<DecryptedPost>) = posts.map { it.id }

    @Test
    fun appendDropsPostsAlreadyInTheList() {
        // Page 1 = 1..3, page 2 overlaps by re-returning 3.
        val existing = listOf(post(1), post(2), post(3))
        val nextPage = listOf(post(3), post(4), post(5))

        val merged = FeedMerge.append(existing, nextPage)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(merged))
    }

    @Test
    fun appendKeepsExistingWhenPageIsEmpty() {
        val existing = listOf(post(1), post(2))

        assertSame(existing, FeedMerge.append(existing, emptyList()))
    }

    @Test
    fun prependPutsNewPostsFirstAndDropsOverlaps() {
        val existing = listOf(post(2), post(3))
        val refreshed = listOf(post(1), post(2))

        val merged = FeedMerge.prepend(existing, refreshed)

        assertEquals(listOf(1L, 2L, 3L), ids(merged))
    }

    @Test
    fun duplicateRootIdsCollapseSoTheListStaysKeyable() {
        // The regression: a refresh rewound the cursor, the next page re-returned
        // posts already loaded, and the duplicated key crashed LazyColumn.
        var posts = listOf(post(10, parentId = null), post(9, parentId = null))
        posts = FeedMerge.prepend(posts, listOf(post(11, parentId = null)))
        posts = FeedMerge.append(posts, listOf(post(10, parentId = null), post(8, parentId = null)))

        val rootIds = posts.filter { it.parentId == null }.map { it.id }
        assertEquals(rootIds.distinct(), rootIds)
        assertEquals(listOf(11L, 10L, 9L, 8L), rootIds)
    }

    @Test
    fun distinctKeepsFirstOccurrenceAndOrder() {
        val merged = FeedMerge.distinct(listOf(post(3), post(1), post(3), post(2)))

        assertEquals(listOf(3L, 1L, 2L), ids(merged))
    }
}
