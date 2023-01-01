package net.wiedekopf.coords

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.what3words.androidwrapper.What3WordsV3
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import org.threeten.bp.LocalDateTime
import uk.me.jstott.jcoord.LatLng
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.roundToInt

private const val TAG = "CoordsViewModel"


@HiltViewModel
class CoordsViewModel @Inject constructor() : ViewModel() {

    private val _latLongWgs84 = MutableStateFlow<LatLongDecimalWgs84Point?>(null)
    private val _updatedAt = MutableStateFlow<LocalDateTime?>(null)
    var latLongWgs84: StateFlow<LatLongDecimalWgs84Point?> = _latLongWgs84
    var updatedAt: StateFlow<LocalDateTime?> = _updatedAt

    private val _currentProjection = MutableStateFlow(SupportedProjection.all.first())
    val currentProjection: StateFlow<SupportedProjection> = _currentProjection

    suspend fun formatData(context: Context): List<LabelledDatum> = _latLongWgs84.value?.let {
        val specific = _currentProjection.value.formatter.invoke(it, context)
        val common = commonElementsForLocation(it, context)
        specific.plus(common)
    } ?: emptyList()

    private fun commonElementsForLocation(
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

    suspend fun updateLocation(location: Location) {
        when {
            _latLongWgs84.value == null -> _latLongWgs84.emit(
                LatLongDecimalWgs84Point.fromLocation(location)
            )
            !_latLongWgs84.value!!.equalLocation(location) -> LatLongDecimalWgs84Point.fromLocation(
                location
            )

        }
        this._latLongWgs84.compareAndSet(
            this._latLongWgs84.value, LatLongDecimalWgs84Point(
                latitude = location.latitude.toBigDecimal(),
                longitude = location.longitude.toBigDecimal(),
                rawLocation = location
            )
        )
        this._updatedAt.emit(LocalDateTime.now())
    }

    private val wgsToUtm = getConverter()

    private fun getConverter(): CoordinateTransform {
        val crsFactory = CRSFactory()
        val wgs84 = crsFactory.createFromName("epsg:4326")
        val utm = crsFactory.createFromName("epsg:25833")
        return CoordinateTransformFactory().createTransform(wgs84, utm)
    }

    @Suppress("unused")
    private fun toUtm(location: Location): ProjCoordinate {
        val wgs84Coordinate = ProjCoordinate(location.latitude, location.longitude)
        return wgsToUtm.transform(wgs84Coordinate)
    }

    @Suppress("unused")
    private fun CoordinateTransform.transform(source: ProjCoordinate): ProjCoordinate {
        val target = ProjCoordinate()
        wgsToUtm.transform(source, target)
        return target
    }

    suspend fun setProjection(supportedProjection: SupportedProjection) {
        _currentProjection.emit(supportedProjection)
    }
}

data class LatLongDecimalWgs84Point(
    val latitude: BigDecimal, val longitude: BigDecimal, val rawLocation: Location
) {

    fun equalLocation(location: Location): Boolean {
        val lat = latitude.toString().take(8).compareTo(location.latitude.toString().take(8))
        val long = longitude.toString().take(8).compareTo(location.longitude.toString().take(8))
        return lat == 0 && long == 0
    }

    companion object {
        fun fromLocation(location: Location) = LatLongDecimalWgs84Point(
            latitude = location.latitude.toBigDecimal(),
            longitude = location.longitude.toBigDecimal(),
            rawLocation = location
        )
    }
}

data class LabelledDatum(
    @StringRes val label: Int, val datum: String, val priority: Int = 1
)

private fun getDesignatorFromBigDecimal(
    bd: BigDecimal, context: Context, @StringRes lessThanZero: Int, @StringRes elseCase: Int
) = context.getString(
    when (bd.compareTo(BigDecimal.ZERO)) {
        -1 -> lessThanZero
        else -> elseCase
    }
)

sealed class SupportedProjection(
    @StringRes val shortNameRes: Int,
    @StringRes val longNameRes: Int,
    @StringRes val explanationRes: Int? = null,
    val formatter: suspend (LatLongDecimalWgs84Point, Context) -> List<LabelledDatum>,
    val usesInternet: Boolean = false
) {
    companion object {
        val all = listOf(UTM, WGS84Decimal, WGS84DMS, OpenLocationCode, What3Words)
        val locationCache = mutableMapOf<String, String>()
    }

    object WGS84Decimal : SupportedProjection(shortNameRes = R.string.wgs84_dec_short,
        longNameRes = R.string.wgs84_dec_long,
        formatter = { latlong, context ->
            val lat = "${
                latlong.latitude.abs().formatWithDecimals(6)
            } ${
                getDesignatorFromBigDecimal(
                    bd = latlong.latitude,
                    context = context,
                    lessThanZero = R.string.south_symbol,
                    elseCase = R.string.north_symbol
                )
            }"
            val long = "${
                latlong.longitude.abs().formatWithDecimals(6)
            } ${
                getDesignatorFromBigDecimal(
                    bd = latlong.longitude,
                    context = context,
                    lessThanZero = R.string.west_symbol,
                    elseCase = R.string.east_symbol
                )
            }"

            listOf(
                LabelledDatum(R.string.latitude, lat), LabelledDatum(R.string.longitude, long)
            )
        })

    object WGS84DMS : SupportedProjection(shortNameRes = R.string.wgs84_dms_short,
        longNameRes = R.string.wgs84_dms_long,
        formatter = { latlong, context ->
            val lat = "${latlong.latitude.toDMS()} ${
                getDesignatorFromBigDecimal(
                    latlong.latitude, context, R.string.south_symbol, R.string.north_symbol
                )
            }"
            val long = "${latlong.longitude.toDMS()} ${
                getDesignatorFromBigDecimal(
                    latlong.longitude, context, R.string.west_symbol, R.string.east_symbol
                )
            }"
            listOf(
                LabelledDatum(R.string.latitude, lat), LabelledDatum(
                    R.string.longitude, long
                )
            )
        })

    object UTM : SupportedProjection(shortNameRes = R.string.utm_short,
        longNameRes = R.string.utm_long,
        formatter = { latLong, _ ->
            val latLongRef = LatLng(latLong.latitude.toDouble(), latLong.longitude.toDouble())
            val utmRef = latLongRef.toUTMRef()
            val northing = utmRef.northing.roundToInt().toString()
            val easting = utmRef.easting.roundToInt().toString()
            listOf(
                LabelledDatum(R.string.utm_zone, "${utmRef.lngZone}${utmRef.latZone}"),
                LabelledDatum(R.string.northing, northing), LabelledDatum(R.string.easting, easting)
            )
        })

    object OpenLocationCode : SupportedProjection(shortNameRes = R.string.olc_code,
        longNameRes = R.string.olc_code,
        explanationRes = R.string.olc_code_explanation,
        formatter = { latlong, _ ->
            val digits =
                when (latlong.rawLocation.hasAccuracy() && latlong.rawLocation.accuracy <= 5f) {
                    true -> 11
                    else -> 10
                }
            val olc = com.google.openlocationcode.OpenLocationCode.encode(
                /* latitude = */ latlong.latitude.toDouble(),
                /* longitude = */ latlong.longitude.toDouble(),
                /* codeLength = */ digits
            )
            listOf(LabelledDatum(R.string.olc_code, olc))
        })

    object What3Words : SupportedProjection(shortNameRes = R.string.w3w_short,
        longNameRes = R.string.w3w_long,
        explanationRes = R.string.w3w_explanation,
        usesInternet = true,
        formatter = { latlong, context ->
            Log.i(TAG, "triggering W3W request")
            val cacheKey = "${latlong.latitude}|${latlong.longitude}|W3W"
            val words = when (locationCache.containsKey(cacheKey)) {
                true -> {
                    Log.d(TAG, "Cache hit for: $cacheKey")
                    locationCache[cacheKey]!!
                }
                else -> {
                    Log.d(TAG, "Cache MISS for: $cacheKey")
                    val w3wApi = What3WordsV3(BuildConfig.W3W_API_KEY, context)
                    val w3w = w3wApi.convertTo3wa(
                        com.what3words.javawrapper.request.Coordinates(
                            latlong.latitude.toDouble(), latlong.longitude.toDouble()
                        )
                    ).execute()
                    when (w3w.isSuccessful) {
                        true -> w3w.words.replace(".", "\n").also {
                            locationCache[cacheKey] = it
                        }
                        else -> context.getString(R.string.w3w_error)
                    }
                }
            }
            listOf(LabelledDatum(R.string.w3w_address, words))
        }) {
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

fun BigDecimal.formatWithDecimals(decimals: Int): String {
    val (wholePart, fractionalPart) = this.toString().split(".")
    return "$wholePart.${fractionalPart.take(decimals)}"
}