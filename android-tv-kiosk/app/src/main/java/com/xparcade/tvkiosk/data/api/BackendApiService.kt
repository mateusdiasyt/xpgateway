package com.xparcade.tvkiosk.data.api

import com.xparcade.tvkiosk.domain.model.CreatePaymentRequest
import com.xparcade.tvkiosk.domain.model.CreatePaymentResponse
import com.xparcade.tvkiosk.domain.model.ForceUnlockRequest
import com.xparcade.tvkiosk.domain.model.LastPaymentWrapper
import com.xparcade.tvkiosk.domain.model.SessionStatusResponse
import com.xparcade.tvkiosk.domain.model.StationConfigResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface BackendApiService {

    @GET("/health")
    suspend fun healthCheck(): Map<String, Any>

    @GET("/api/stations/{stationId}/config")
    suspend fun getStationConfig(
        @Path("stationId") stationId: String,
        @Header("x-station-id") stationHeaderId: String,
        @Header("x-station-token") stationToken: String
    ): StationConfigResponse

    @GET("/api/stations/{stationId}/last-payment")
    suspend fun getLastPayment(
        @Path("stationId") stationId: String,
        @Header("x-station-id") stationHeaderId: String,
        @Header("x-station-token") stationToken: String
    ): LastPaymentWrapper

    @POST("/api/sessions/create-payment")
    suspend fun createPayment(
        @Header("x-station-id") stationHeaderId: String,
        @Header("x-station-token") stationToken: String,
        @Body request: CreatePaymentRequest
    ): CreatePaymentResponse

    @GET("/api/sessions/{sessionId}/status")
    suspend fun getSessionStatus(
        @Path("sessionId") sessionId: String,
        @Header("x-station-id") stationHeaderId: String,
        @Header("x-station-token") stationToken: String
    ): SessionStatusResponse

    @POST("/api/admin/stations/{stationId}/force-unlock")
    suspend fun forceUnlock(
        @Path("stationId") stationId: String,
        @Header("x-admin-key") adminKey: String,
        @Body request: ForceUnlockRequest
    ): Map<String, Any>

    @POST("/api/admin/sessions/{sessionId}/end")
    suspend fun endSession(
        @Path("sessionId") sessionId: String,
        @Header("x-admin-key") adminKey: String,
        @Body body: Map<String, String> = emptyMap()
    ): Map<String, Any>

    @POST("/api/admin/payments/{providerPaymentId}/mock-confirm")
    suspend fun confirmMockPayment(
        @Path("providerPaymentId") providerPaymentId: String,
        @Header("x-admin-key") adminKey: String,
        @Body body: Map<String, String> = emptyMap()
    ): Map<String, Any>
}
