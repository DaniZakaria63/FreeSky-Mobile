package com.wingsheep.network.model

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
