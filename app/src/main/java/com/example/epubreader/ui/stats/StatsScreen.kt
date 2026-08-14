package com.example.epubreader.ui.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.model.BookEntity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    navController: NavController,
    settingsViewModel: com.example.epubreader.ui.settings.SettingsViewModel,
    globalBackdrop: com.kyant.backdrop.Backdrop
) {
    val context = LocalContext.current
    val dao = AppDatabase.getDatabase(context).bookDao()
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModelFactory(dao))
    val appTheme by settingsViewModel.appTheme.collectAsState()
    val isDark = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
    val primaryTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF2C3E50)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF7F8C8D)
    
    val state by viewModel.stats.collectAsState()
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Foreground content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp) // Leave space for bottom nav
        ) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp))
            
            Text(
                text = "Reading Stats",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryTextColor,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Text(
                text = "Your library at a glance",
                fontSize = 16.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Dashboard Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Books",
                    value = state.totalBooks,
                    icon = Icons.Filled.MenuBook,
                    backdrop = globalBackdrop,
                    color = Color(0xFF3498DB),
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Series",
                    value = state.totalSeries,
                    icon = Icons.Filled.LibraryBooks,
                    backdrop = globalBackdrop,
                    color = Color(0xFF9B59B6),
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Finished",
                    value = state.finishedBooks,
                    icon = Icons.Filled.CheckCircle,
                    backdrop = globalBackdrop,
                    color = Color(0xFF2ECC71),
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Reading",
                    value = state.readingBooks,
                    icon = Icons.Filled.AutoGraph,
                    backdrop = globalBackdrop,
                    color = Color(0xFFE67E22),
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Progress Ring Card
            ProgressCard(
                total = state.totalBooks,
                finished = state.finishedBooks,
                backdrop = globalBackdrop,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                isDark = isDark
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Recent Books
            if (state.recentlyRead != null || state.recentlyAdded != null) {
                Text(
                    text = "Recent Activity",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    state.recentlyRead?.let { book ->
                        item {
                            RecentBookCard(
                                title = "Last Read",
                                book = book,
                                backdrop = globalBackdrop,
                                onClick = { navController.navigate("reader/${book.id}") }
                            )
                        }
                    }
                    state.recentlyAdded?.let { book ->
                        if (book.id != state.recentlyRead?.id) {
                            item {
                                RecentBookCard(
                                    title = "New Arrival",
                                    book = book,
                                    backdrop = globalBackdrop,
                                    onClick = { navController.navigate("reader/${book.id}") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    icon: ImageVector,
    backdrop: com.kyant.backdrop.Backdrop,
    color: Color,
    primaryTextColor: Color = Color(0xFF2C3E50),
    secondaryTextColor: Color = Color(0xFF7F8C8D),
    isDark: Boolean = false
) {
    // Number animation
    var animationTarget by remember { mutableStateOf(0) }
    LaunchedEffect(value) {
        delay(100)
        animationTarget = value
    }
    
    val animatedValue by animateIntAsState(
        targetValue = animationTarget,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow)
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape)
                    .align(Alignment.End),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            
            Column {
                Text(
                    text = animatedValue.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = primaryTextColor
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
fun ProgressCard(
    total: Int,
    finished: Int,
    backdrop: com.kyant.backdrop.Backdrop,
    primaryTextColor: Color = Color(0xFF2C3E50),
    secondaryTextColor: Color = Color(0xFF7F8C8D),
    isDark: Boolean = false
) {
    val progress = if (total > 0) finished.toFloat() / total else 0f
    
    var animationTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(progress) {
        delay(300)
        animationTarget = progress
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(1500, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(140.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = {
                    blur(24f.dp.toPx())
                    vibrancy()
                    lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
                },
                shadow = { Shadow(color = Color.Black.copy(alpha = 0.15f), radius = 24.dp) },
                onDrawSurface = { drawRect(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.25f)) }
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background track
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    drawArc(
                        color = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE0E0E0),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                
                // Progress track
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0xFF3498DB), Color(0xFF2ECC71), Color(0xFF3498DB))
                        ),
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                
                Text(
                    text = "${(animatedProgress * 100).roundToInt()}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            }
            
            Spacer(modifier = Modifier.width(24.dp))
            
            Column {
                Text(
                    text = "Completion Rate",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$finished out of $total books finished.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
fun RecentBookCard(
    title: String,
    book: BookEntity,
    backdrop: com.kyant.backdrop.Backdrop,
    primaryTextColor: Color = Color(0xFF2C3E50),
    secondaryTextColor: Color = Color(0xFF7F8C8D),
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(260.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = {
                    blur(24f.dp.toPx())
                    vibrancy()
                    lens(8f.dp.toPx(), 16f.dp.toPx(), chromaticAberration = true)
                },
                shadow = { Shadow(color = Color.Black.copy(alpha = 0.2f), radius = 16.dp) },
                onDrawSurface = { drawRect(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.25f)) }
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF3498DB),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            val coverPath = book.coverImage
            if (coverPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = book.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateStr = if (title == "New Arrival") dateFormat.format(Date(book.addedTime)) else dateFormat.format(Date(book.lastReadTime))
            Text(
                text = dateStr,
                fontSize = 12.sp,
                color = secondaryTextColor,
                maxLines = 1
            )
        }
    }
}
