package net.wiedekopf.coords

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.*
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val TAG = "CoordsApp"
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@OptIn(ExperimentalPagerApi::class)
@SuppressLint("MissingPermission")
@Composable
fun CoordsAppContent() {
    val viewModel = hiltViewModel<CoordsViewModel>()
    val context = LocalContext.current
    var myLocation by remember {
        mutableStateOf<Location?>(null)
    }
    LaunchedEffect(key1 = myLocation) {
        myLocation?.let {
            viewModel.updateLocation(it)
        }
    }
    val locationListener = LocationListener { location ->
        myLocation = location
        Log.i(
            TAG, "got location: LAT ${location.latitude} LONG ${location.longitude}" + " at ${
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            }"
        )
    }
    val locationManager =
        ContextCompat.getSystemService(context, LocationManager::class.java) as LocationManager

    val projections = SupportedProjection.values().toList()

    val pagerState = rememberPagerState(0)

    val currentProjection by remember {
        derivedStateOf {
            projections[pagerState.currentPage]
        }
    }

    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()

    SideEffect {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 5000L, 1F, locationListener
        )
    }

    Scaffold(scaffoldState = scaffoldState,
        backgroundColor = colorScheme.background,
        contentColor = colorScheme.onBackground,
        bottomBar = {
            if (myLocation != null) AppBottomBar(projections, pagerState.currentPage) {
                coroutineScope.launch {
                    pagerState.scrollToPage(it)
                }
            }
        }) { scaffoldPadding ->
        Column(modifier = Modifier.padding(scaffoldPadding)) {
            DisplayCoordinates(viewModel, currentProjection, pagerState, projections.size)
        }
    }
}

@ExperimentalPagerApi
@Composable
fun AppBottomBar(
    projections: List<SupportedProjection>, currentPage: Int, onChangePage: (Int) -> Unit
) {
    BottomAppBar(
        backgroundColor = colorScheme.primaryContainer,
        contentColor = colorScheme.onPrimaryContainer
    ) {
        ScrollableTabRow(
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp,
            selectedTabIndex = currentPage,
            backgroundColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
        ) {
            projections.forEachIndexed { page, proj ->
                Tab(
                    selected = currentPage == page, onClick = { onChangePage(page) },
                    text = {
                        Text(
                            stringResource(proj.shortNameRes),
                            color = colorScheme.onPrimaryContainer,
                            style = typography.bodyLarge
                        )
                    })
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun DisplayCoordinates(
    viewModel: CoordsViewModel,
    currentProjection: SupportedProjection,
    pagerState: PagerState,
    numberOfPages: Int
) {
    val latLong by viewModel.latLongWgs84.collectAsState()
    val lastUpdate by viewModel.updatedAt.collectAsState()
    when (latLong) {
        null -> WaitingScreen()
        else -> CoordinateViewerScreen(
            latLong!!, currentProjection, lastUpdate!!, pagerState, numberOfPages
        )
    }
}

private fun formatLastUpdate(context: Context, lastUpdate: LocalDateTime): String =
    context.getString(R.string.last_update_format, dateTimeFormatter.format(lastUpdate))

@OptIn(ExperimentalPagerApi::class)
@Composable
fun CoordinateViewerScreen(
    latLong: LatLongDecimalWgs84Point,
    currentProjection: SupportedProjection,
    lastUpdate: LocalDateTime,
    pagerState: PagerState,
    numberOfPages: Int
) = HorizontalPager(count = numberOfPages, state = pagerState) {
    Column(Modifier.fillMaxSize()) {
        val context = LocalContext.current
        var updatedAtString by remember {
            mutableStateOf(formatLastUpdate(context, lastUpdate))
        }
        LaunchedEffect(lastUpdate) {
            while (true) {
                updatedAtString = formatLastUpdate(context, lastUpdate)
                delay(1000L)
            }
        }
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            Column(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = currentProjection.longNameRes),
                    style = typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                currentProjection.explanationRes?.let { exp ->
                    Text(
                        modifier = Modifier.fillMaxWidth(0.75f),
                        text = stringResource(id = exp),
                        style = typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = updatedAtString,
                    style = typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val formatElements = remember { mutableStateListOf<LabelledDatum>() }

                LaunchedEffect(latLong, pagerState.currentPage) {
                    formatElements.clear()
                    CoroutineScope(Dispatchers.IO).launch {
                        formatElements.addAll(latLong.format(currentProjection, context))
                    }
                    formatElements.addAll(commonElementsForLocation(latLong, context))
                }
                formatElements.forEach {
                    LabelledDatumView(labelledDatum = it)
                }
            }
        }
    }
}

fun commonElementsForLocation(
    latLong: LatLongDecimalWgs84Point, context: Context
): List<LabelledDatum> = listOfNotNull(
    when (latLong.rawLocation.hasAltitude()) {
        true -> LabelledDatum(
            label = R.string.altitude,
            datum = context.getString(R.string.altitude_format, latLong.rawLocation.altitude),
            priority = 2
        )
        else -> null
    },
    when (latLong.rawLocation.hasAccuracy()) {
        true -> LabelledDatum(
            label = R.string.accuracy,
            datum = context.getString(R.string.accuracy_format, latLong.rawLocation.accuracy),
            priority = 2
        )
        else -> null
    },
    when (latLong.rawLocation.hasBearing()) {
        true -> LabelledDatum(
            label = R.string.bearing, datum = context.getString(
                R.string.bearing_format, latLong.rawLocation.bearing.roundToInt()
            ), priority = 2
        )
        else -> null
    },
)

@Composable
fun WaitingScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                color = colorScheme.secondary
            )
            Text(
                modifier = Modifier.fillMaxWidth(0.5f),
                text = stringResource(id = R.string.waiting_for_location),
                style = typography.headlineSmall,
                color = colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LabelledDatumView(labelledDatum: LabelledDatum) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val contentColor = when (labelledDatum.priority) {
            1 -> colorScheme.onBackground
            else -> colorScheme.onBackground.copy(alpha = 0.8f)
        }
        Text(
            text = stringResource(id = labelledDatum.label), style = when (labelledDatum.priority) {
                1 -> typography.displaySmall.copy(fontSize = 30.sp)
                else -> typography.displaySmall.copy(fontSize = 28.sp)
            }, color = contentColor
        )
        Text(
            text = labelledDatum.datum, style = when (labelledDatum.priority) {
                1 -> typography.displayMedium
                else -> typography.displaySmall
            }, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, color = contentColor
        )
    }
}