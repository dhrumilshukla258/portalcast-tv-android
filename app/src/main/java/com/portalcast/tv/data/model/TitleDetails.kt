package com.portalcast.tv.data.model

import kotlinx.serialization.Serializable

/** One credit on a detail page: a person, their character or job, and a photo URL. */
@Serializable
data class PersonCredit(
    val name: String,
    val role: String? = null,
    val photo: String? = null,
    /** Opens the person's page (`GET /api/v2/people/{id}`, servers from 2026-10-21). */
    val id: Int? = null,
)

/** `GET /api/v2/people/{id}`: who the person is and every title of theirs in the catalog. */
@Serializable
data class PersonInfo(
    val id: Int,
    val name: String,
    val photo: String? = null,
    val biography: String? = null,
    val knownFor: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val placeOfBirth: String? = null,
)

/** How a person worked on a title: kind (cast, director, writer, creator) and character or job. */
@Serializable
data class CreditRole(val kind: String, val role: String? = null)

@Serializable
data class PersonPage(val person: PersonInfo, val credits: List<MediaItem> = emptyList())

/**
 * `GET /api/v2/discover/details` (servers from 2026-10-21): what a detail page shows beyond
 * the card, as detailed as Jellyfin's metadata. Every field is optional.
 */
@Serializable
data class TitleDetails(
    val tagline: String? = null,
    val ageRating: String? = null,
    val runtime: Int? = null,
    val studios: String? = null,
    val collection: String? = null,
    val originalTitle: String? = null,
    val premiered: String? = null,
    val writers: String? = null,
    val logo: String? = null,
    val cast: List<PersonCredit> = emptyList(),
    val directors: List<PersonCredit> = emptyList(),
    val creators: List<PersonCredit> = emptyList(),
    val writersPeople: List<PersonCredit> = emptyList(),
)
