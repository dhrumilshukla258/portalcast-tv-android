package com.portalcast.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.PersonCredit
import com.portalcast.tv.data.model.TitleDetails
import com.portalcast.tv.appContainer
import com.portalcast.tv.ui.theme.AppColors

/**
 * The title's detail-page data (`/api/v2/discover/details`): tagline, age rating, studios,
 * collection, writers, logo, cast with photos. Null while loading, and on servers that do not
 * have the endpoint: the page then simply shows what the card carried.
 */
@Composable
fun rememberTitleDetails(item: MediaItem, kind: String): TitleDetails? {
    val api = LocalContext.current.appContainer.api
    val key = item.catalogId ?: "${kind}_${item.rawId}"
    val details by produceState<TitleDetails?>(null, key) {
        value = runCatching {
            if (item.catalogId != null) api.titleDetails(id = item.catalogId, type = null, raw = null)
            else api.titleDetails(id = null, type = kind, raw = item.rawId)
        }.getOrNull()
    }
    return details
}

/** The title's logo when it has one (transparent art), else its name as text. */
@Composable
fun TitleHeading(title: String, logoPath: String?, maxLines: Int = 2, large: Boolean = true) {
    val logo = rememberImageUrl(logoPath)
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.heightIn(max = if (large) 120.dp else 96.dp).widthIn(max = 520.dp),
        )
    } else {
        Text(
            title.ifBlank { "Untitled" },
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
            color = AppColors.Text,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tagline, then "PG-13 · Columbia Pictures · The Dark Knight Collection", then writers. */
@Composable
fun DetailFacts(item: MediaItem, details: TitleDetails?, series: Boolean) {
    val tagline = details?.tagline ?: item.tagline
    if (!tagline.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(tagline, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val facts = listOfNotNull(
        (details?.ageRating ?: item.ratingMpaa)?.takeIf { it.isNotBlank() },
        (details?.studios ?: item.studios)?.split(",")?.take(2)?.joinToString(", ") { it.trim() }?.takeIf { it.isNotBlank() },
        (details?.collection ?: item.collection)?.takeIf { it.isNotBlank() },
    )
    if (facts.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val writers = details?.writers ?: item.writers
    if (!series && !writers.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text("Written by: $writers", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

