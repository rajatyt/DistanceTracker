package com.example.distancetracker.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.example.distancetracker.ui.MapUtils.calculateTheDistance
import com.example.distancetracker.utils.Constants.ACTION_SERVICE_START
import com.example.distancetracker.utils.Constants.ACTION_SERVICE_STOP
import com.example.distancetracker.utils.Constants.LOCATION_FASTEST_UPDATE_INTERVAL
import com.example.distancetracker.utils.Constants.LOCATION_UPDATE_INTERVAL
import com.example.distancetracker.utils.Constants.NOTIFICATION_CHANNEL_ID
import com.example.distancetracker.utils.Constants.NOTIFICATION_CHANNEL_NAME
import com.example.distancetracker.utils.Constants.NOTIFICATION_ID
import com.google.android.gms.common.util.MapUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackerService : LifecycleService() {

	@Inject
	lateinit var notification: NotificationCompat.Builder

	@Inject
	lateinit var notificationManager: NotificationManager

	private lateinit var fusedLocationProviderClient: FusedLocationProviderClient


	companion object {
		val started = MutableLiveData<Boolean>()
		val startTime = MutableLiveData<Long>()
		val stopTime = MutableLiveData<Long>()

		val locationList = MutableLiveData<MutableList<LatLng>>()

	}

	private fun setInitialValues() {
		started.postValue(false)
		startTime.postValue(0L)
		stopTime.postValue(0L)
		locationList.postValue(mutableListOf())
	}

	private val locationCallback = object : LocationCallback() {
		override fun onLocationResult(result: LocationResult) {
			super.onLocationResult(result)
			result?.locations?.let { locations ->
				for (location in locations) {
//					val newLocation = LatLng(location.latitude, location.longitude)
					updateLocationList(location)
					updateNotificationPeriodically()
				}
			}
		}
	}


	private fun updateLocationList(location: Location) {
		val newLatLng = LatLng(location.latitude, location.longitude)
		locationList.value?.apply {
			add(newLatLng)
			locationList.postValue(this)
		}
	}


	override fun onCreate() {
		fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
		setInitialValues()
		super.onCreate()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		intent?.let {
			when (it.action) {
				ACTION_SERVICE_START -> {
					started.postValue(true)
					startForegroundService()
					startLocationUpdates()
				}

				ACTION_SERVICE_STOP -> {
					started.postValue(false)
					stopForegroundService()
				}

				else -> {

				}
			}
		}
		return super.onStartCommand(intent, flags, startId)

	}


	private fun removeLocationUpdates() {
		fusedLocationProviderClient.removeLocationUpdates(locationCallback)
	}

	private fun startForegroundService() {
		createNotificationChannel()
		startForeground(NOTIFICATION_ID, notification.build())
	}

	@SuppressLint("MissingPermission")
	private fun startLocationUpdates() {
//		val locationRequest = LocationRequest().apply {
//			interval = LOCATION_UPDATE_INTERVAL
//			fastestInterval = LOCATION_FASTEST_UPDATE_INTERVAL
//			priority = LocationRequest.PRIORITY_HIGH_ACCURACY
		//Priority.PRIORITY_HIGH_ACCURACY
		val locationRequest =
			LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
				.apply {
					setMinUpdateDistanceMeters(LOCATION_FASTEST_UPDATE_INTERVAL.toFloat())
					setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
					setWaitForAccurateLocation(true)
				}.build()



		fusedLocationProviderClient.requestLocationUpdates(
			locationRequest,
			locationCallback,
			Looper.getMainLooper()

		)
		startTime.postValue(System.currentTimeMillis())
	}

	private fun stopForegroundService() {
		removeLocationUpdates()
		(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(
			NOTIFICATION_ID
		)
		stopForeground(true)
		stopSelf()
		stopTime.postValue(System.currentTimeMillis())

	}

	private fun updateNotificationPeriodically() {
		notification.apply {
			setContentTitle("Distance Tracker")
			setContentText(locationList.value?.let {
				calculateTheDistance(
					locationList = it
				)
			} + "km")
		}
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				NOTIFICATION_CHANNEL_ID,
				NOTIFICATION_CHANNEL_NAME,
				NotificationManager.IMPORTANCE_LOW

			)
			notificationManager.createNotificationChannel(channel)
		}
	}
}