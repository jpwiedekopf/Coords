package net.wiedekopf.coords

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.pager.*
import de.schnettler.datastore.manager.DataStoreManager
import kotlinx.coroutines.*
import net.wiedekopf.coords.ui.util.pagerTabIndicatorOffsetM3
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

private const val TAG = "CoordsApp"
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
val Context.dataStore by preferencesDataStore(
    name = "settings"
)
val Context.dataStoreManager get() = DataStoreManager(this.dataStore)


@OptIn(ExperimentalPagerApi::class, ExperimentalMaterial3Api::class)
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
        Log.d(
            TAG, "got location: LAT ${location.latitude} LONG ${location.longitude}" + " at ${
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            }"
        )
    }
    val locationManager =
        ContextCompat.getSystemService(context, LocationManager::class.java) as LocationManager

    val projections = SupportedProjection.all

    val pagerState = rememberPagerState(0)

    val coroutineScope = rememberCoroutineScope()

    SideEffect {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 5000L, 1F, locationListener
        )
    }

    Scaffold(containerColor = colorScheme.background,
        contentColor = colorScheme.onBackground,
        bottomBar = {
            if (myLocation != null) AppBottomBar(projections, pagerState) {
                Log.d(
                    TAG,
                    "changing from page ${pagerState.currentPage} to $it (${projections[it]::class.simpleName})"
                )
                coroutineScope.launch {
                    pagerState.animateScrollToPage(it)
                }
            }
        }) { scaffoldPadding ->
        Column(modifier = Modifier.padding(scaffoldPadding)) {
            DisplayCoordinates(viewModel, pagerState, projections.size)
        }
    }
}

