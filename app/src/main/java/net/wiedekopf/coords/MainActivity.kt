package net.wiedekopf.coords

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import com.jakewharton.threetenabp.AndroidThreeTen
import net.wiedekopf.coords.ui.theme.CoordsTheme
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter.ISO_DATE_TIME
import kotlin.math.absoluteValue

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this);
        requestLocationPermissions(this) {
            setContent {
                CoordsTheme {
                    CoordsApp()
                }
            }
        }

    }

    private fun requestLocationPermissions(
        context: Context,
        onRequestPermissionsResultCallback: (Boolean) -> Unit
    ) = when (PackageManager.PERMISSION_GRANTED) {
        ContextCompat.checkSelfPermission(
            context,
            ACCESS_FINE_LOCATION
        ) -> onRequestPermissionsResultCallback(true)
        else -> {
            val locationPermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                when {
                    permissions.getOrDefault2(ACCESS_FINE_LOCATION, false) ->
                        onRequestPermissionsResultCallback(true)
                    permissions.getOrDefault2(
                        ACCESS_COARSE_LOCATION,
                        false
                    ) -> onRequestPermissionsResultCallback(false)
                    else -> onRequestPermissionsResultCallback(false)
                }
            }
            locationPermissionRequest.launch(
                arrayOf(
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION
                )
            )

        }
    }
}

private fun <K, V> Map<K, V>.getOrDefault2(k: K, default: V): V {
    if (this.containsKey(k)) return this[k]!!
    return default
}

@SuppressLint("MissingPermission")
@Composable
fun CoordsApp() {
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
                        " at ${LocalDateTime.now().format(ISO_DATE_TIME)}"
            )
        }
    val locationManager =
        getSystemService(context, LocationManager::class.java) as LocationManager

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
        CircularProgressIndicator(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
        )
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


fun Double.toDMS(): String = this.absoluteValue.let { d ->
    buildString {
        val whole = d.toInt()
        append("$whole° ")
        val fractional = d - whole
        val minutePart = fractional * 60
        val minutes = minutePart.toInt()
        append("$minutes′ ")
        val seconds = (minutePart - minutes) * 60
        append("${seconds.toString().take(5)}″")
    }
}