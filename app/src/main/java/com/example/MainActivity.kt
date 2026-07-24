package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) { // Force dark mode as requested
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        floatingActionButton = {
                            val context = LocalContext.current
                            FloatingActionButton(onClick = {
                                SpeedTracker.dailyUsage.value = 0L
                                SpeedTracker.monthlyUsage.value = 0L
                                Toast.makeText(context, "تم تصفير الإحصائيات", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Usage")
                            }
                        }
                    ) { innerPadding ->
                        MainScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val downloadSpeed by SpeedTracker.currentDownloadSpeed.collectAsState()
    val uploadSpeed by SpeedTracker.currentUploadSpeed.collectAsState()
    val dailyUsage by SpeedTracker.dailyUsage.collectAsState()
    val monthlyUsage by SpeedTracker.monthlyUsage.collectAsState()

    val dailyLimitMb by SettingsManager.getDailyLimit(context).collectAsState(initial = 0)
    val gaugeColorName by SettingsManager.getGaugeColor(context).collectAsState(initial = "Primary")

    var hasUsagePermission by remember { mutableStateOf(NetworkStatsHelper.hasUsageStatsPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val serviceIntent = Intent(context, SpeedMonitorService::class.java)
        context.startForegroundService(serviceIntent)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = NetworkStatsHelper.hasUsageStatsPermission(context)
                if (hasUsagePermission) {
                    NetworkStatsHelper.getUsageStats(context)
                }
                scope.launch {
                    SettingsManager.getDataOffsets(context).collect { offsets ->
                        SpeedTracker.dailyOffset.value = offsets.first
                        SpeedTracker.monthlyOffset.value = offsets.second
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val serviceIntent = Intent(context, SpeedMonitorService::class.java)
                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                    }
                } else {
                    val serviceIntent = Intent(context, SpeedMonitorService::class.java)
                    androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasUsagePermission) {
        while (true) {
            if (hasUsagePermission) {
                NetworkStatsHelper.getUsageStats(context)
            }
            delay(5000) // update every 5 seconds
        }
    }

    var showLimitDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "مراقب سرعة الإنترنت",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Speedometer(downloadSpeed, uploadSpeed, gaugeColorName)
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UsageCard(
                    title = "استهلاك اليوم",
                    value = formatBytes(dailyUsage),
                    icon = Icons.Default.Today,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                UsageCard(
                    title = "استهلاك الشهر",
                    value = formatBytes(monthlyUsage),
                    icon = Icons.Default.DateRange,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            if (!hasUsagePermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { NetworkStatsHelper.requestUsageStatsPermission(context) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "اضغط هنا لمنح صلاحية الوصول لبيانات الاستخدام للحصول على إحصائيات دقيقة.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                // Check limit warning
                if (dailyLimitMb > 0) {
                    val dailyUsageMb = dailyUsage / (1024 * 1024)
                    if (dailyUsageMb >= dailyLimitMb) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "تنبيه: لقد تجاوزت الحد اليومي لاستهلاك البيانات!",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (dailyUsageMb >= dailyLimitMb * 0.9) {
                         Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA000)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "تنبيه: اقتربت من الحد اليومي لاستهلاك البيانات.",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الإعدادات", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLimitDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("تحديد الاستهلاك اليومي (ميغابايت)", fontSize = 16.sp)
                            Text(
                                text = if (dailyLimitMb == 0) "غير محدد" else "$dailyLimitMb MB",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showColorDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("تخصيص مظهر الأيقونة", fontSize = 16.sp)
                            Text("اختر لون العداد المفضل", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (showLimitDialog) {
        var limitInput by remember { mutableStateOf(if (dailyLimitMb > 0) dailyLimitMb.toString() else "") }
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("تحديد الاستهلاك اليومي") },
            text = {
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("ميغابايت") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val limit = limitInput.toIntOrNull() ?: 0
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        SettingsManager.setDailyLimit(context, limit)
                    }
                    showLimitDialog = false
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showColorDialog) {
        val colors = listOf("Primary" to MaterialTheme.colorScheme.primary, "Red" to Color(0xFFE53935), "Green" to Color(0xFF43A047), "Blue" to Color(0xFF1E88E5), "Orange" to Color(0xFFFB8C00))

        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("اختر لون العداد") },
            text = {
                LazyColumn {
                    items(colors.size) { index ->
                        val (name, color) = colors[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        SettingsManager.setGaugeColor(context, name)
                                    }
                                    showColorDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(color))
                            Spacer(modifier = Modifier.width(16.dp))
                            val colorDisplayName = when(name) {
                                "Primary" -> "اللون الأساسي"
                                "Red" -> "أحمر"
                                "Green" -> "أخضر"
                                "Blue" -> "أزرق"
                                "Orange" -> "برتقالي"
                                else -> name
                            }
                            Text(colorDisplayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun UsageCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
                    .padding(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun Speedometer(downloadSpeed: Long, uploadSpeed: Long, gaugeColorName: String) {
    val maxSpeed = 10L * 1024 * 1024 // 10 MB/s for max gauge reading (arbitrary)
    val progress = (downloadSpeed.toFloat() / maxSpeed).coerceIn(0f, 1f)

    val primaryColor = when(gaugeColorName) {
        "Red" -> Color(0xFFE53935)
        "Green" -> Color(0xFF43A047)
        "Blue" -> Color(0xFF1E88E5)
        "Orange" -> Color(0xFFFB8C00)
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 135f
            val sweepAngle = 270f

            // Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )

            // Progress
            drawArc(
                color = primaryColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * progress,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = formatSpeed(downloadSpeed),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatSpeed(uploadSpeed),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun formatSpeed(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B/s"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB/s"
        else -> String.format("%.1f MB/s", bytes / (1024f * 1024f))
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        else -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
    }
}
