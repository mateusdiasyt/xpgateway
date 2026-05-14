package com.xparcade.tvkiosk.data.repository

import com.xparcade.tvkiosk.data.api.BackendApiService
import com.xparcade.tvkiosk.BuildConfig
import com.xparcade.tvkiosk.data.local.AppConfig
import com.xparcade.tvkiosk.domain.model.CreatePaymentRequest
import com.xparcade.tvkiosk.domain.model.CreatePaymentResponse
import com.xparcade.tvkiosk.domain.model.ForceUnlockRequest
import com.xparcade.tvkiosk.domain.model.SessionStatusResponse
import com.xparcade.tvkiosk.domain.model.StationConfigResponse
import com.xparcade.tvkiosk.domain.model.TvStatusResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class BackendRepository {

    @Volatile
    private var currentBaseUrl: String? = null

    @Volatile
    private var api: BackendApiService? = null

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun resolveDeviceKey(config: AppConfig): String {
        val candidate = config.deviceKey.trim()
        return if (candidate.isNotBlank()) candidate else config.stationToken
    }

    private fun getApi(baseUrl: String): BackendApiService {
        val normalized = normalizeBaseUrl(baseUrl)
        if (api != null && currentBaseUrl == normalized) {
            return api as BackendApiService
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(BackendApiService::class.java)
        currentBaseUrl = normalized
        api = service
        return service
    }

    suspend fun healthCheck(config: AppConfig): Boolean {
        return runCatching {
            getApi(config.backendUrl).healthCheck()
            true
        }.getOrDefault(false)
    }

    suspend fun getStationConfig(config: AppConfig): StationConfigResponse {
        return getApi(config.backendUrl).getStationConfig(
            stationId = config.stationId,
            stationHeaderId = config.stationId,
            stationToken = config.stationToken
        )
    }

    suspend fun getLastPaymentSummary(config: AppConfig): String? {
        val wrapper = getApi(config.backendUrl).getLastPayment(
            stationId = config.stationId,
            stationHeaderId = config.stationId,
            stationToken = config.stationToken
        )
        val data = wrapper.data ?: return null
        return "Último pagamento: ${data.status} • R$ ${"%.2f".format(data.amount)}"
    }

    suspend fun createPayment(config: AppConfig, optionMinutes: Int, optionAmount: Double): CreatePaymentResponse {
        return getApi(config.backendUrl).createPayment(
            stationHeaderId = config.stationId,
            stationToken = config.stationToken,
            request = CreatePaymentRequest(
                stationId = config.stationId,
                durationMinutes = optionMinutes,
                amount = optionAmount
            )
        )
    }

    suspend fun getSessionStatus(config: AppConfig, sessionId: String): SessionStatusResponse {
        return getApi(config.backendUrl).getSessionStatus(
            sessionId = sessionId,
            stationHeaderId = config.stationId,
            stationToken = config.stationToken
        )
    }

    suspend fun getTvStatus(config: AppConfig): TvStatusResponse {
        val api = getApi(config.backendUrl)
        val configuredDeviceKey = resolveDeviceKey(config)

        return runCatching {
            api.getTvStatus(
                stationId = config.stationId,
                deviceKey = configuredDeviceKey
            )
        }.recoverCatching { error ->
            val defaultDeviceKey = BuildConfig.DEFAULT_DEVICE_KEY
            val shouldRetryWithBundledKey =
                configuredDeviceKey != defaultDeviceKey &&
                    (error !is HttpException || error.code() == 401 || error.code() == 403)

            if (!shouldRetryWithBundledKey) {
                throw error
            }

            api.getTvStatus(
                stationId = config.stationId,
                deviceKey = defaultDeviceKey
            )
        }.getOrThrow()
    }

    suspend fun forceUnlock(config: AppConfig, durationMinutes: Int): Map<String, Any> {
        return getApi(config.backendUrl).forceUnlock(
            stationId = config.stationId,
            adminKey = config.adminApiKey,
            request = ForceUnlockRequest(durationMinutes)
        )
    }

    suspend fun endSession(config: AppConfig, sessionId: String): Map<String, Any> {
        return getApi(config.backendUrl).endSession(
            sessionId = sessionId,
            adminKey = config.adminApiKey
        )
    }

    suspend fun confirmMockPayment(config: AppConfig, providerPaymentId: String): Map<String, Any> {
        return getApi(config.backendUrl).confirmMockPayment(
            providerPaymentId = providerPaymentId,
            adminKey = config.adminApiKey
        )
    }
}
