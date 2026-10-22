package com.portalcast.tv.data.repo

/**
 * Route paths handed to `apiCall(path)` so [com.portalcast.tv.data.net.ApiError.from]
 * can recognise the four cold-cache list routes. Leading slash matters: the
 * matcher uses `endsWith`.
 */
internal object ApiPaths {
    const val LOGIN = "/api/auth/login"
    const val DEVICE_CODE = "/api/auth/device/code"
    const val DEVICE_POLL = "/api/auth/device/poll"
    const val PROFILE = "/api/user/profile"
    const val PREFERENCES = "/api/user/preferences"
    const val PROGRESS = "/api/user/progress"
    const val PROVIDER_KEY = "/api/v2/provider-key"
    const val GROUPS = "/api/v2/groups"
    const val CHANNELS = "/api/v2/channels"
    const val EPG = "/api/v2/epg"
    const val EPG_WINDOW = "/api/v2/epg/window"
    const val MOVIE_GROUPS = "/api/v2/movie-groups"
    const val MOVIES = "/api/v2/movies"
    const val SERIES_GROUPS = "/api/v2/series-groups"
    const val SERIES = "/api/v2/series"
    const val DISCOVER_FACETS = "/api/v2/discover/facets"
    const val DISCOVER_BROWSE = "/api/v2/discover/browse"
    const val DISCOVER_RECOMMENDATIONS = "/api/v2/discover/recommendations"
    const val HOME = "/api/v2/home"
    const val SIMILAR = "/api/v2/discover/similar"
    const val MY_LIST = "/api/user/list"
    const val SPORTS = "/api/v2/sports"
    const val DISCOVER_VARIANTS = "/api/v2/discover/variants"
    const val PERSON = "/api/v2/people"
    const val PEOPLE_SEARCH = "/api/v2/search/people"
}
