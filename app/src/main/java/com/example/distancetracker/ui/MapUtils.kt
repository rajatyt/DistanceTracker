package com.example.distancetracker.ui

import android.graphics.Camera
import android.location.Location
import android.text.style.TabStopSpan
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.text.DecimalFormat

object MapUtils {

	fun setCameraPosition(location: LatLng): CameraPosition {
		return CameraPosition.Builder()
			.target(location)
			.zoom(18f)
			.build()
	}

	fun calculateElapseTime(startTime: Long, stopTime: Long): String {
		val elapseTime = stopTime - startTime
		val seconds = (elapseTime / 1000).toInt() % 60
		val min = (elapseTime / (1000 * 60) % 60)
		val hours = (elapseTime / (1000 * 60 * 60) % 24)

		return "$hours:$min:$seconds"
	}

	fun calculateTheDistance(locationList: MutableList<LatLng>): String {
		if (locationList.size > 1) {
			val meters = SphericalUtil.computeDistanceBetween(locationList.first(), locationList.last())
			val kilometers = meters / 1000
			return DecimalFormat("#.##").format(kilometers)
		}
		return "0.00"
	}
}