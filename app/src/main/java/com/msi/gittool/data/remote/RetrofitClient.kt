package com.msi.gittool.data.remote

import com.msi.gittool.data.local.TokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.github.com/"

    fun create(tokenManager: TokenManager): GitHubApiService {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val requestBuilder = request.newBuilder()
            
            val host = request.url.host.lowercase()
            val isGitHubHost = host == "api.github.com" || host == "github.com" || 
                    host.endsWith(".github.com") || host.endsWith(".githubusercontent.com")
            
            if (isGitHubHost) {
                val token = tokenManager.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    val authHeader = if (token.startsWith("ghp_") || token.startsWith("gho_")) {
                        "token $token"
                    } else {
                        "Bearer $token"
                    }
                    requestBuilder.header("Authorization", authHeader)
                }
                requestBuilder.header("Accept", "application/vnd.github.v3+json")
            }
            
            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val customDispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }

        val customConnectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(customDispatcher)
            .connectionPool(customConnectionPool)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(GitHubApiService::class.java)
    }
}
