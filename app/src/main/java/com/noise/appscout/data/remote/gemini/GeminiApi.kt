package com.noise.appscout.data.remote.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Request/response shapes for the Gemini `generateContent` REST endpoint. */
@Serializable
data class GeminiPart(val text: String? = null)

@Serializable
data class GeminiContent(val parts: List<GeminiPart> = emptyList())

@Serializable
data class GeminiGenerationConfig(
    @SerialName("responseMimeType") val responseMimeType: String? = null,
    @SerialName("responseSchema") val responseSchema: GeminiSchema? = null,
    @SerialName("temperature") val temperature: Double? = null,
)

@Serializable
data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchema>? = null,
    val items: GeminiSchema? = null,
    @SerialName("propertyOrdering") val propertyOrdering: List<String>? = null,
)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
data class GeminiErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

@Serializable
data class GeminiError(val error: GeminiErrorBody? = null)

interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest,
    ): Response<GeminiResponse>
}
