package net.wiedekopf.coords

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.math.BigDecimal
import kotlin.math.absoluteValue

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

    SideEffect {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000L,
            0F,
            locationListener
        )
    }

    DisplayCoordinates(viewModel)
}

@Composable
fun DisplayCoordinates(viewModel: CoordsViewModel) {
    val location by viewModel.currentLocation.collectAsState()
    val utm by viewModel.currentLocation.collectAsState()
    when (location) {
        null -> WaitingScreen()
        else -> LatLongDMSScreen(location!!)
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
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun LatLongDecimalScreen(location: Location) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabeledDatum(stringResource(id = R.string.latitude), location.latitude)
        LabeledDatum(label = stringResource(id = R.string.longitude), datum = location.longitude)
    }
}

@Composable
fun LatLongDMSScreen(location: Location) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val (lat, long) = location.toDMS()
        LabeledDatum(stringResource(id = R.string.latitude), lat)
        LabeledDatum(label = stringResource(id = R.string.longitude), datum = long)
    }
}

@Composable
fun LabeledDatum(label: String, datum: Any) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.displaySmall)
        Text(
            text = datum.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

fun Location.toDMS(): Pair<String, String> =
    formatLatitudeDms(latitude) to formatLatitudeDms(longitude)

fun formatLatitudeDms(lat: Double) = when (lat.compareTo(0)) {
    -1 -> "${lat.toDMS()} S"
    else -> "${lat.toDMS()} N"
}

fun formatLongitudeDms(lat: Double) = when (lat.compareTo(0)) {
    -1 -> "${lat.toDMS()} W"
    else -> "${lat.toDMS()} E"
}


fun Double.toDMS(): String = this.absoluteValue.toBigDecimal().let { d ->
    buildString {
        val whole = d.toInt()
        append("$whole° ")
        val fractional = d.subtract(whole.toBigDecimal())
        val minutePart = fractional.times(BigDecimal(60))
        val minutes = minutePart.toInt()
        append("$minutes′ ")
        val seconds = (minutePart - minutes.toBigDecimal()).times(BigDecimal(60))
        append("${seconds.toString().take(5)}″")
    }
}