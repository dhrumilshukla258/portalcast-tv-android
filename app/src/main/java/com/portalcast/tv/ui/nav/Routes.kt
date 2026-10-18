package com.portalcast.tv.ui.nav

/** String routes for navigation-compose. Keep args small: ids and category only. */
object Routes {
    const val SPLASH = "splash"
    const val SERVER = "server"
    const val PAIR = "pair"
    const val LOGIN = "login"

    const val HOME = "home/{tab}"
    fun home(tab: HomeTab = HomeTab.DISCOVER) = "home/${tab.route}"

    const val MOVIE_DETAIL = "movie/{id}?category={category}"
    fun movieDetail(id: String, category: String?) = "movie/${enc(id)}?category=${enc(category ?: "*")}"

    const val SERIES_DETAIL = "series/{id}?category={category}"
    fun seriesDetail(id: String, category: String?) = "series/${enc(id)}?category=${enc(category ?: "*")}"

    /** The session comes from PlaybackHolder, not from the route. */
    const val PLAYER = "player"
    const val SETTINGS = "settings"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Routes that only make sense while signed out. */
    val authRoutes = setOf(SERVER, PAIR, LOGIN)
}

enum class HomeTab(val route: String, val title: String) {
    DISCOVER("discover", "Discover"),
    LIVE("live", "Live TV"),
    MOVIES("movies", "Movies"),
    SERIES("series", "Series");

    companion object {
        fun fromRoute(route: String?): HomeTab = entries.firstOrNull { it.route == route } ?: DISCOVER
    }
}
