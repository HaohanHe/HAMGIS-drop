package top.hsyscn.hamgis

import android.Manifest
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.os.Bundle
import android.content.Intent
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import top.hsyscn.hamgis.ui.theme.HAMGISTheme
import java.io.OutputStreamWriter
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    
    private lateinit var httpServerManager: HttpServerManager
    private var pendingExportContent: String? = null
    private var pendingExportType: String = "csv" // or "json"
    private var receivedJsonData: String? = null // Store received data for export

    // SAF: Crea
    // te File
    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
        uri?.let {
            saveContentToUri(it, pendingExportContent ?: "")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        httpServerManager = HttpServerManager(this)
        
        // Auto-start the HTTP server
        httpServerManager.startServer()
        Log.d("MainActivity", "HTTP Server auto-started on port 8888")
        
        setContent {
            HAMGISTheme {
                // UI State for received data
                var uiReceivedJson by remember { mutableStateOf<String?>(null) }
                
                // Update callback to update both member and UI state
                DisposableEffect(Unit) {
                    httpServerManager.onDataReceived = { jsonStr ->
                        receivedJsonData = jsonStr
                        runOnUiThread {
                            uiReceivedJson = jsonStr
                            Toast.makeText(this@MainActivity, getString(R.string.msg_data_received), Toast.LENGTH_LONG).show()
                        }
                    }
                    onDispose { 
                        httpServerManager.onDataReceived = null 
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HttpScreen(
                        httpServerManager = httpServerManager,
                        receivedJson = uiReceivedJson,
                        modifier = Modifier.padding(innerPadding),
                        onStart = { httpServerManager.startServer() },
                        onStop = { httpServerManager.stopServer() },
                        onSaveCsv = { startExportProcess("csv") },
                        onSaveJson = { startExportProcess("json") },
                        onSaveGeoJson = { startExportProcess("geojson") },
                        onSaveKml = { startExportProcess("kml") },
                        onDiscard = { 
                            uiReceivedJson = null 
                            receivedJsonData = null
                        },
                        onHelp = {
                            val intent = Intent(this@MainActivity, HelpActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
    
    private fun startExportProcess(type: String) {
        val jsonStr = receivedJsonData ?: return
        
        Log.d("MainActivity", "startExportProcess called, type: $type, hasData: ${jsonStr != null}")
        
        try {
            val content = when (type) {
                "csv" -> convertJsonToCsv(jsonStr)
                "geojson" -> convertJsonToGeoJson(jsonStr)
                "kml" -> convertJsonToKml(jsonStr)
                "json" -> {
                    // 格式化JSON，确保它是有效的
                    val jsonObject = JSONObject(jsonStr)
                    jsonObject.toString(2) // 格式化并缩进2个空格
                }
                else -> jsonStr // 其他情况保持原样
            }
            pendingExportContent = content
            pendingExportType = type
            
            // Launch SAF Picker
            val ext = type
            val fileName = "HAMGIS_Export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.$ext"
            Log.d("MainActivity", "Launching SAF picker for file: $fileName")
            createFileLauncher.launch(fileName)
            
            Log.d("MainActivity", "Export process completed, content length: ${content?.length ?: 0}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Export process failed: ${e.message}", e)
            e.printStackTrace()
            Toast.makeText(this, "Convert Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun convertJsonToGeoJson(jsonStr: String): String {
        val root = JSONObject(jsonStr)
        val name = root.optString("name", "Unnamed")
        val type = root.optString("type", "polygon")
        val pointsArr = root.optJSONArray("points") ?: JSONArray()
        
        val coordinates = StringBuilder()
        
        val geometryType = when (type) {
            "point" -> "MultiPoint"
            "line" -> "LineString"
            else -> "Polygon"
        }
        
        if (type == "point" || type == "line") {
            coordinates.append("[")
            for (i in 0 until pointsArr.length()) {
                val p = pointsArr.getJSONObject(i)
                if (i > 0) coordinates.append(",")
                val lat = if (p.has("latitude")) p.optDouble("latitude") else p.optDouble("lat", 0.0)
                val lon = if (p.has("longitude")) p.optDouble("longitude") else p.optDouble("lon", 0.0)
                val alt = p.optDouble("altitude", 0.0)
                coordinates.append("[$lon, $lat, $alt]")
            }
            coordinates.append("]")
        } else {
            coordinates.append("[[")
            for (i in 0 until pointsArr.length()) {
                val p = pointsArr.getJSONObject(i)
                if (i > 0) coordinates.append(",")
                val lat = if (p.has("latitude")) p.optDouble("latitude") else p.optDouble("lat", 0.0)
                val lon = if (p.has("longitude")) p.optDouble("longitude") else p.optDouble("lon", 0.0)
                val alt = p.optDouble("altitude", 0.0)
                coordinates.append("[$lon, $lat, $alt]")
            }
            if (pointsArr.length() > 0) {
                val first = pointsArr.getJSONObject(0)
                val last = pointsArr.getJSONObject(pointsArr.length() - 1)
                
                val fLat = if (first.has("latitude")) first.optDouble("latitude") else first.optDouble("lat", 0.0)
                val fLon = if (first.has("longitude")) first.optDouble("longitude") else first.optDouble("lon", 0.0)
                val fAlt = first.optDouble("altitude", 0.0)
                
                val lLat = if (last.has("latitude")) last.optDouble("latitude") else last.optDouble("lat", 0.0)
                val lLon = if (last.has("longitude")) last.optDouble("longitude") else last.optDouble("lon", 0.0)

                if (fLat != lLat || fLon != lLon) {
                    coordinates.append(",[$fLon, $fLat, $fAlt]")
                }
            }
            coordinates.append("]]")
        }
        
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "name": "$name",
                "timestamp": ${root.optLong("timestamp")},
                "desc": "Generated by HAMGIS"
              },
              "geometry": {
                "type": "$geometryType",
                "coordinates": $coordinates
              }
            }
          ]
        }
        """.trimIndent()
    }
    
    private fun convertJsonToKml(jsonStr: String): String {
        val root = JSONObject(jsonStr)
        val name = root.optString("name", "Unnamed")
        val type = root.optString("type", "polygon")
        val pointsArr = root.optJSONArray("points") ?: JSONArray()
        
        val coordinates = StringBuilder()
        for (i in 0 until pointsArr.length()) {
            val p = pointsArr.getJSONObject(i)
            if (i > 0) coordinates.append(" ")
            val lat = if (p.has("latitude")) p.optDouble("latitude") else p.optDouble("lat", 0.0)
            val lon = if (p.has("longitude")) p.optDouble("longitude") else p.optDouble("lon", 0.0)
            val alt = p.optDouble("altitude", 0.0)
            coordinates.append("$lon,$lat,$alt")
        }
        
        if (type == "polygon" && pointsArr.length() > 0) {
            val first = pointsArr.getJSONObject(0)
            val last = pointsArr.getJSONObject(pointsArr.length() - 1)
            
            val fLat = if (first.has("latitude")) first.optDouble("latitude") else first.optDouble("lat", 0.0)
            val fLon = if (first.has("longitude")) first.optDouble("longitude") else first.optDouble("lon", 0.0)
            val fAlt = first.optDouble("altitude", 0.0)
            
            val lLat = if (last.has("latitude")) last.optDouble("latitude") else last.optDouble("lat", 0.0)
            val lLon = if (last.has("longitude")) last.optDouble("longitude") else last.optDouble("lon", 0.0)

            if (fLat != lLat || fLon != lLon) {
                 coordinates.append(" $fLon,$fLat,$fAlt")
            }
        }
        
        val placemarkContent = when (type) {
            "point" -> {
                val sb = StringBuilder("<MultiGeometry>\n")
                for (i in 0 until pointsArr.length()) {
                    val p = pointsArr.getJSONObject(i)
                    val lat = if (p.has("latitude")) p.optDouble("latitude") else p.optDouble("lat", 0.0)
                    val lon = if (p.has("longitude")) p.optDouble("longitude") else p.optDouble("lon", 0.0)
                    val alt = p.optDouble("altitude", 0.0)
                    sb.append("<Point><coordinates>$lon,$lat,$alt</coordinates></Point>\n")
                }
                sb.append("</MultiGeometry>")
                sb.toString()
            }
            "line" -> """
                <LineString>
                    <tessellate>1</tessellate>
                    <coordinates>$coordinates</coordinates>
                </LineString>
            """.trimIndent()
            else -> """
                <Polygon>
                    <outerBoundaryIs>
                        <LinearRing>
                            <coordinates>$coordinates</coordinates>
                        </LinearRing>
                    </outerBoundaryIs>
                </Polygon>
            """.trimIndent()
        }

        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <name>$name</name>
            <Placemark>
              <name>$name</name>
              $placemarkContent
            </Placemark>
          </Document>
        </kml>
        """.trimIndent()
    }
    
    private fun convertJsonToCsv(jsonStr: String): String {
        Log.d("MainActivity", "convertJsonToCsv called, input length: ${jsonStr.length}")
        val root = JSONObject(jsonStr)
        val sb = StringBuilder()
        
        // Header
        sb.append("Name,Timestamp,Area (Mu),Points Count,Perimeter (m),Avg Altitude (m)\n")
        
        val name = root.optString("name", "Unnamed")
        val timestamp = root.optLong("timestamp", 0)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        
        val areaObj = root.optJSONObject("area")
        val areaMu = areaObj?.optDouble("mu", 0.0) ?: 0.0
        val areaSqm = areaObj?.optDouble("squareMeters", 0.0) ?: 0.0
        
        val pointsArr = root.optJSONArray("points") ?: JSONArray()
        val pointsCount = pointsArr.length()
        
        val perimeter = root.optDouble("perimeter", 0.0)
        
        // Handle elevation object or direct avgAltitude field
        val elevationObj = root.optJSONObject("elevation")
        val avgAlt = if (elevationObj != null) {
            elevationObj.optDouble("average", 0.0)
        } else {
            root.optDouble("avgAltitude", 0.0)
        }
        
        Log.d("MainActivity", "CSV Summary - Name: $name, Points: $pointsCount, Area: $areaMu, Perimeter: $perimeter, AvgAlt: $avgAlt")
        
        // 增加导出精度：面积保留4位，周长保留2位，海拔保留2位
        sb.append("$name,$dateStr,${String.format("%.4f", areaMu)},$pointsCount,${String.format("%.2f", perimeter)},${String.format("%.2f", avgAlt)}\n")
        
        sb.append("\n--- Points Detail ---\n")
        sb.append("Index,Latitude,Longitude,Altitude (m),Time\n")
        
        for (i in 0 until pointsCount) {
            val p = pointsArr.getJSONObject(i)
            val lat = if (p.has("latitude")) p.optDouble("latitude") else p.optDouble("lat", 0.0)
            val lon = if (p.has("longitude")) p.optDouble("longitude") else p.optDouble("lon", 0.0)
            val alt = p.optDouble("altitude", 0.0)
            val time = p.optLong("timestamp", 0)
            val timeStr = if (time > 0) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time)) else ""
            
            // 坐标精度显式保留8位
            sb.append("${i+1},${String.format("%.8f", lat)},${String.format("%.8f", lon)},${String.format("%.2f", alt)},$timeStr\n")
        }
        
        Log.d("MainActivity", "CSV generated successfully, total length: ${sb.length}")
        return sb.toString()
    }

    private fun saveContentToUri(uri: Uri, content: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            }
            Toast.makeText(this, "File Saved Successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val hostAddress = addr.hostAddress
                    if (!addr.isLoopbackAddress && hostAddress != null && hostAddress.indexOf(':') < 0) {
                        return hostAddress
                    }
                }
            }
        } catch (e: Exception) { }
        return "Unknown"
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServerManager.stopServer()
    }
}

