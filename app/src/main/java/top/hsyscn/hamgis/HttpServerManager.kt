package top.hsyscn.hamgis

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class HttpServerManager(private val context: Context) : NanoHTTPD(8888) {

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages = _logMessages.asStateFlow()

    private val _serverState = MutableStateFlow("Stopped")
    val serverState = _serverState.asStateFlow()

    var onDataReceived: ((String) -> Unit)? = null

    fun startServer() {
        try {
            start(30000, false)
            _serverState.value = "Running on port 8888"
            log("HTTP Server Started on port 8888")
        } catch (e: IOException) {
            _serverState.value = "Error: ${e.message}"
            log("Server Start Failed: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stopServer() {
        stop()
        _serverState.value = "Stopped"
        log("HTTP Server Stopped")
    }

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri

        log("Request: $method $uri")
        
        if (method == Method.POST && uri == "/data") {
            try {
                Log.d("HttpServer", "Starting to parse POST request...")
                
                // Directly read the raw body from session
                // This avoids the file name length issue with parseBody()
                var content: String? = null
                try {
                    val body = session.inputStream
                    val buffer = ByteArray(8192)
                    val bodyBuilder = StringBuilder()
                    
                    var bytesRead: Int
                    while (true) {
                        bytesRead = body.read(buffer)
                        if (bytesRead == -1) {
                            break
                        }
                        if (bytesRead > 0) {
                            bodyBuilder.append(String(buffer, 0, bytesRead))
                        }
                    }
                    
                    content = bodyBuilder.toString()
                    Log.d("HttpServer", "Direct read completed, body length: ${content?.length ?: 0}")
                } catch (readException: Exception) {
                    Log.e("HttpServer", "Failed to read body directly: ${readException.message}", readException)
                    readException.printStackTrace()
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Read Error: ${readException.message}")
                }
                
                if (content != null) {
                    // It might be a JSON string like {"postData": "..."}
                    // This is how our Side Service sends it: body: JSON.stringify({ postData: data })
                    try {
                        if (content.trim().startsWith("{")) {
                            val json = org.json.JSONObject(content)
                            if (json.has("postData")) {
                                val actualData = json.optString("postData")
                                if (actualData.isNotEmpty()) {
                                     log("Data Received via JSON wrapper (${actualData.length} chars)")
                                     Log.d("HttpServer", "Received data preview: ${actualData.take(100)}...")
                                     onDataReceived?.invoke(actualData)
                                     return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                                }
                            }
                        }
                    } catch(e: Exception) {
                        log("JSON wrapper parse fail: ${e.message}")
                        Log.e("HttpServer", "JSON parse exception", e)
                    }
                    
                    // If it's not wrapped or parsing failed, treat raw content as data
                    log("Data Received Raw (${content.length} chars)")
                    Log.d("HttpServer", "Received raw data preview: ${content.take(100)}...")
                    onDataReceived?.invoke(content)
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                } else {
                    Log.e("HttpServer", "Empty body received!")
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Empty Body")
                }
            } catch (e: Exception) {
                Log.e("HttpServer", "General exception in serve: ${e.message}", e)
                e.printStackTrace()
                log("Parse Error: ${e.message}")
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
            }
        } else if (method == Method.GET) {
             return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "HAMGIS Receiver is Running")
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }

    private fun log(msg: String) {
        Log.d("HttpServer", msg)
        val list = _logMessages.value.toMutableList()
        list.add(0, msg)
        _logMessages.value = list
    }
}
