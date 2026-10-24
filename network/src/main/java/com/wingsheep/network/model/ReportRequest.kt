package com.wingsheep.network.model

/**
 * Request body for the `report` Noise call.
 *
 * Sent as a map by [com.wingsheep.network.noise.NoiseApiClient], so nothing reflects
 * on this class today; it is kept unannotated on purpose and R8 may strip it.
 */
data class ReportRequest(
    val post_id: Long,
    val reporter_pk: List<Int>,
    val reason: String?
) {
    companion object {
        fun fromBytes(postId: Long, reporterPk: ByteArray, reason: String?): ReportRequest =
            ReportRequest(
                post_id = postId,
                reporter_pk = reporterPk.map { it.toInt() and 0xFF },
                reason = reason
            )
    }
}
