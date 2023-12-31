package com.example.distancetracker.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.distancetracker.R
import com.example.distancetracker.databinding.FragmentMapsBinding
import com.example.distancetracker.service.TrackerService
import com.example.distancetracker.service.TrackerService.Companion.started
import com.example.distancetracker.ui.MapUtils.calculateElapseTime
import com.example.distancetracker.ui.MapUtils.calculateTheDistance
import com.example.distancetracker.ui.MapUtils.setCameraPosition
import com.example.distancetracker.utils.Constants.ACTION_SERVICE_START
import com.example.distancetracker.utils.Constants.ACTION_SERVICE_STOP
import com.example.distancetracker.utils.ExtensionFunctions.disable
import com.example.distancetracker.utils.ExtensionFunctions.enable
import com.example.distancetracker.utils.ExtensionFunctions.hide
import com.example.distancetracker.utils.ExtensionFunctions.show
import com.example.distancetracker.utils.Permissions.hasBackgroundLocationPermission
import com.example.distancetracker.utils.Permissions.requestBackgroundLocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.ButtCap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapsFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
	EasyPermissions.PermissionCallbacks, OnMarkerClickListener {
	private var _binding: FragmentMapsBinding? = null
	private val binding get() = _binding!!
	private lateinit var map: GoogleMap

	private var locationList = mutableListOf<LatLng>()
	private var polylineList = mutableListOf<Polyline>()
	private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
	private var startTime = 0L
	private var stopTime = 0L
	val started = MutableLiveData(false)
	private var markerList = mutableListOf<Marker>()


	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		_binding = FragmentMapsBinding.inflate(layoutInflater, container, false)

		binding.lifecycleOwner = this
		binding.tracking = this
		binding.startButton.setOnClickListener {
			onStartButtonClicked()

		}
		binding.stopButton.setOnClickListener {
			onStopButtonClicked()
		}
		binding.resetButton.setOnClickListener {
			onResetButtonClicked()
		}

		fusedLocationProviderClient =
			LocationServices.getFusedLocationProviderClient(requireContext())
		return binding.root
	}


	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
		mapFragment?.getMapAsync(this)
	}

	@SuppressLint("MissingPermission")
	override fun onMapReady(googleMap: GoogleMap) {
		map = googleMap!!
		map.isMyLocationEnabled = true
		map.setOnMyLocationButtonClickListener(this)
		map.setOnMarkerClickListener(this)
		map.uiSettings.apply {
			isZoomControlsEnabled = false
			isZoomGesturesEnabled = false
			isRotateGesturesEnabled = false
			isTiltGesturesEnabled = false
			isCompassEnabled = false
			isScrollGesturesEnabled = false
		}
		observeTrackerService()
	}

	private fun observeTrackerService() {
		TrackerService.locationList.observe(viewLifecycleOwner) {
			if (it != null) {
				locationList = it
				if (locationList.size > 1) {
					binding.stopButton.enable()
				}
				drawPolyline()
				followPolyline()

			}
		}
		TrackerService.started.observe(viewLifecycleOwner) {
			started.value = it
		}
		TrackerService.startTime.observe(viewLifecycleOwner) {
			startTime = it
		}
		TrackerService.stopTime.observe(viewLifecycleOwner) {
			stopTime = it
			if (stopTime != 0L) {
				showBiggerPicture()
				displayResults()
			}
		}
	}


	private fun drawPolyline() {
		var polyline = map.addPolyline(
			PolylineOptions().apply {
				width(10f)
				color(Color.BLUE)
				jointType(JointType.ROUND)
				startCap(ButtCap())
				endCap(ButtCap())
				addAll(locationList)
			}
		)
		polylineList.add(polyline)
	}

	private fun followPolyline() {
		if (locationList.isNotEmpty()) {
			map.animateCamera(CameraUpdateFactory.newCameraPosition(
					setCameraPosition(locationList.last())
				), 1000, null
			)
		}
	}


	private fun onStartButtonClicked() {
		if (hasBackgroundLocationPermission(requireContext())) {
			startCountDown()
			binding.startButton.disable()
			binding.startButton.hide()
			binding.stopButton.show()

		} else {
			requestBackgroundLocationPermission(this)
		}
	}

	private fun onStopButtonClicked() {
		stopForegroundService()
		binding.stopButton.hide()
		binding.startButton.show()
	}

	private fun onResetButtonClicked() {
		mapReset()
	}

	@SuppressLint("MissingPermission")
	private fun mapReset() {
		fusedLocationProviderClient.lastLocation.addOnCompleteListener {
			val lastKnownLocation = LatLng(
				it.result.latitude,
				it.result.longitude
			)
			map.animateCamera(
				CameraUpdateFactory.newCameraPosition(
					setCameraPosition(lastKnownLocation)
				)
			)
			for (polyline in polylineList) {
				polyline.remove()
			}

			for (marker in markerList) {
				marker.remove()
			}
			locationList.clear()
			markerList.clear()
			binding.resetButton.hide()
			binding.startButton.show()
		}
	}

	private fun showBiggerPicture() {
		val bounds = LatLngBounds.Builder()
		for (location in locationList) {
			bounds.include(location)
		}
		map.animateCamera(
			CameraUpdateFactory.newLatLngBounds(
				bounds.build(), 100
			), 200, null
		)
		addMarker(locationList.first())
		addMarker(locationList.last())

	}

	private fun addMarker(position: LatLng) {
		val marker = map.addMarker(MarkerOptions().position(position))
		marker?.let { markerList.add(it) }
	}

	private fun displayResults() {
		val result = com.example.distancetracker.model.Result(
			calculateTheDistance(locationList),
			calculateElapseTime(startTime, stopTime)
		)
		lifecycleScope.launch {
			delay(2500)
			val directions = MapsFragmentDirections.actionMapsFragmentToResultFragment(result)
			findNavController().navigate(directions)
			binding.startButton.apply {
				hide()
				enable()
			}
			binding.stopButton.hide()
			binding.resetButton.show()
		}
	}


	override fun onMyLocationButtonClick(): Boolean {
		binding.hintTextView.animate().alpha(0f).duration = 1500
		lifecycleScope.launch {
			delay(2500)
			binding.hintTextView.hide()
			binding.startButton.show()

		}
		return false
	}


	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
	}

	override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
		if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
			SettingsDialog.Builder(requireContext()).build().show()
		} else {
			requestBackgroundLocationPermission(this)
		}
	}

	override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
		onStartButtonClicked()
	}

	private fun sendActionCommandService(action: String) {
		Intent(
			requireContext(),
			TrackerService::class.java
		).apply {
			this.action = action
			requireContext().startService(this)
		}
	}

	private fun startCountDown() {
		binding.timerTextView.show()
		binding.stopButton.disable()
		val timer: CountDownTimer = object : CountDownTimer(4000, 1000) {
			override fun onTick(millisUntilFinished: Long) {
				val currentSecond = millisUntilFinished / 1000
				if (currentSecond.toString() == "0") {
					binding.timerTextView.text = "Go"
					binding.timerTextView.setTextColor(
						ContextCompat.getColor(
							requireContext(),
							R.color.black
						)
					)

				} else {
					binding.timerTextView.text = currentSecond.toString()
					binding.timerTextView.setTextColor(
						ContextCompat.getColor(
							requireContext(),
							R.color.red
						)
					)
				}
			}

			override fun onFinish() {
				sendActionCommandService(ACTION_SERVICE_START)
				binding.timerTextView.hide()

			}

		}
		timer.start()

	}

	private fun stopForegroundService() {
		binding.startButton.disable()
		sendActionCommandService(ACTION_SERVICE_STOP)
	}

	override fun onMarkerClick(p0: Marker): Boolean {
		return true
	}


}