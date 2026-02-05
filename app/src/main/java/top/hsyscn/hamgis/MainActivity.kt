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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// 可爱的配色方案
val CutePink = Color(0xFFFF6B9D)
val CutePinkLight = Color(0xFFFFB8D1)
val CutePinkDark = Color(0xFFFF4081)
val CuteBlue = Color(0xFF4FC3F7)
val CuteBlueLight = Color(0xFFB3E5FC)
val CutePurple = Color(0xFFB39DDB)
val CutePurpleLight = Color(0xFFE1BEE7)
val CuteYellow = Color(0xFFFFF176)
val CuteGreen = Color(0xFF81C784)
val CuteBackground = Color(0xFFFCE4EC)
val CuteSurface = Color(0xFFFFFFFF)
val CuteText = Color(0xFF4A4A4A)

class MainActivity : ComponentActivity() {
    
    private lateinit var httpServerManager: HttpServerManager
    private var pendingExportContent: String? = null
    private var pendingExportType: String = "csv" // or "json"
    private var receivedJsonData: String? = null // Store received data for export

    // SAF: Create File
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
                    CuteHttpScreen(
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
                            val uri = android.net.Uri.parse("https://hsyscn.top/HAMGISdirections.html")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
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
        
        // 检测项目类型
        val recordType = root.optString("recordType", "")
        val isGISProject = recordType == "gis_project" || root.has("features")
        
        if (isGISProject) {
            return convertGISProjectToGeoJson(root)
        }
        
        // 测面积项目导出
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
    
    private fun convertGISProjectToGeoJson(root: JSONObject): String {
        Log.d("MainActivity", "convertGISProjectToGeoJson called")
        val projectName = root.optString("name", "Unnamed GIS Project")
        val features = root.optJSONArray("features") ?: JSONArray()
        
        val featuresList = StringBuilder()
        
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val featureName = feature.optString("featureName", "Feature ${i+1}")
            val featureType = feature.optString("featureType", "unknown")
            
            val geometryType = when (featureType) {
                "point" -> "Point"
                "line" -> "LineString"
                "polygon" -> "Polygon"
                else -> "GeometryCollection"
            }
            
            val coordinates = StringBuilder()
            
            when (featureType) {
                "point" -> {
                    val coords = feature.optJSONObject("coords")
                    if (coords != null) {
                        val lat = coords.optDouble("lat", 0.0)
                        val lon = coords.optDouble("lon", 0.0)
                        val alt = coords.optDouble("altitude", 0.0)
                        coordinates.append("[$lon, $lat, $alt]")
                    } else {
                        coordinates.append("[0, 0, 0]")
                    }
                }
                "line", "polygon" -> {
                    val coords = feature.optJSONArray("coords")
                    if (coords != null && coords.length() > 0) {
                        coordinates.append("[")
                        for (j in 0 until coords.length()) {
                            val coord = coords.getJSONObject(j)
                            if (j > 0) coordinates.append(",")
                            val lat = coord.optDouble("lat", 0.0)
                            val lon = coord.optDouble("lon", 0.0)
                            val alt = coord.optDouble("altitude", 0.0)
                            coordinates.append("[$lon, $lat, $alt]")
                        }
                        
                        // 面要素需要闭合
                        if (featureType == "polygon" && coords.length() > 0) {
                            val first = coords.getJSONObject(0)
                            val fLat = first.optDouble("lat", 0.0)
                            val fLon = first.optDouble("lon", 0.0)
                            val fAlt = first.optDouble("altitude", 0.0)
                            val last = coords.getJSONObject(coords.length() - 1)
                            val lLat = last.optDouble("lat", 0.0)
                            val lLon = last.optDouble("lon", 0.0)
                            
                            if (fLat != lLat || fLon != lLon) {
                                coordinates.append(",[$fLon, $fLat, $fAlt]")
                            }
                        }
                        
                        coordinates.append("]")
                        
                        // 面要素需要双重括号
                        if (featureType == "polygon") {
                            coordinates.insert(0, "[")
                            coordinates.append("]")
                        }
                    } else {
                        coordinates.append("[]")
                    }
                }
            }
            
            // 获取属性信息
            val length = if (featureType == "line") {
                feature.optDouble("length", 0.0)
            } else {
                feature.optDouble("perimeter", 0.0)
            }
            val area = if (featureType == "polygon") {
                feature.optJSONObject("area")?.optDouble("squareMeters", 0.0) ?: 0.0
            } else {
                0.0
            }
            
            if (i > 0) featuresList.append(",\n            ")
            
            featuresList.append("""
            {
              "type": "Feature",
              "properties": {
                "name": "$featureName",
                "featureType": "$featureType",
                "length": $length,
                "area": $area
              },
              "geometry": {
                "type": "$geometryType",
                "coordinates": $coordinates
              }
            }
            """.trimIndent())
        }
        
        return """
        {
          "type": "FeatureCollection",
          "metadata": {
            "projectName": "$projectName",
            "timestamp": ${root.optLong("timestamp")},
            "generator": "HAMGIS v1.1.0",
            "featureCount": ${features.length()}
          },
          "features": [
            $featuresList
          ]
        }
        """.trimIndent()
    }
    
