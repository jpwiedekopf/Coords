package net.wiedekopf.coords

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.what3words.androidwrapper.What3WordsV3
import de.schnettler.datastore.manager.PreferenceRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import uk.me.jstott.jcoord.LatLng
import java.math.BigDecimal
import kotlin.math.roundToInt

private const val TAG = "CoordsScreen"

private fun prefKeyForProjection(projection: SupportedProjection) =
    "allow_internet_${projection::class.simpleName}"

private fun getDesignatorFromBigDecimal(
    bd: BigDecimal, context: Context, @StringRes lessThanZero: Int, @StringRes elseCase: Int
) = context.getString(
    when (bd.compareTo(BigDecimal.ZERO)) {
        -1 -> lessThanZero
        else -> elseCase
    }
)

sealed class CoordsScreen(
    val projection: SupportedProjection, protected val context: Context
) {
    abstract suspend fun getData(latLong: LatLongDecimalWgs84Point): List<LabelledDatum>

    val internetPrefKey = booleanPreferencesKey(prefKeyForProjection(projection))


    fun checkInternetPermission(): Flow<Boolean> = when (projection.usesInternet) {
        false -> flowOf(true)
        else -> {
            val preferenceRequest = PreferenceRequest(internetPrefKey, false)
            context.dataStoreManager.getPreferenceFlow(preferenceRequest)
        }
    }

    private suspend fun getAllDataElements(latLong: LatLongDecimalWgs84Point) =
        getData(latLong) + commonElementsForLocation(latLong)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAll(latLong: LatLongDecimalWgs84Point): Flow<List<LabelledDatum>?> {
        return checkInternetPermission().mapLatest { permission ->
            when (permission) {
                true -> this.getAllDataElements(latLong).sortedBy {
                    it.priority
                }
                else -> null
            }
        }
    }

    private fun commonElementsForLocation(
        latLong: LatLongDecimalWgs84Point
    ): List<LabelledDatum> = listOfNotNull(
        when (latLong.rawLocation.hasAccuracy()) {
            true -> LabelledDatum(
                label = R.string.accuracy,
                datum = context.getString(R.string.accuracy_format, latLong.rawLocation.accuracy),
                priority = 50
            )
            else -> null
        },
        when (latLong.rawLocation.hasAltitude()) {
            true -> LabelledDatum(
                label = R.string.altitude,
                datum = context.getString(R.string.altitude_format, latLong.rawLocation.altitude),
                priority = 51
            )
            else -> null
        },
        when (latLong.rawLocation.hasBearing()) {
            true -> LabelledDatum(
                label = R.string.bearing, datum = context.getString(
                    R.string.bearing_format, latLong.rawLocation.bearing.roundToInt()
                ), priority = 52
            )
            else -> null
        },
    )

    companion object {
        fun getScreen(
            supportedProjection: SupportedProjection,
            context: Context
        ): CoordsScreen {
            return when (supportedProjection) {
                SupportedProjection.UTM -> UtmScreen(context)
                SupportedProjection.WGS84Decimal -> Wgs84DecimalScreen(context)
                SupportedProjection.WGS84DMS -> Wgs84DmsScreen(context)
                SupportedProjection.What3Words -> What3WordsScreen(context)
                SupportedProjection.OpenLocationCode -> OpenLocationCodeScreen(context)
            }

        }
    }

    class UtmScreen(context: Context) : CoordsScreen(SupportedProjection.UTM, context) {
        override suspend fun getData(latLong: LatLongDecimalWgs84Point): List<LabelledDatum> {
            val latLongRef = LatLng(latLong.latitude.toDouble(), latLong.longitude.toDouble())
            val utmRef = latLongRef.toUTMRef()
            val northing = utmRef.northing.roundToInt().toString()
            val easting = utmRef.easting.roundToInt().toString()
            return listOf(
                LabelledDatum(R.string.easting, easting, 1),
                LabelledDatum(R.string.northing, northing, 2),
                LabelledDatum(
                    R.string.utm_zone, "${utmRef.lngZone}${utmRef.latZone}", priority = 20
                ),
            )
        }
    }

    class Wgs84DecimalScreen(context: Context) :
        CoordsScreen(SupportedProjection.WGS84Decimal, context) {
        override suspend fun getData(latLong: LatLongDecimalWgs84Point): List<LabelledDatum> {
            val lat = "${
                latLong.latitude.abs().formatWithDecimals(6)
            } ${
                getDesignatorFromBigDecimal(
                    bd = latLong.latitude,
                    context = context,
                    lessThanZero = R.string.south_symbol,
                    elseCase = R.string.north_symbol
                )
            }"
            val long = "${
                latLong.longitude.abs().formatWithDecimals(6)
            } ${
                getDesignatorFromBigDecimal(
                    bd = latLong.longitude,
                    context = context,
                    lessThanZero = R.string.west_symbol,
                    elseCase = R.string.east_symbol
                )
            }"

            return listOf(
                LabelledDatum(R.string.latitude, lat, 1), LabelledDatum(R.string.longitude, long, 2)
            )
        }
    }

    class Wgs84DmsScreen(context: Context) : CoordsScreen(SupportedProjection.WGS84DMS, context) {
        override suspend fun getData(latLong: LatLongDecimalWgs84Point): List<LabelledDatum> {

            val lat = "${latLong.latitude.toDMS()} ${
                getDesignatorFromBigDecimal(
                    latLong.latitude, context, R.string.south_symbol, R.string.north_symbol
                )
            }"
            val long = "${latLong.longitude.toDMS()} ${
                getDesignatorFromBigDecimal(
                    latLong.longitude, context, R.string.west_symbol, R.string.east_symbol
                )
            }"
            return listOf(
                LabelledDatum(R.string.latitude, lat, 1), LabelledDatum(R.string.longitude, long, 2)
            )
        }

    }

    class What3WordsScreen(context: Context) :
        CoordsScreen(SupportedProjection.What3Words, context) {

        companion object {
            private val locationCache = mutableMapOf<String, String>()
        }

        override suspend fun getData(latLong: LatLongDecimalWgs84Point): List<LabelledDatum> {
            Log.i(TAG, "triggering W3W request")
            val cacheKey = "${latLong.latitude}|${latLong.longitude}"
            val words = when (locationCache.containsKey(cacheKey)) {
                true -> {
                    Log.d(TAG, "Cache hit for: $cacheKey")
                    locationCache[cacheKey]!!
                }
                else -> {
                    Log.d(TAG, "Cache MISS for: $cacheKey")
                    val w3wApi = What3WordsV3(BuildConfig.W3W_API_KEY, context)
                    val w3wResult = w3wApi.convertTo3wa(
                        com.what3words.javawrapper.request.Coordinates(
                            latLong.latitude.toDouble(), latLong.longitude.toDouble()
                        )
                    ).execute()
                    when (w3wResult.isSuccessful) {
                        true -> w3wResult.words.replace(".", ".\n").also {
                            locationCache[cacheKey] = it
                        }
                        else -> context.getString(R.string.w3w_error)
                    }
                }
            }
            return listOf(LabelledDatum(R.string.w3w_address, words, textAlign = TextAlign.Left))
        }

    }

    class OpenLocationCodeScreen(context: Context) :
        CoordsScreen(projection = SupportedProjection.OpenLocationCode, context) {
        override suspend fun getData(latLong: LatLongDecimalWgs84Point): List<LabelledDatum> {
            val digits =
                when (latLong.rawLocation.hasAccuracy() && latLong.rawLocation.accuracy <= 5f) {
                    true -> 11
                    else -> 10
                }
            val olc = com.google.openlocationcode.OpenLocationCode.encode(
                /* latitude = */ latLong.latitude.toDouble(),
                /* longitude = */ latLong.longitude.toDouble(),
                /* codeLength = */ digits
            )
            return listOf(LabelledDatum(R.string.olc_code, olc))
        }
    }
}

private fun BigDecimal.toDMS(newline: Boolean = true): String = this.abs().let { d ->
    buildString {
        val whole = d.toInt()
        append("$whole° ")
        val fractional = d.subtract(whole.toBigDecimal())
        val minutePart = fractional.times(BigDecimal(60))
        val minutes = minutePart.toInt()
        append("$minutes′")
        if (newline) append("\n") else append(" ")
        val seconds = (minutePart - minutes.toBigDecimal()).times(BigDecimal(60))
        append("${seconds.toString().take(6)}″")
    }
}

private fun BigDecimal.formatWithDecimals(decimals: Int): String {
    val (wholePart, fractionalPart) = this.toString().split(".")
    return "$wholePart.${fractionalPart.take(decimals)}"
}