package io.legado.app.ui.book.readRecord

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.ui.book.readRecord.component.ReadingTimeBarChartCard
import io.legado.app.ui.book.readRecord.component.StatItem
import io.legado.app.ui.book.readRecord.component.StatsGridCard
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.cover.CoilBookCover
import io.legado.app.ui.widget.components.heatmap.HeatmapMode
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadRecordOverviewScreen(
    viewModel: ReadRecordOverviewViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onBookClick: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = "阅读总览",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PeriodSelector(
                selectedPeriod = state.period,
                onPeriodSelected = { viewModel.setPeriod(it) }
            )

            DateNavigator(
                period = state.period,
                referenceDate = state.referenceDate,
                onPrevClick = { viewModel.prevDate() },
                onNextClick = { viewModel.nextDate() }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    HeatmapCard(state)
                }

                item {
                    val stats = listOf(
                        StatItem("阅读时间", ReadRecordFormatter.formatDuration(state.totalTime)),
                        StatItem("阅读天数", "${state.readingDays}天"),
                        StatItem("累计读过", "${state.totalBooks}本"),
                        StatItem("读完书籍", "${state.finishedBooks}本"),
                        StatItem("在读书籍", "${state.readingBooks}本"),
                        StatItem("阅读字数", ReadRecordFormatter.formatWords(state.totalWords))
                    )
                    StatsGridCard(title = "阅读数据", items = stats)
                }

                if (state.period != ReadPeriod.ALL && state.dailyTimeData.isNotEmpty()) {
                    item {
                        ReadingTimeBarChartCard(data = state.dailyTimeData, period = state.period)
                    }
                }

                if (state.topBooks.isNotEmpty()) {
                    item {
                        TopReadingListCard(state.topBooks, viewModel, onBookClick)
                    }
                }

                item {
                    ReadingCalendarCard(state, viewModel)
                }
            }
        }
    }
}

@Composable
fun PeriodSelector(
    selectedPeriod: ReadPeriod,
    onPeriodSelected: (ReadPeriod) -> Unit
) {
    val periods = listOf(
        ReadPeriod.DAY to "日",
        ReadPeriod.WEEK to "周",
        ReadPeriod.MONTH to "月",
        ReadPeriod.YEAR to "年",
        ReadPeriod.ALL to "总"
    )

    PrimaryTabRow(
        selectedTabIndex = periods.indexOfFirst { it.first == selectedPeriod },
        containerColor = Color.Transparent,
        divider = {}
    ) {
        periods.forEach { (period, label) ->
            Tab(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                text = { AppText(label) }
            )
        }
    }
}

@Composable
fun DateNavigator(
    period: ReadPeriod,
    referenceDate: LocalDate,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit
) {
    if (period == ReadPeriod.ALL) return

    val dateText = when (period) {
        ReadPeriod.DAY -> referenceDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        ReadPeriod.WEEK -> {
            val start = referenceDate.with(java.time.DayOfWeek.MONDAY)
            val end = referenceDate.with(java.time.DayOfWeek.SUNDAY)
            "${start.format(DateTimeFormatter.ofPattern("M.d"))} - ${end.format(DateTimeFormatter.ofPattern("M.d"))}"
        }
        ReadPeriod.MONTH -> referenceDate.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        ReadPeriod.YEAR -> referenceDate.format(DateTimeFormatter.ofPattern("yyyy年"))
        ReadPeriod.ALL -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediumIconButton(
            onClick = onPrevClick,
            imageVector = Icons.AutoMirrored.Filled.ArrowLeft
        )
        AppText(
            text = dateText,
            style = LegadoTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        MediumIconButton(
            onClick = onNextClick,
            imageVector = Icons.AutoMirrored.Filled.ArrowRight
        )
    }
}

@Composable
fun HeatmapCard(state: ReadRecordOverviewUiState) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText("阅读热力图", style = LegadoTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            HeatmapCalendarSection(
                dailyReadCounts = state.allReadCounts,
                dailyReadTimes = state.allReadTimes,
                currentMode = HeatmapMode.TIME,
                selectedDate = null,
                onDateSelected = {}
            )
        }
    }
}

@Composable
fun TopReadingListCard(
    topBooks: List<ReadBookRanking>,
    viewModel: ReadRecordOverviewViewModel,
    onBookClick: (String, String) -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            AppText(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = "阅读时长榜",
                style = LegadoTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            topBooks.forEachIndexed { index, book ->
                var coverPath by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(book.bookName, book.bookAuthor) {
                    coverPath = viewModel.getBookCover(book.bookName, book.bookAuthor)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookClick(book.bookName, book.bookAuthor) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "${index + 1}",
                        style = LegadoTheme.typography.titleMedium,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center,
                        color = if (index < 3) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurfaceVariant
                    )
                    CoilBookCover(
                        name = book.bookName,
                        author = book.bookAuthor,
                        path = coverPath,
                        modifier = Modifier.width(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = book.bookName,
                            style = LegadoTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        AppText(
                            text = book.bookAuthor,
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppText(
                        modifier = Modifier.padding(end = 8.dp),
                        text = ReadRecordFormatter.formatDuration(book.readTime),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingCalendarCard(
    state: ReadRecordOverviewUiState,
    viewModel: ReadRecordOverviewViewModel
) {
    val currentMonth = YearMonth.from(state.referenceDate)
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // 0 for Sunday

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText("读书日历", style = LegadoTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                val days = listOf("日", "一", "二", "三", "四", "五", "六")
                days.forEach { day ->
                    AppText(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = LegadoTheme.typography.labelSmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            val totalCells = ((daysInMonth + firstDayOfWeek + 6) / 7) * 7
            for (i in 0 until totalCells step 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (j in 0 until 7) {
                        val cellIndex = i + j
                        val dayOfMonth = cellIndex - firstDayOfWeek + 1
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.75f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayOfMonth in 1..daysInMonth) {
                                val date = currentMonth.atDay(dayOfMonth)
                                CalendarDayCell(date, state, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    date: LocalDate,
    state: ReadRecordOverviewUiState,
    viewModel: ReadRecordOverviewViewModel
) {
    val topBook = state.dailyTopBook[date]
    var coverPath by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(topBook) {
        topBook?.let { (name, author) ->
            coverPath = viewModel.getBookCover(name, author)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(LegadoTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (topBook != null) {
            CoilBookCover(
                name = topBook.first,
                author = topBook.second,
                path = coverPath,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.4f)
            )
        }
        
        AppText(
            text = date.dayOfMonth.toString(),
            style = LegadoTheme.typography.bodySmall,
            fontWeight = if (topBook != null) FontWeight.Bold else FontWeight.Normal,
            color = if (topBook != null) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurface
        )
    }
}
