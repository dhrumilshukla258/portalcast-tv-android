package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The shared envelope for `/api/v2/movies`, `/series`, `/movie-groups`, `/series-groups`.
 * Page size is fixed at 14 by the server; `totalLoaded` is the count in this page.
 * See docs/api-contract.md §3.
 */
@Serializable
data class Paged<T>(
    val success: Boolean? = null,
    val page: LenientInt? = null,
    @SerialName("total_items") val totalItems: LenientInt? = null,
    @SerialName("total_loaded") val totalLoaded: LenientInt? = null,
    val data: List<T> = emptyList(),
    val isPortal: Boolean? = null,
    val error: String? = null,
) {
    /** True when the server reports more items than `page * 14` has shown. */
    fun hasMore(loadedSoFar: Int): Boolean = (totalItems ?: 0) > loadedSoFar
}

@Serializable
data class SuccessResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
)

/** Error body shape: `{ "error": "..." }`, sometimes with `success:false`. */
@Serializable
data class ErrorBody(val error: String? = null, val success: Boolean? = null)

@Serializable
data class ProviderKeyResponse(val providerKey: String? = null)

@Serializable
data class ExpiryResponse(val success: Boolean? = null, val expiry: String? = null)
