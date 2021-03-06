package dev.timroberts.simpleremote.presentation

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.timroberts.simpleremote.models.RokuDevice
import dev.timroberts.simpleremote.network.HostSelectionInterceptor
import dev.timroberts.simpleremote.network.RokuService
import dev.timroberts.simpleremote.network.models.DeviceInfoResponse
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.IOException

class MainViewModel(
    private val hostSelectionInterceptor: HostSelectionInterceptor,
    private val rokuService: RokuService,
    private val sharedPrefs: SharedPreferences
) : ViewModel() {

    companion object {
        private const val PREVIOUS_LOCATION_KEY = "PREVIOUS_DEVICE_LOCATION"
        private const val PREVIOUS_NAME_KEY = "PREVIOUS_DEVICE_NAME"
    }

    private val _viewState = MutableLiveData<MainViewState>()
    val viewState: LiveData<MainViewState> = _viewState

    init {
        _viewState.postValue(MainViewState.Initializing)
    }

    fun reconnectLastDevice() {
        _viewState.postValue(MainViewState.ReconnectingLastDevice)
        // first check if we have anything in sharedPrefs
        val previousLocation = sharedPrefs.getString(PREVIOUS_LOCATION_KEY, null)
        val previousName = sharedPrefs.getString(PREVIOUS_NAME_KEY, null)

        if (previousLocation == null && previousName == null) {
            Timber.d("No previous device information in SharedPreferences.")
            _viewState.postValue(MainViewState.NoDeviceSelected)
            return
        }

        // send device-info request to see if the same device is on & talking
        val response: DeviceInfoResponse? = try {
            runBlocking {
                hostSelectionInterceptor.host = previousLocation!!
                rokuService.getDeviceInfoAsync().body()
            }
        } catch (e: IOException) {
            Timber.d("Device did not respond to device-info query.")
            _viewState.postValue(MainViewState.DeviceDisconnected)
            clearLastDevice()
            return
        }

        if (response?.friendlyDeviceName == previousName) {
            Timber.d("Successfully reconnected to previous device.")
            _viewState.postValue(MainViewState.DeviceReconnected)
            // TODO: extract into DeviceInfoResponse extension function
            val device = RokuDevice(previousLocation!!, previousName!!, response!!.deviceId)
            setCurrentDevice(device, true)
        }
    }

    fun setCurrentDevice(rokuDevice: RokuDevice, isPrevious: Boolean = false) {
        hostSelectionInterceptor.host = rokuDevice.location
        _viewState.postValue(MainViewState.DeviceSelected(rokuDevice))
        if (!isPrevious) sharedPrefs.edit {
            putString(PREVIOUS_LOCATION_KEY, rokuDevice.location)
            putString(PREVIOUS_NAME_KEY, rokuDevice.friendlyDeviceName)
        }
    }

    private fun clearLastDevice() {
        sharedPrefs.edit {
            remove(PREVIOUS_NAME_KEY)
            remove(PREVIOUS_LOCATION_KEY)
        }
    }
}