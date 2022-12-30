package net.wiedekopf.coords

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import org.threeten.bp.LocalDateTime
import javax.inject.Inject


@HiltViewModel
class CoordsViewModel @Inject constructor() : ViewModel() {

    private val _currentLocation = MutableStateFlow<Location?>(null)
    private val _updatedAt = MutableStateFlow<LocalDateTime?>(null)
    private val _utmLocation = MutableStateFlow<ProjCoordinate?>(null)
    var currentLocation: StateFlow<Location?> = _currentLocation
    var updatedAt: StateFlow<LocalDateTime?> = _updatedAt
    val utmLocation: StateFlow<ProjCoordinate?> = _utmLocation

    suspend fun updateLocation(location: Location) {
        this._currentLocation.emit(location)
        this._updatedAt.emit(LocalDateTime.now())
        this._utmLocation.emit(toUtm(location))
    }

    private val wgsToUtm = getConverter()

    private fun getConverter(): CoordinateTransform {
        val crsFactory = CRSFactory()
        val wgs84 = crsFactory.createFromName("epsg:4326")
        val utm = crsFactory.createFromName("epsg:25833")
        return CoordinateTransformFactory().createTransform(wgs84, utm)
    }

    private fun toUtm(location: Location): ProjCoordinate {
        val wgs84Coordinate = ProjCoordinate(location.latitude, location.longitude)
        return wgsToUtm.transform(wgs84Coordinate)
    }

    private fun CoordinateTransform.transform(source: ProjCoordinate): ProjCoordinate {
        val target = ProjCoordinate()
        wgsToUtm.transform(source, target)
        return target
    }

}