package dev.aura.auradroid.data.network

import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.Session
import retrofit2.http.*

interface AuraApiService {

    @POST("api/chat")
    suspend fun sendMessage(
        @Body request: ChatRequest
    ): ChatResponse

    @POST("api/chat/stream")
    suspend fun sendMessageStream(
        @Body request: ChatRequest
    ): retrofit2.Response<okhttp3.ResponseBody>

    @GET("api/sessions")
    suspend fun getSessions(): List<Session>

    @GET("api/sessions/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: String): Session

    @POST("api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): Session

    @PUT("api/sessions/{sessionId}")
    suspend fun updateSession(
        @Path("sessionId") sessionId: String,
        @Body request: UpdateSessionRequest
    ): Session

    @DELETE("api/sessions/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: String)

    @GET("api/sessions/{sessionId}/messages")
    suspend fun getMessages(@Path("sessionId") sessionId: String): List<Message>

    @GET("api/models")
    suspend fun getAvailableModels(): List<ModelInfo>

    @GET("api/providers")
    suspend fun getProviders(): List<ProviderInfo>
}

data class ChatRequest(
    val sessionId: String,
    val message: String,
    val model: String,
    val mode: String,
    val context: Map<String, Any>? = null
)

data class ChatResponse(
    val messageId: String,
    val content: String,
    val role: String,
    val toolCalls: List<ToolCallResponse>? = null,
    val finishReason: String? = null
)

data class ToolCallResponse(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>,
    val result: String? = null
)

data class CreateSessionRequest(
    val title: String,
    val model: String,
    val provider: String,
    val mode: String,
    val projectPath: String? = null
)

data class UpdateSessionRequest(
    val title: String? = null,
    val isPinned: Boolean? = null,
    val isArchived: Boolean? = null
)

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val contextLength: Int,
    val supportsStreaming: Boolean
)

data class ProviderInfo(
    val name: String,
    val baseUrl: String,
    val requiresApiKey: Boolean
)
