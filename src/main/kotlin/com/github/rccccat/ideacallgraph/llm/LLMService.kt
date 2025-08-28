package com.github.rccccat.ideacallgraph.llm

import com.github.rccccat.ideacallgraph.model.CallGraph
import com.github.rccccat.ideacallgraph.export.CallGraphJsonExporter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonArray

/**
 * Service for interacting with LLM APIs
 */
@Service(Service.Level.APP)
class LLMService {
    
    private val logger = thisLogger()
    private val settings = LLMSettings.getInstance()
    private val jsonExporter = CallGraphJsonExporter()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    
    companion object {
        fun getInstance(): LLMService {
            return ApplicationManager.getApplication().getService(LLMService::class.java)
        }
    }

    /**
     * Analyzes a call graph using LLM
     */
    suspend fun analyzeCallGraph(callGraph: CallGraph): Result<String> {
        if (!settings.enabled) {
            return Result.failure(IllegalStateException("LLM analysis is not enabled"))
        }

        if (settings.apiKey.isEmpty()) {
            return Result.failure(IllegalStateException("API key is not configured"))
        }

        return try {
            val jsonData = jsonExporter.exportToJson(callGraph)
            
            val requestBody = createRequestBody(jsonData)
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${settings.baseUrl}/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .timeout(Duration.ofSeconds(settings.timeout.toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }

            if (response.statusCode() == 200) {
                parseResponse(response.body())
            } else {
                Result.failure(RuntimeException("HTTP ${response.statusCode()}: ${response.body()}"))
            }
        } catch (e: Exception) {
            logger.warn("LLM analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Analyzes call graph with progress indication
     */
    fun analyzeCallGraphWithProgress(
        project: Project,
        callGraph: CallGraph,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        object : Task.Backgroundable(project, "Analyzing Call Graph with LLM", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Preparing call graph data..."
                indicator.fraction = 0.1

                indicator.text = "Sending request to LLM..."
                indicator.fraction = 0.3

                try {
                    val result = runBlocking {
                        analyzeCallGraph(callGraph)
                    }

                    indicator.fraction = 1.0

                    ApplicationManager.getApplication().invokeLater {
                        result.fold(
                            onSuccess = { response ->
                                onSuccess(response)
                            },
                            onFailure = { error ->
                                onError(error)
                            }
                        )
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        onError(e)
                    }
                }
            }
        }.queue()
    }

    /**
     * Tests the LLM connection
     */
    suspend fun testConnection(): Result<String> {
        if (!settings.enabled || settings.apiKey.isEmpty()) {
            return Result.failure(IllegalStateException("LLM is not properly configured"))
        }

        return try {
            val requestBody = createTestRequestBody()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${settings.baseUrl}/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }

            if (response.statusCode() == 200) {
                parseResponse(response.body())
            } else {
                Result.failure(RuntimeException("HTTP ${response.statusCode()}: ${response.body()}"))
            }
        } catch (e: Exception) {
            logger.warn("LLM connection test failed", e)
            Result.failure(e)
        }
    }

    private fun createRequestBody(callGraphJson: String): String {
        val messages = JsonArray()
        
        val systemMessage = JsonObject()
        systemMessage.addProperty("role", "system")
        systemMessage.addProperty("content", settings.systemPrompt)
        messages.add(systemMessage)
        
        val userMessage = JsonObject()
        userMessage.addProperty("role", "user")
        userMessage.addProperty("content", "Please analyze this call graph:\n\n$callGraphJson")
        messages.add(userMessage)
        
        val requestBody = JsonObject()
        requestBody.addProperty("model", settings.model)
        requestBody.add("messages", messages)
        requestBody.addProperty("temperature", 0.3)
        requestBody.addProperty("max_tokens", 4000)
        
        return requestBody.toString()
    }

    private fun createTestRequestBody(): String {
        val messages = JsonArray()
        
        val userMessage = JsonObject()
        userMessage.addProperty("role", "user")
        userMessage.addProperty("content", "Hello, this is a connection test. Please respond with 'Connection successful'.")
        messages.add(userMessage)
        
        val requestBody = JsonObject()
        requestBody.addProperty("model", settings.model)
        requestBody.add("messages", messages)
        requestBody.addProperty("max_tokens", 50)
        
        return requestBody.toString()
    }

    private fun parseResponse(responseBody: String): Result<String> {
        return try {
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val choices = jsonResponse.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val choice = choices[0].asJsonObject
                val message = choice.getAsJsonObject("message")
                val content = message.get("content").asString
                Result.success(content)
            } else {
                Result.failure(RuntimeException("No response content received from LLM"))
            }
        } catch (e: Exception) {
            Result.failure(RuntimeException("Failed to parse LLM response: ${e.message}"))
        }
    }

    /**
     * Validates the current settings
     */
    fun validateSettings(): List<String> {
        val errors = mutableListOf<String>()

        if (!settings.enabled) {
            errors.add("LLM analysis is disabled")
        }

        if (settings.apiKey.isEmpty()) {
            errors.add("API key is required")
        }

        if (settings.baseUrl.isEmpty()) {
            errors.add("Base URL is required")
        }

        if (settings.model.isEmpty()) {
            errors.add("Model name is required")
        }

        if (settings.systemPrompt.isEmpty()) {
            errors.add("System prompt is required")
        }

        if (settings.timeout < 5 || settings.timeout > 300) {
            errors.add("Timeout must be between 5 and 300 seconds")
        }

        return errors
    }
}