@Composable
fun HttpScreen(
    httpServerManager: HttpServerManager,
    receivedJson: String?,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSaveCsv: () -> Unit,
    onSaveJson: () -> Unit,
    onSaveGeoJson: () -> Unit,
    onSaveKml: () -> Unit,
    onDiscard: () -> Unit,
    onHelp: () -> Unit
) {
    val logs by httpServerManager.logMessages.collectAsState()
    val serverState by httpServerManager.serverState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "HAMGIS HTTP Receiver", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        val isRunning = serverState.contains("Running")
        
        Text(text = "Status: $serverState", 
             style = MaterialTheme.typography.bodyLarge, 
             color = if(isRunning) Color.Green else Color.Gray)
             
        if (isRunning) {
            // Display IP address hint
            // In a real scenario, we would display the phone's IP
            // Text(text = "IP: ...", style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = onStart) { Text(context.getString(R.string.btn_start_server)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onStop) { Text(context.getString(R.string.btn_stop_server)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onHelp) { Text(context.getString(R.string.btn_help)) }
        }
        
        // Data Preview Section
        if (receivedJson != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = context.getString(R.string.label_data_preview), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Parse Summary
                    var summaryText by remember(receivedJson) { mutableStateOf("") }
                    LaunchedEffect(receivedJson) {
                         try {
                             receivedJson?.let { json ->
                                 val root = JSONObject(json)
                                 val name = root.optString("name", "?")
                                 val size = json.length
                                 summaryText = "${context.getString(R.string.label_project_name, name)}\n${context.getString(R.string.label_data_size, size.toString())}"
                             }
                         } catch (e: Exception) {
                             summaryText = "Error parsing JSON"
                         }
                    }
                    
                    Text(text = summaryText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = onSaveCsv) { Text(context.getString(R.string.btn_save_csv)) }
                        Button(onClick = onSaveGeoJson) { Text(context.getString(R.string.btn_save_geojson)) }
                        Button(onClick = onSaveKml) { Text(context.getString(R.string.btn_save_kml)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(onClick = onSaveJson) { Text(context.getString(R.string.btn_save_json)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDiscard, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) { 
                        Text(context.getString(R.string.btn_discard)) 
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(context.getString(R.string.label_logs), style = MaterialTheme.typography.titleMedium)
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.LightGray.copy(alpha = 0.2f))
                .padding(8.dp)
        ) {
            items(logs) { log ->
                Text(text = log, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }
        }
    }
}
