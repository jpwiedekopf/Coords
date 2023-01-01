package net.wiedekopf.coords

import android.location.Location
import androidx.annotation.StringRes
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

}

data class LatLongDecimalWgs84Point(
    val latitude: BigDecimal, val longitude: BigDecimal, val rawLocation: Location
) {

    fun equalLocation(location: Location): Boolean {
        val lat = latitude.toString().take(8).compareTo(location.latitude.toString().take(8))
        val long = longitude.toString().take(8).compareTo(location.longitude.toString().take(8))
        return lat == 0 && long == 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LatLongDecimalWgs84Point

        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false

        return true
    }

    override fun hashCode(): Int {
        var result = latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        return result
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
    @StringRes val label: Int,
    val datum: String,
    val priority: Int = 1,
    val textAlign: TextAlign = TextAlign.Center
)

sealed class SupportedProjection(
    @StringRes val shortNameRes: Int,
    @StringRes val longNameRes: Int,
    @StringRes val explanationRes: Int? = null,
    @StringRes val privacyLinkRes: Int? = null,
    val usesInternet: Boolean = false
) {
    companion object {
        val all = listOf(UTM, WGS84Decimal, WGS84DMS, OpenLocationCode, What3Words)
    }

    object WGS84Decimal : SupportedProjection(
        shortNameRes = R.string.wgs84_dec_short, longNameRes = R.string.wgs84_dec_long
    )

    object WGS84DMS : SupportedProjection(
        shortNameRes = R.string.wgs84_dms_short,
        longNameRes = R.string.wgs84_dms_long
    )

    object UTM : SupportedProjection(
        shortNameRes = R.string.utm_short,
        longNameRes = R.string.utm_long,
        explanationRes = R.string.utm_explanation
    )

    object OpenLocationCode : SupportedProjection(
        shortNameRes = R.string.olc_code,
        longNameRes = R.string.olc_code,
        explanationRes = R.string.olc_code_explanation
    )

    object What3Words : SupportedProjection(
        shortNameRes = R.string.w3w_short,
        longNameRes = R.string.w3w_long,
        explanationRes = R.string.w3w_explanation,
        privacyLinkRes = R.string.w3w_privacy_link,
        usesInternet = true
    )
}