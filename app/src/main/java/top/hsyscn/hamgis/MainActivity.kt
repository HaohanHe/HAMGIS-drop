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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
        // 标题区域
        Text(
            text = "HAMGIS HTTP Receiver", 
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // 状态显示
        val isRunning = serverState.contains("Running")
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Status:", 
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = serverState, 
                style = MaterialTheme.typography.bodyLarge, 
                color = if(isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // 服务器控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(context.getString(R.string.btn_start_server))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(context.getString(R.string.btn_stop_server))
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 帮助按钮 - 正常样式
            Button(
                onClick = onHelp,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Text(context.getString(R.string.btn_help))
            }
        }
        
        // 数据预览区域
        if (receivedJson != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = context.getString(R.string.label_data_preview), 
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 数据摘要
                    var summaryText by remember(receivedJson) { mutableStateOf("") }
                    var isGISProject by remember(receivedJson) { mutableStateOf(false) }
                    var projectType by remember(receivedJson) { mutableStateOf("") }
                    
                    LaunchedEffect(receivedJson) {
                         try {
                             receivedJson?.let { json ->
                                 val root = JSONObject(json)
                                 val name = root.optString("name", "?")
                                 val size = json.length
                                 
                                 // 检测项目类型
                                 val recordType = root.optString("recordType", "")
                                 isGISProject = recordType == "gis_project" || root.has("features")
                                 projectType = if (isGISProject) "GIS Project" else "Area Measurement"
                                 
                                 // 构建摘要信息
                                 val sb = StringBuilder()
                                 sb.appendLine("${context.getString(R.string.label_project_name, name)}")
                                 sb.appendLine("Type: $projectType")
                                 
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
                                 
                                 sb.appendLine(context.getString(R.string.label_data_size, size.toString()))
                                 summaryText = sb.toString()
                             }
                         } catch (e: Exception) {
                             summaryText = "Error parsing JSON: ${e.message}"
                         }
                    }
                    
                    Text(
                        text = summaryText, 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 导出按钮网格
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = onSaveCsv,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(context.getString(R.string.btn_save_csv))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onSaveGeoJson,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(context.getString(R.string.btn_save_geojson))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onSaveKml,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(context.getString(R.string.btn_save_kml))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onSaveJson,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(context.getString(R.string.btn_save_json))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onDiscard, 
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { 
                            Text(context.getString(R.string.btn_discard)) 
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // 日志标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                context.getString(R.string.label_logs), 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${logs.size} entries", 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 日志区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                items(logs) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = it, 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (logs.lastIndex > logs.indexOf(it)) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "No logs yet", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .wrapContentHeight()
                        )
                    }
                }
            }
        }
    }
}
