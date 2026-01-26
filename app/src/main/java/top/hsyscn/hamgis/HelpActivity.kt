package top.hsyscn.hamgis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.hsyscn.hamgis.ui.theme.HAMGISTheme

class HelpActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HAMGISTheme {
                HelpScreen()
            }
        }
    }
}

@Composable
fun HelpScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = context.getString(R.string.help_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Overview
            HelpSection(
                title = context.getString(R.string.help_overview),
                content = context.getString(R.string.help_overview_content)
            )
            
            // Workflow
            HelpSection(
                title = context.getString(R.string.help_workflow),
                content = context.getString(R.string.help_data_sync_content)
            )
            
            // Watch App
            HelpSection(
                title = context.getString(R.string.help_watch),
                content = context.getString(R.string.help_watch_content)
            )
            
            // Phone App
            HelpSection(
                title = context.getString(R.string.help_phone),
                content = context.getString(R.string.help_phone_content)
            )
            
            // Export Function
            HelpSection(
                title = context.getString(R.string.help_export),
                content = context.getString(R.string.help_export_content)
            )
            
            // Working Modes
            HelpSection(
                title = context.getString(R.string.help_modes),
                content = context.getString(R.string.help_modes_content)
            )
            
            // Data Sync
            HelpSection(
                title = context.getString(R.string.help_data_sync),
                content = context.getString(R.string.help_data_sync_content)
            )
            
            // Troubleshooting
            HelpSection(
                title = context.getString(R.string.help_troubleshooting),
                content = context.getString(R.string.help_troubleshooting_content)
            )
        }
    }
}

@Composable
fun HelpSection(
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCCCCCC),
                lineHeight = 1.5.sp
            )
        }
    }
}
