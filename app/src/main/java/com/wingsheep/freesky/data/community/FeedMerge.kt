package com.wingsheep.freesky.data.community

/**
 * Merges feed pages without ever producing a duplicate post id.
 *
 * A duplicate id is not cosmetic. The feed list is keyed by post id, and Compose
 * throws "Key ... was already used" when the same key appears twice, which crashed
 * paging as soon as a notification-driven refresh and a page load overlapped.
 */
internal object FeedMerge {

    /** [incoming] are newer; they go in front of [existing]. */
    fun prepend(existing: List<DecryptedPost>, incoming: List<DecryptedPost>): List<DecryptedPost> =
        if (incoming.isEmpty()) existing else distinct(incoming + existing)

    /** [incoming] are older; they go after [existing]. */
    fun append(existing: List<DecryptedPost>, incoming: List<DecryptedPost>): List<DecryptedPost> =
        if (incoming.isEmpty()) existing else distinct(existing + incoming)

    /** Keeps the first occurrence of each id, preserving order. */
    fun distinct(posts: List<DecryptedPost>): List<DecryptedPost> {
        if (posts.size < 2) return posts
        val seen = HashSet<Long>(posts.size)
        return posts.filter { seen.add(it.id) }
    }
}
