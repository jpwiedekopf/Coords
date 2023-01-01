package net.wiedekopf.coords

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*
import com.jakewharton.threetenabp.AndroidThreeTen
import net.wiedekopf.coords.ui.theme.CoordsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
        setContent {
            CoordsTheme {
                CoordsApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CoordsApp() {
    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION
        )
    )
    when (locationPermissionState.allPermissionsGranted) {
        true -> CoordsAppContent()
        else -> MissingPermissionsContent(locationPermissionState)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MissingPermissionsContent(locationPermissionState: MultiplePermissionsState) {
    Column(
        Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fineLocationUpgradeRequest = rememberPermissionState(permission = ACCESS_FINE_LOCATION)
        val coarseStatus =
            locationPermissionState.permissions.find { it.permission == ACCESS_COARSE_LOCATION }!!.status.isGranted
        val fineStatus = fineLocationUpgradeRequest.status.isGranted
        val request: Pair<String, () -> Unit> = when {
            !coarseStatus && !fineStatus -> stringResource(id = R.string.rationale_no_permissions) to { locationPermissionState.launchMultiplePermissionRequest() }
            coarseStatus && !fineStatus -> stringResource(id = R.string.rationale_upgrade_fine) to { fineLocationUpgradeRequest.launchPermissionRequest() }
            else -> stringResource(id = R.string.rationale_no_permissions) to { locationPermissionState.launchMultiplePermissionRequest() }
        }
        val (rationale, grantRequest) = request
        Text(
            rationale,
            modifier = Modifier.fillMaxWidth(0.8f),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Justify,
            color = colorScheme.onBackground
        )
        Button(onClick = grantRequest) {
            Text(stringResource(id = R.string.grant_permission), fontWeight = FontWeight.Bold)
        }
    }
}

