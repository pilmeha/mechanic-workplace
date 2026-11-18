package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.auth.QueueGateState
import com.example.mechanicworkplace.data.local.StatusCount
import com.example.mechanicworkplace.data.local.SyncOperationEntity

data class SyncBannerState(
    val needsAuth: Boolean,
    val pending: Int,
    val needsReconciliation: Int,
    val failed: Int
) {
    val visible: Boolean
        get() = needsAuth || pending > 0 || needsReconciliation > 0 || failed > 0
}

fun syncBannerState(counts: List<StatusCount>, gateState: QueueGateState): SyncBannerState {
    fun count(status: String): Int = counts.firstOrNull { it.status == status }?.count ?: 0
    return SyncBannerState(
        needsAuth = gateState is QueueGateState.Paused ||
            count(SyncOperationEntity.STATUS_NEEDS_AUTH) > 0,
        pending = count(SyncOperationEntity.STATUS_PENDING),
        needsReconciliation = count(SyncOperationEntity.STATUS_NEEDS_RECONCILIATION),
        failed = count(SyncOperationEntity.STATUS_FAILED)
    )
}
