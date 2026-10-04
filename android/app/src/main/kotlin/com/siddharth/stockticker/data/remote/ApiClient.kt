package com.siddharth.stockticker.data.remote

import com.siddharth.stockticker.data.remote.dto.ChartResponse
import com.siddharth.stockticker.data.remote.dto.QuoteResponse
import com.siddharth.stockticker.data.remote.dto.SearchResponse
import com.squareup.moshi.Moshi
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Networking singleton.
 *
 * Two deliberate choices here:
 *  - **No backend required.** The watch hits Yahoo's web endpoints itself, so the
 *    APK is installable and useful with nothing else running. Set [BASE_URL] to a
 *    self-hosted Flask proxy if you'd rather centralise caching (see backend/).
 *  - **One shared OkHttp client** with an 8s timeout, a single retry, and an
 *    in-memory cookie jar, so a hung request can never pin a loading spinner.
 */
object ApiClient {

    /** Yahoo's query host. Replace with `https://your-proxy/` to use backend/. */
    const val BASE_URL = "https://query1.finance.yahoo.com/"

    /** Host that hands out the session cookie the crumb endpoint requires. */
    const val COOKIE_URL = "https://fc.yahoo.com/"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    private val cookieJar = object : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(store) {
                // Yahoo scopes its session cookie to `.yahoo.com`, so key on the
                // registrable domain rather than the exact host.
                val bucket = store.getOrPut(ROOT_DOMAIN) { mutableListOf() }
                cookies.forEach { fresh ->
                    bucket.removeAll { it.name == fresh.name }
                    bucket.add(fresh)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) {
            val now = System.currentTimeMillis()
            store[ROOT_DOMAIN]?.filter { it.expiresAt > now }.orEmpty()
        }

        private val ROOT_DOMAIN = "yahoo.com"
    }

    /** Browser-ish headers: the endpoints reject the default OkHttp UA. */
    private val headerInterceptor = Interceptor { chain ->
        val request: Request = chain.request().newBuilder()
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        chain.proceed(request)
    }

    /** One retry with a short backoff; anything more wastes radio on a watch. */
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var lastError: java.io.IOException? = null
        repeat(2) { attempt ->
            if (attempt > 0) {
                try {
                    Thread.sleep(400L)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            try {
                val response: Response = chain.proceed(request)
                if (response.code < 500 || attempt == 1) return@Interceptor response
                response.close()
            } catch (io: java.io.IOException) {
                lastError = io
            }
        }
        throw lastError ?: java.io.IOException("Request failed")
    }

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(headerInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val moshi: Moshi by lazy { Moshi.Builder().build() }

    val service: StockApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(StockApiService::class.java)
    }

    // Kept so R8 keeps the generated adapters reachable in release builds.
    @Suppress("unused")
    private val adapterAnchors = listOf(
        ChartResponse::class.java,
        QuoteResponse::class.java,
        SearchResponse::class.java
    )
}
