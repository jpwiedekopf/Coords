package net.wiedekopf.coords

import android.content.Context
import android.location.Location
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import org.threeten.bp.LocalDateTime
import java.math.BigDecimal
import javax.inject.Inject


@HiltViewModel
class CoordsViewModel @Inject constructor() : ViewModel() {

    private val _latLongWgs84 = MutableStateFlow<LatLongDecimalWgs84Point?>(null)
    private val _updatedAt = MutableStateFlow<LocalDateTime?>(null)
    var latLongWgs84: StateFlow<LatLongDecimalWgs84Point?> = _latLongWgs84
    var updatedAt: StateFlow<LocalDateTime?> = _updatedAt

    suspend fun updateLocation(location: Location) {
        this._latLongWgs84.emit(
            LatLongDecimalWgs84Point(
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
}

data class LatLongDecimalWgs84Point(
    val latitude: BigDecimal, val longitude: BigDecimal, val rawLocation: Location
) {
    fun format(projection: SupportedProjection, context: Context) =
        projection.formatter(this, context)
}

data class LabelledDatum(
    @StringRes val label: Int, val datum: String, val priority: Int = 1
)

private fun getDesignatorFromBigDecimal(
    bd: BigDecimal,
    context: Context,
    @StringRes lessThanZero: Int,
    @StringRes elseCase: Int
) =
    context.getString(
        when (bd.compareTo(BigDecimal.ZERO)) {
            -1 -> lessThanZero
            else -> elseCase
        }
    )

enum class SupportedProjection(
    @StringRes val shortNameRes: Int,
    @StringRes val longNameRes: Int,
    @StringRes val explanationRes: Int? = null,
    val formatter: (LatLongDecimalWgs84Point, Context) -> List<LabelledDatum>
) {
    WGS84_DEC(
        shortNameRes = R.string.wgs84_dec_short,
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
                LabelledDatum(R.string.latitude, lat),
                LabelledDatum(R.string.longitude, long)
            )
        }),
    WGS84_DMS(
        shortNameRes = R.string.wgs84_dms_short,
        longNameRes = R.string.wgs84_dms_long,
        formatter = { latlong, context ->
            val lat = "${latlong.latitude.toDMS()} ${
                getDesignatorFromBigDecimal(
                    latlong.latitude,
                    context,
                    R.string.south_symbol,
                    R.string.north_symbol
                )
            }"
            val long = "${latlong.longitude.toDMS()} ${
                getDesignatorFromBigDecimal(
                    latlong.longitude,
                    context,
                    R.string.west_symbol,
                    R.string.east_symbol
                )
            }"
            listOf(
                LabelledDatum(R.string.latitude, lat), LabelledDatum(
                    R.string.longitude,
                    long
                )
            )
        }),
    UTM(
        shortNameRes = R.string.utm_short,
        longNameRes = R.string.utm_long,
        formatter = { _, _ ->
            val northing = "NYI"
            val easting = "NYI"
            listOf(
                LabelledDatum(R.string.northing, northing),
                LabelledDatum(R.string.easting, easting)
            )
        }
    )
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