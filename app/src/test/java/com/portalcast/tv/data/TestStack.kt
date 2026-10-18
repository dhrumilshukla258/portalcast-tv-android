package com.portalcast.tv.data

import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.AppJson
import com.portalcast.tv.data.net.AuthInterceptor
import com.portalcast.tv.data.net.DynamicBaseUrlInterceptor
import com.portalcast.tv.data.net.TokenAuthenticator
import com.portalcast.tv.data.net.TokenRefresher
import com.portalcast.tv.data.repo.DefaultAuthRepository
import com.portalcast.tv.data.repo.DefaultDiscoverRepository
import com.portalcast.tv.data.repo.DefaultLiveRepository
import com.portalcast.tv.data.repo.DefaultSeriesRepository
import com.portalcast.tv.data.repo.DefaultUserRepository
import com.portalcast.tv.data.repo.DefaultVodRepository
import com.portalcast.tv.data.store.Session
import com.portalcast.tv.data.store.SessionSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** In-memory [SessionSource]; the real one needs an Android Context for DataStore. */
class FakeSessionSource(serverUrl: String?, initial: Session?) : SessionSource {
    override val isLoaded = MutableStateFlow(true)
    override val serverUrl = MutableStateFlow(serverUrl)
    override val session = MutableStateFlow(initial)
    val clearCount = AtomicInteger()
    private val issued = AtomicLong(1L)

    override suspend fun setServerUrl(url: String) { serverUrl.value = url }
    override suspend fun saveSession(accessToken: String, refreshToken: String, user: UserDto) {
        session.value = Session(accessToken, refreshToken, issued.incrementAndGet(), user)
    }
    override suspend fun updateTokens(accessToken: String, refreshToken: String) {
        session.value = session.value?.copy(accessToken = accessToken, refreshToken = refreshToken)
    }
    override suspend fun updateUser(user: UserDto) { session.value = session.value?.copy(user = user) }
    override suspend fun clearSession() { session.value = null; clearCount.incrementAndGet() }
}

/** Path-keyed MockWebServer dispatcher. Later registrations win, so tests can override defaults. */
class Routes : Dispatcher() {
    private class Route(val method: String, val path: String, val handler: (RecordedRequest) -> MockResponse)
    private val routes = CopyOnWriteArrayList<Route>()
    val received = CopyOnWriteArrayList<RecordedRequest>()

    fun on(method: String, path: String, handler: (RecordedRequest) -> MockResponse) = apply { routes += Route(method, path, handler) }
    fun get(path: String, handler: (RecordedRequest) -> MockResponse) = on("GET", path, handler)
    fun get(path: String, fixtureName: String) = get(path) { fixtureResponse(fixtureName) }

    override fun dispatch(request: RecordedRequest): MockResponse {
        received += request
        val path = request.requestUrl?.encodedPath ?: request.path.orEmpty().substringBefore('?')
        val route = routes.lastOrNull { it.method == request.method && it.path == path }
        return route?.handler?.invoke(request) ?: json("""{"error":"no route for ${request.method} $path"}""", 404)
    }

    fun requests(method: String, path: String): List<RecordedRequest> =
        received.filter { it.method == method && it.requestUrl?.encodedPath == path }

    fun count(method: String, path: String): Int = requests(method, path).size
}

fun fixture(name: String): String =
    Routes::class.java.classLoader!!.getResource("fixtures/$name.json")!!.readText()

fun json(body: String, code: Int = 200): MockResponse =
    MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body)

fun fixtureResponse(name: String, code: Int = 200): MockResponse = json(fixture(name), code)

fun unauthorized(): MockResponse = json("""{"error":"Unauthorized"}""", 401)

fun RecordedRequest.bodyJson(): JsonObject = AppJson.parseToJsonElement(body.clone().readUtf8()).jsonObject

fun RecordedRequest.query(name: String): String? = requestUrl?.queryParameter(name)

/** An unsigned HS256-shaped JWT whose payload carries [expSeconds]; only `exp` is read client side. */
fun fakeJwt(expSeconds: Long): String {
    val enc = java.util.Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val payload = enc.encodeToString("""{"userId":1,"email":"tv@example.com","role":"user","exp":$expSeconds}""".toByteArray())
    return "$header.$payload.c2lnbmF0dXJl"
}

/**
 * The AppContainer network stack (bare client, refresher, interceptors, authenticator,
 * Retrofit + kotlinx) pointed at a [MockWebServer], plus the real repositories on top.
 */
class TestStack(
    val server: MockWebServer,
    signedIn: Boolean = true,
    accessToken: String = "access-old",
    refreshToken: String = "refresh-1",
) {
    val routes = Routes().also { server.dispatcher = it }
    val origin: String = server.url("/").toString().trimEnd('/')
    val user = UserDto(id = 1, email = "tv@example.com", name = "TV", role = "user")
    val session = FakeSessionSource(origin, if (signedIn) Session(accessToken, refreshToken, 1L, user) else null)

    val bareClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    val refresher = TokenRefresher(session, bareClient)
    val apiClient: OkHttpClient = bareClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(DynamicBaseUrlInterceptor(session))
        .addInterceptor(AuthInterceptor(session, refresher))
        .authenticator(TokenAuthenticator(refresher))
        .build()
    val api: ApiService = Retrofit.Builder()
        .baseUrl("http://placeholder.invalid/")
        .client(apiClient)
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

    /** Fake wall clock for TTL caches, in ms. */
    val clock = AtomicLong(1_000_000L)

    val users = DefaultUserRepository(api, session) { clock.get() }
    val live = DefaultLiveRepository(api, users, session) { clock.get() }
    val vod = DefaultVodRepository(api)
    val series = DefaultSeriesRepository(api)
    val discover = DefaultDiscoverRepository(api, vod, series)
    val auth = DefaultAuthRepository(api, session, bareClient)
}
