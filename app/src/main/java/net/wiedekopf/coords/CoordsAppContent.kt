package net.wiedekopf.coords

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

private const val TAG = "CoordsApp"

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
    val locationListener =
        LocationListener { location ->
            myLocation = location
            Log.i(
                TAG, "got location: LAT ${location.latitude} LONG ${location.longitude}" +
                        " at ${LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)}"
            )
        }
    val locationManager =
        ContextCompat.getSystemService(context, LocationManager::class.java) as LocationManager

    val projections = listOf(SupportedProjection.WGS84_DEC, SupportedProjection.WGS84_DMS)

    val selectedPage = remember {
        mutableStateOf(0)
    }

    val currentProjection by remember {
        derivedStateOf {
            projections[selectedPage.value]
        }
    }

    val scaffoldState = rememberScaffoldState()

    SideEffect {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000L,
            0F,
            locationListener
        )
    }

    Scaffold(
        scaffoldState = scaffoldState,
        bottomBar = { AppBottomBar(projections, selectedPage) }
    ) { scaffoldPadding ->
        Column(modifier = Modifier.padding(scaffoldPadding)) {
            DisplayCoordinates(viewModel, currentProjection)
        }
    }
}

@Composable
fun AppBottomBar(projections: List<SupportedProjection>, selectedPage: MutableState<Int>) {
    TabRow(selectedTabIndex = selectedPage.value) {
        projections.forEachIndexed { page, proj ->
            Tab(selected = selectedPage.value == page, onClick = { selectedPage.value = page }) {
                Text(stringResource(proj.shortNameRes), color = colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun DisplayCoordinates(viewModel: CoordsViewModel, currentProjection: SupportedProjection) {
    val latLong by viewModel.latLongWgs84.collectAsState()
    val lastUpdate by viewModel.updatedAt.collectAsState()
    when (latLong) {
        null -> WaitingScreen()
        else -> CoordinateViewerScreen(latLong!!, currentProjection, lastUpdate!!)
    }
}

private fun formatDurationFromLastUpdate(context: Context, lastUpdate: LocalDateTime): String {
    val now = LocalDateTime.now()
    val duration = Duration.between(lastUpdate, now).seconds
    return context.getString(R.string.duration_format, duration)
}

@Composable
fun CoordinateViewerScreen(
    latLong: LatLongDecimalWgs84Point,
    currentProjection: SupportedProjection,
    lastUpdate: LocalDateTime
) = Column(Modifier.fillMaxSize()) {
    val context = LocalContext.current
    var updatedAtString by remember {
        mutableStateOf(formatDurationFromLastUpdate(context, lastUpdate))
    }
    LaunchedEffect(lastUpdate) {
        while (true) {
            updatedAtString = formatDurationFromLastUpdate(context, lastUpdate)
            delay(1000L)
        }
    }
    Column(
        Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = currentProjection.longNameRes),
            style = typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        currentProjection.explanationRes?.let { exp ->
            Text(text = stringResource(id = exp), style = typography.labelMedium)
        }
        Text(text = updatedAtString, style = typography.bodyMedium)
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val format = latLong.format(currentProjection, context)
        format.forEach { ld ->
            LabelledDatum(ld)
        }
    }
}

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
                    .aspectRatio(1f)
            )
            Text(
                stringResource(id = R.string.waiting_for_location),
                style = typography.bodyMedium
            )
        }
    }
}

@Composable
fun LabelledDatum(labelledDatum: LabelledDatum) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = stringResource(id = labelledDatum.label), style = typography.displaySmall)
        Text(
            text = labelledDatum.datum,
            style = typography.displayLarge,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}