@ExperimentalPagerApi
@Composable
fun AppBottomBar(
    projections: List<SupportedProjection>, pagerState: PagerState, onChangePage: (Int) -> Unit
) {
    val currentPage = pagerState.currentPage
    BottomAppBar(
        containerColor = colorScheme.primaryContainer,
        contentColor = colorScheme.onPrimaryContainer,
        modifier = Modifier.height(IntrinsicSize.Max)
    ) {
        ScrollableTabRow(modifier = Modifier.weight(1f),
            divider = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(color = colorScheme.onPrimaryContainer.copy(0.5f))
                )
            },
            selectedTabIndex = currentPage,
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.pagerTabIndicatorOffsetM3(pagerState, tabPositions)
                )
            }) {
            projections.forEachIndexed { page, proj ->
                Tab(selected = currentPage == page, onClick = { onChangePage(page) }, text = {
                    Text(
                        text = stringResource(id = proj.shortNameRes),
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
    viewModel: CoordsViewModel, pagerState: PagerState, numberOfPages: Int
) {
    val latLong by viewModel.latLongWgs84.collectAsState()
    val lastUpdate by viewModel.updatedAt.collectAsState()
    LocalContext.current

    when (latLong) {
        null -> WaitingScreen()
        else -> CoordinateViewerScreen(
            latLong = latLong!!,
            lastUpdate = lastUpdate!!,
            pagerState = pagerState,
            numberOfPages = numberOfPages
        )
    }
}

private fun formatLastUpdate(context: Context, lastUpdate: LocalDateTime): String =
    context.getString(R.string.last_update_format, dateTimeFormatter.format(lastUpdate))

@OptIn(ExperimentalPagerApi::class)
@Composable
fun CoordinateViewerScreen(
    latLong: LatLongDecimalWgs84Point,
    lastUpdate: LocalDateTime,
    pagerState: PagerState,
    numberOfPages: Int
) = HorizontalPager(count = numberOfPages, state = pagerState) { page ->
    val context = LocalContext.current
    val allPages by remember {
        mutableStateOf(SupportedProjection.all.map {
            CoordsScreen.getScreen(it, context = context)
        })
    }
    Column(Modifier.fillMaxSize()) {
        var updatedAtString by remember {
            mutableStateOf(formatLastUpdate(context, lastUpdate))
        }

        LaunchedEffect(lastUpdate) {
            while (true) {
                updatedAtString = formatLastUpdate(context, lastUpdate)
                delay(1000L)
            }
        }

        val currentScreen by remember {
            derivedStateOf {
                allPages[page]
            }
        }

        val currentProjection by remember {
            derivedStateOf {
                currentScreen.projection
            }
        }

        val hasInternetPermission by currentScreen.checkInternetPermission()
            .collectAsState(initial = null)

        val coroutineScope = rememberCoroutineScope()

        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            Column(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = currentProjection.longNameRes),
                    style = typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                if (currentProjection.usesInternet && hasInternetPermission == true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            modifier = Modifier.clickable {
                                coroutineScope.launch {
                                    context.dataStoreManager.editPreference(
                                        currentScreen.internetPrefKey, false
                                    )
                                }
                            },
                            text = stringResource(id = R.string.disable_projection),
                            textDecoration = TextDecoration.Underline,
                            color = colorScheme.primary
                        )
                        currentProjection.privacyLinkRes?.let { tcRes ->
                            PrivacyLinkButton(tcRes)
                        }
                    }


                }
                currentProjection.explanationRes?.let { exp ->
                    Text(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        text = stringResource(id = exp),
                        style = typography.labelMedium,
                        textAlign = TextAlign.Justify,
                        color = colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
                if (hasInternetPermission == true) {
                    Text(
                        text = updatedAtString,
                        style = typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            when (hasInternetPermission) {
                null -> {}
                false -> RequestInternetPermissionScreen(currentProjection) {
                    coroutineScope.launch {
                        context.dataStoreManager.editPreference(
                            currentScreen.internetPrefKey, true
                        )
                        Log.i(
                            TAG,
                            "Stored preference true for accessing internet, ${currentScreen.internetPrefKey.name}"
                        )
                    }
                }
                true -> CoordinateView(latLong, currentScreen)
            }

        }
    }
}

@Composable
fun PrivacyLinkButton(@StringRes linkRes: Int) {
    val context = LocalContext.current
    Text(
        modifier = Modifier.clickable {
            val linkSource = context.getString(linkRes)
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(linkSource))
            ContextCompat.startActivity(context, browserIntent, null)
        },
        text = stringResource(R.string.privacy),
        textDecoration = TextDecoration.Underline,
        color = colorScheme.primary
    )
}

@Composable
fun RequestInternetPermissionScreen(
    currentProjection: SupportedProjection, grantPermission: () -> Unit
) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val projectionName = stringResource(id = currentProjection.longNameRes)
        Text(
            stringResource(
                id = R.string.coordinate_system_request_internet_text_format, projectionName
            ),
            style = typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(0.8f),
            textAlign = TextAlign.Justify
        )
        currentProjection.privacyLinkRes?.let {
            PrivacyLinkButton(linkRes = it)
        }
        Button(onClick = grantPermission) {
            Text(text = stringResource(id = R.string.grant_permission))
        }
    }
}

@Composable
fun CoordinateView(
    latLong: LatLongDecimalWgs84Point, screen: CoordsScreen
) {
    val formatElements by screen.getAll(latLong)
        .collectAsState(initial = emptyList(), context = Dispatchers.IO)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .scrollable(state = rememberScrollState(), orientation = Orientation.Vertical),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (formatElements?.size) {
            null -> {}
            0 -> item {
                WaitingScreen(0.75f)
            }
            else -> items(formatElements!!) {
                LabelledDatumView(labelledDatum = it)
            }
        }
    }
}

@Composable
fun WaitingScreen(weight: Float = 1f, showLabel: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxSize(weight)
            .padding(8.dp),
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
            if (showLabel) {
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
}

@Composable
fun LabelledDatumView(labelledDatum: LabelledDatum) {
    Column(
        modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val contentColor = when (labelledDatum.priority) {
            in 0..49 -> colorScheme.onBackground
            else -> colorScheme.onBackground.copy(alpha = 0.8f)
        }
        Text(
            text = buildAnnotatedString {
                val titleStyle = typography.displaySmall.copy(
                    color = contentColor, fontSize = when (labelledDatum.priority) {
                        in 0..19 -> 30.sp
                        else -> 28.sp
                    }
                )
                withStyle(titleStyle.toParagraphStyle()) {
                    withStyle(titleStyle.toSpanStyle()) {
                        append(stringResource(id = labelledDatum.label))
                    }
                }
                val datumStyle = when (labelledDatum.priority) {
                    in 0..19 -> typography.displayMedium
                    else -> typography.displaySmall
                }
                withStyle(datumStyle.toParagraphStyle()) {
                    withStyle(
                        datumStyle.toSpanStyle().copy(
                            color = contentColor, fontFamily = FontFamily.Monospace
                        )
                    ) {
                        append(labelledDatum.datum)
                    }
                }
            }, textAlign = labelledDatum.textAlign
        )
    }
}