    private fun convertJsonToKml(jsonStr: String): String {
        val root = JSONObject(jsonStr)
        
        // 检测项目类型
        val recordType = root.optString("recordType", "")
        val isGISProject = recordType == "gis_project" || root.has("features")
        
        if (isGISProject) {
            return convertGISProjectToKml(root)
        }
        
        // 测面积项目导出
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
    
    private fun convertGISProjectToKml(root: JSONObject): String {
        Log.d("MainActivity", "convertGISProjectToKml called")
        val projectName = root.optString("name", "Unnamed GIS Project")
        val features = root.optJSONArray("features") ?: JSONArray()
        
        val placemarks = StringBuilder()
        
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val featureName = feature.optString("featureName", "Feature ${i+1}")
            val featureType = feature.optString("featureType", "unknown")
            
            val placemarkContent = when (featureType) {
                "point" -> {
                    val coords = feature.optJSONObject("coords")
                    if (coords != null) {
                        val lat = coords.optDouble("lat", 0.0)
                        val lon = coords.optDouble("lon", 0.0)
                        val alt = coords.optDouble("altitude", 0.0)
                        "<Point><coordinates>$lon,$lat,$alt</coordinates></Point>"
                    } else {
                        "<Point><coordinates>0,0,0</coordinates></Point>"
                    }
                }
                "line" -> {
                    val coords = feature.optJSONArray("coords")
                    val coordinates = StringBuilder()
                    if (coords != null) {
                        for (j in 0 until coords.length()) {
                            val coord = coords.getJSONObject(j)
                            if (j > 0) coordinates.append(" ")
                            val lat = coord.optDouble("lat", 0.0)
                            val lon = coord.optDouble("lon", 0.0)
                            val alt = coord.optDouble("altitude", 0.0)
                            coordinates.append("$lon,$lat,$alt")
                        }
                    }
                    """
                    <LineString>
                        <tessellate>1</tessellate>
                        <coordinates>$coordinates</coordinates>
                    </LineString>
                    """.trimIndent()
                }
                "polygon" -> {
                    val coords = feature.optJSONArray("coords")
                    val coordinates = StringBuilder()
                    if (coords != null) {
                        for (j in 0 until coords.length()) {
                            val coord = coords.getJSONObject(j)
                            if (j > 0) coordinates.append(" ")
                            val lat = coord.optDouble("lat", 0.0)
                            val lon = coord.optDouble("lon", 0.0)
                            val alt = coord.optDouble("altitude", 0.0)
                            coordinates.append("$lon,$lat,$alt")
                        }
                        // 闭合多边形
                        if (coords.length() > 0) {
                            val first = coords.getJSONObject(0)
                            val last = coords.getJSONObject(coords.length() - 1)
                            val fLat = first.optDouble("lat", 0.0)
                            val fLon = first.optDouble("lon", 0.0)
                            val fAlt = first.optDouble("altitude", 0.0)
                            val lLat = last.optDouble("lat", 0.0)
                            val lLon = last.optDouble("lon", 0.0)
                            
                            if (fLat != lLat || fLon != lLon) {
                                coordinates.append(" $fLon,$fLat,$fAlt")
                            }
                        }
                    }
                    """
                    <Polygon>
                        <outerBoundaryIs>
                            <LinearRing>
                                <coordinates>$coordinates</coordinates>
                            </LinearRing>
                        </outerBoundaryIs>
                    </Polygon>
                    """.trimIndent()
                }
                else -> "<Point><coordinates>0,0,0</coordinates></Point>"
            }
            
            placemarks.append("""
            <Placemark>
              <name>$featureName</name>
              <description>Feature Type: $featureType</description>
              $placemarkContent
            </Placemark>
            """.trimIndent())
            
            if (i < features.length() - 1) {
                placemarks.append("\n            ")
            }
        }
        
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <name>$projectName</name>
            <description>Generated by HAMGIS v1.1.0 - GIS Project</description>
            $placemarks
          </Document>
        </kml>
        """.trimIndent()
    }
    
    private fun convertJsonToCsv(jsonStr: String): String {
        Log.d("MainActivity", "convertJsonToCsv called, input length: ${jsonStr.length}")
        val root = JSONObject(jsonStr)
        val sb = StringBuilder()
        
        // 检测项目类型
        val recordType = root.optString("recordType", "")
        val isGISProject = recordType == "gis_project" || root.has("features")
        
        if (isGISProject) {
            // GIS项目导出格式
            return convertGISProjectToCsv(root)
        }
        
        // 测面积项目导出格式
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
    
    private fun convertGISProjectToCsv(root: JSONObject): String {
        Log.d("MainActivity", "convertGISProjectToCsv called")
        val sb = StringBuilder()
        val name = root.optString("name", "Unnamed Project")
        val timestamp = root.optLong("timestamp", 0)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        
        // 项目基本信息
        sb.appendLine("=== GIS Project Summary ===")
        sb.appendLine("Project Name,$name")
        sb.appendLine("Timestamp,$dateStr")
        sb.appendLine("Project Type,GIS Collection")
        
        // 要素统计
        val featureCount = root.optJSONObject("featureCount")
        if (featureCount != null) {
            val pointCount = featureCount.optInt("point", 0)
            val lineCount = featureCount.optInt("line", 0)
            val polygonCount = featureCount.optInt("polygon", 0)
            val totalPoints = root.optInt("totalPoints", 0)
            sb.appendLine("Point Features,$pointCount")
            sb.appendLine("Line Features,$lineCount")
            sb.appendLine("Polygon Features,$polygonCount")
            sb.appendLine("Total Points,$totalPoints")
        }
        sb.appendLine()
        
        // 要素详细列表
        sb.appendLine("=== Features Detail ===")
        sb.appendLine("Feature ID,Feature Name,Type,Points Count,Length/Perimeter(m),Area(m²)")
        
        val features = root.optJSONArray("features")
        if (features != null) {
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val featureId = feature.optString("id", "feature_$i")
                val featureName = feature.optString("featureName", "Feature ${i+1}")
                val featureType = feature.optString("featureType", "unknown")
                val coords = feature.optJSONArray("coords")
                val coordCount = coords?.length() ?: 0
                
                val length = if (featureType == "line") {
                    feature.optDouble("length", 0.0)
                } else {
                    feature.optDouble("perimeter", 0.0)
                }
                val area = if (featureType == "polygon") {
                    feature.optJSONObject("area")?.optDouble("squareMeters", 0.0) ?: 0.0
                } else {
                    0.0
                }
                
                sb.appendLine("$featureId,$featureName,$featureType,$coordCount,${String.format("%.2f", length)},${String.format("%.2f", area)}")
            }
        }
        sb.appendLine()
        
        // 坐标详细数据
        sb.appendLine("=== Coordinates Detail ===")
        sb.appendLine("Feature Name,Point Index,Latitude,Longitude,Altitude(m),Timestamp")
        
        if (features != null) {
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val featureName = feature.optString("featureName", "Feature ${i+1}")
                val featureType = feature.optString("featureType", "unknown")
                
                when (featureType) {
                    "point" -> {
                        // 点要素只有一个坐标
                        val coords = feature.optJSONObject("coords")
                        if (coords != null) {
                            val lat = coords.optDouble("lat", 0.0)
                            val lon = coords.optDouble("lon", 0.0)
                            val alt = coords.optDouble("altitude", 0.0)
                            val time = coords.optLong("timestamp", 0)
                            val timeStr = if (time > 0) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time)) else ""
                            sb.appendLine("$featureName,1,${String.format("%.8f", lat)},${String.format("%.8f", lon)},${String.format("%.2f", alt)},$timeStr")
                        }
                    }
                    "line", "polygon" -> {
                        // 线/面要素有多个坐标
                        val coords = feature.optJSONArray("coords")
                        if (coords != null) {
                            for (j in 0 until coords.length()) {
                                val coord = coords.getJSONObject(j)
                                val lat = coord.optDouble("lat", 0.0)
                                val lon = coord.optDouble("lon", 0.0)
                                val alt = coord.optDouble("altitude", 0.0)
                                val time = coord.optLong("timestamp", 0)
                                val timeStr = if (time > 0) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time)) else ""
                                sb.appendLine("$featureName,${j+1},${String.format("%.8f", lat)},${String.format("%.8f", lon)},${String.format("%.2f", alt)},$timeStr")
                            }
                        }
                    }
                }
            }
        }
        
        Log.d("MainActivity", "GIS CSV generated successfully, total length: ${sb.length}")
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
fun CuteHttpScreen(
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

    // 动画状态
    val isRunning = serverState.contains("Running")
    
    // 增强的脉冲动画 - 呼吸灯效果
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    // 背景渐变流动动画
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientFlow"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CuteBackground,
                        CutePinkLight.copy(alpha = 0.3f + 0.1f * gradientOffset),
                        CuteBlueLight.copy(alpha = 0.2f + 0.1f * (1 - gradientOffset)),
                        CutePurpleLight.copy(alpha = 0.15f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // 可爱的标题区域
        CuteHeader()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 状态卡片
        CuteStatusCard(
            isRunning = isRunning,
            pulseScale = if (isRunning) pulseScale else 1f,
            pulseAlpha = if (isRunning) pulseAlpha else 1f,
            serverState = serverState
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // 服务器控制按钮
        CuteControlButtons(
            isRunning = isRunning,
            onStart = onStart,
            onStop = onStop,
            onHelp = onHelp
        )
        
        // 数据预览区域 - 增强的入场动画
        AnimatedVisibility(
            visible = receivedJson != null,
            enter = fadeIn(
                animationSpec = tween(400, easing = EaseOutCubic)
            ) + expandVertically(
                animationSpec = tween(400, easing = EaseOutCubic)
            ) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(
                animationSpec = tween(300)
            ) + shrinkVertically(
                animationSpec = tween(300)
            ) + slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(300)
            )
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                CuteDataCard(
                    receivedJson = receivedJson,
                    context = context,
                    onSaveCsv = onSaveCsv,
                    onSaveJson = onSaveJson,
                    onSaveGeoJson = onSaveGeoJson,
                    onSaveKml = onSaveKml,
                    onDiscard = onDiscard
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // 空状态提示
        AnimatedVisibility(
            visible = receivedJson == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CuteEmptyState()
        }
        
        // 日志标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = null,
                tint = CutePink,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Logs", 
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CutePurple.copy(alpha = 0.2f)
            ) {
                Text(
                    "${logs.size} entries", 
                    style = MaterialTheme.typography.bodySmall,
                    color = CutePurple,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 日志区域
        CuteLogCard(
            logs = logs,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CuteHeader() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CuteSurface,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 装饰图标 - 添加摇摆动画
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 图标摇摆动画
                    val infiniteTransition = rememberInfiniteTransition(label = "headerIcon")
                    val iconRotation by infiniteTransition.animateFloat(
                        initialValue = -8f,
                        targetValue = 8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "iconWiggle"
                    )
                    
                    // 图标缩放呼吸
                    val iconScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "iconScale"
                    )
                    
                    Surface(
                        shape = CircleShape,
                        color = CutePink.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = CutePink,
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer {
                                        rotationZ = iconRotation
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "",
                        fontSize = 24.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "HAMGIS Receiver",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = CutePink
                    )
                )
                
                Text(
                    text = "Cute Data Collector",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = CutePurple
                    )
                )
            }
        }
    }
}

@Composable
fun CuteStatusCard(
    isRunning: Boolean,
    pulseScale: Float,
    pulseAlpha: Float,
    serverState: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CuteSurface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 增强的状态指示器 - 呼吸灯效果
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .scale(if (isRunning) pulseScale else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    // 外圈光晕
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = CuteGreen.copy(alpha = pulseAlpha * 0.3f),
                                    shape = CircleShape
                                )
                        )
                    }
                    // 核心圆点
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isRunning) CuteGreen.copy(alpha = pulseAlpha) else Color(0xFFFF5252),
                                shape = CircleShape
                            )
                            .shadow(
                                elevation = if (isRunning) (8.dp * pulseAlpha) else 0.dp,
                                shape = CircleShape,
                                spotColor = if (isRunning) CuteGreen else Color.Transparent
                            )
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Server Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = CuteText.copy(alpha = 0.6f)
                    )
                    Text(
                        text = serverState,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) CuteGreen else Color(0xFFFF5252)
                        )
                    )
                }
            }
            
            // 状态图标 - 添加入场动画
            val iconScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "iconScale"
            )
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isRunning) CuteGreen.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isRunning) CuteGreen else Color(0xFFFF5252),
                    modifier = Modifier
                        .padding(8.dp)
                        .scale(iconScale)
                )
            }
        }
    }
}

@Composable
fun CuteControlButtons(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHelp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CuteActionButton(
            text = "Start",
            icon = Icons.Default.PlayArrow,
            color = CuteGreen,
            onClick = onStart,
            enabled = !isRunning,
            modifier = Modifier.weight(1f)
        )
        
        CuteActionButton(
            text = "Stop",
            icon = Icons.Default.Close,
            color = Color(0xFFFF5252),
            onClick = onStop,
            enabled = isRunning,
            modifier = Modifier.weight(1f)
        )
        
        CuteActionButton(
            text = "Help",
            icon = Icons.Default.Info,
            color = CuteBlue,
            onClick = onHelp,
            enabled = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CuteActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    // 使用 Spring 动画实现弹性缩放效果
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "springScale"
    )
    
    // 添加图标旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "iconWiggle")
    val iconRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "iconRotation"
    )
    
    // 悬停/按压时的额外动画状态
    val animatedElevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )
    
    Surface(
        onClick = {
            isPressed = true
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) color else color.copy(alpha = 0.3f),
        shadowElevation = if (enabled) animatedElevation.dp else 0.dp,
        modifier = modifier
            .scale(scale)
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        rotationZ = if (isPressed) 15f else 0f
                    }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

@Composable
fun CuteDataCard(
    receivedJson: String?,
    context: android.content.Context,
    onSaveCsv: () -> Unit,
    onSaveJson: () -> Unit,
    onSaveGeoJson: () -> Unit,
    onSaveKml: () -> Unit,
    onDiscard: () -> Unit
) {
    var summaryText by remember(receivedJson) { mutableStateOf("") }
    var isGISProject by remember(receivedJson) { mutableStateOf(false) }
    var projectType by remember(receivedJson) { mutableStateOf("") }
    var projectName by remember(receivedJson) { mutableStateOf("") }
    
    LaunchedEffect(receivedJson) {
        try {
            receivedJson?.let { json ->
                val root = JSONObject(json)
                val name = root.optString("name", "?")
                projectName = name
                val size = json.length
                
                // 检测项目类型
                val recordType = root.optString("recordType", "")
                isGISProject = recordType == "gis_project" || root.has("features")
                projectType = if (isGISProject) "GIS Project" else "Area Measurement"
                
                // 构建摘要信息
                val sb = StringBuilder()
                
                if (isGISProject) {
                    // GIS项目显示要素统计
                    val features = root.optJSONArray("features")
                    val featureCount = features?.length() ?: 0
                    val totalPoints = root.optInt("totalPoints", 0)
                    sb.appendLine("Features: $featureCount")
                    sb.appendLine("Total Points: $totalPoints")
                } else {
                    // 测面积项目显示面积信息
                    val areaObj = root.optJSONObject("area")
                    val areaMu = areaObj?.optDouble("mu", 0.0) ?: 0.0
                    val points = root.optJSONArray("points")
                    val pointCount = points?.length() ?: 0
                    sb.appendLine("Area: ${String.format("%.2f", areaMu)} mu")
                    sb.appendLine("Points: $pointCount")
                }
                
                sb.appendLine("Size: $size bytes")
                summaryText = sb.toString()
            }
        } catch (e: Exception) {
            summaryText = "Error parsing JSON: ${e.message}"
        }
    }
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CuteSurface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = CutePink.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = CutePink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Data Received!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CutePink
                        )
                    )
                    Text(
                        text = projectType,
                        style = MaterialTheme.typography.bodySmall,
                        color = CutePurple
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 项目信息
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CuteBlueLight.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Project: $projectName",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = CuteText
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = CuteText.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 导出按钮
            Text(
                text = "Export Options",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = CuteText.copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 第一行导出按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CuteExportButton(
                    text = "CSV",
                    icon = Icons.Default.List,
                    color = CuteGreen,
                    onClick = onSaveCsv,
                    modifier = Modifier.weight(1f)
                )
                CuteExportButton(
                    text = "GeoJSON",
                    icon = Icons.Default.LocationOn,
                    color = CuteBlue,
                    onClick = onSaveGeoJson,
                    modifier = Modifier.weight(1f)
                )
                CuteExportButton(
                    text = "KML",
                    icon = Icons.Default.LocationOn,
                    color = CutePurple,
                    onClick = onSaveKml,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // JSON 导出按钮
            CuteExportButton(
                text = "Save as JSON",
                icon = Icons.Default.Info,
                color = CutePink,
                onClick = onSaveJson,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 丢弃按钮
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF5252)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Discard Data")
            }
        }
    }
}

@Composable
fun CuteExportButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    // Spring 弹性动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "exportSpringScale"
    )
    
    // 图标摇摆动画
    val iconRotation by animateFloatAsState(
        targetValue = if (isPressed) -10f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconWiggle"
    )
    
    Button(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier
            .scale(scale)
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    rotationZ = iconRotation
                }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

@Composable
fun CuteEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 灵动的云朵浮动动画 - 上下浮动+左右轻微摆动
            val infiniteTransition = rememberInfiniteTransition(label = "cloudFloat")
            
            // 垂直浮动 - 使用正弦波模拟自然浮动
            val floatY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "floatY"
            )
            
            // 水平摆动 - 与垂直不同步，产生更自然的效果
            val floatX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "floatX"
            )
            
            // 旋转摇摆 - 轻微的角度变化
            val rotation by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rotation"
            )
            
            // 缩放呼吸效果
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            
            // 计算偏移量 - 使用正弦波
            val offsetY = kotlin.math.sin(floatY * 2 * kotlin.math.PI) * 12f
            val offsetX = kotlin.math.sin(floatX * 2 * kotlin.math.PI) * 6f
            
            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp, y = offsetY.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                // 外层光晕
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CuteBlueLight.copy(alpha = 0.5f),
                                    CuteBlueLight.copy(alpha = 0f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
                
                Surface(
                    shape = CircleShape,
                    color = CuteBlueLight.copy(alpha = 0.5f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = CuteBlue,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 文字淡入淡出效果
            val textAlpha by infiniteTransition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "textAlpha"
            )
            
            Text(
                text = "Waiting for data...",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = textAlpha),
                    fontWeight = FontWeight.Medium
                )
            )
            
            Text(
                text = "Send data from your watch",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.8f)
                )
            )
        }
    }
}

@Composable
fun CuteLogCard(logs: List<String>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CuteSurface,
        shadowElevation = 2.dp
    ) {
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = CuteText.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No logs yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CuteText.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                itemsIndexed(logs) { index, log ->
                    CuteLogItem(log = log, isLast = logs.last() == log, index = index)
                }
            }
        }
    }
}

@Composable
fun CuteLogItem(log: String, isLast: Boolean, index: Int = 0) {
    val logColor = when {
        log.contains("Error", ignoreCase = true) || log.contains("Failed", ignoreCase = true) -> Color(0xFFFF5252)
        log.contains("Success", ignoreCase = true) || log.contains("Verified", ignoreCase = true) -> CuteGreen
        log.contains("Received", ignoreCase = true) || log.contains("started", ignoreCase = true) -> CuteBlue
        else -> CuteText.copy(alpha = 0.7f)
    }
    
    val icon = when {
        log.contains("Error", ignoreCase = true) -> Icons.Default.Close
        log.contains("Success", ignoreCase = true) -> Icons.Default.CheckCircle
        log.contains("Received", ignoreCase = true) -> Icons.Default.Add
        else -> Icons.Default.Info
    }
    
    // 入场动画
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 50L)
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = EaseOutCubic),
        label = "logAlpha"
    )
    
    val slideOffset by animateFloatAsState(
        targetValue = if (visible) 0f else -20f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "logSlide"
    )
    
    Column(
        modifier = Modifier
            .alpha(alpha)
            .offset(y = slideOffset.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = logColor.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = log,
                style = MaterialTheme.typography.bodySmall,
                color = logColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = CutePurple.copy(alpha = 0.2f)
            )
        }
    }
}
