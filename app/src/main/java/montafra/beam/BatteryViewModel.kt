package montafra.beam

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryData(
    val power: String = "-",
    val current: String = "-",
    val voltage: String = "-",
    val energy: String = "-",
    val temperature: String = "-",
    val chargeLevel: String = "-",
    val charging: String = "-",
    val chargingSince: String = "-",
    val timeToFullCharge: String = "-",
    val screenTime: String = "-",
    val chargeLevelFloat: Float = 0f,
)

class BatteryViewModel(app: Application) : AndroidViewModel(app) {
    private val _data = MutableStateFlow(BatteryData())
    val data: StateFlow<BatteryData> = _data.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val ind = "-"
            val levelStr = intent.getStringExtra("chargeLevel") ?: ind
            val levelFloat = levelStr.removeSuffix("%").toFloatOrNull()?.div(100f) ?: 0f
            _data.value = BatteryData(
                power = intent.getStringExtra("power") ?: ind,
                current = intent.getStringExtra("current") ?: ind,
                voltage = intent.getStringExtra("voltage") ?: ind,
                energy = intent.getStringExtra("energy") ?: ind,
                temperature = intent.getStringExtra("temperature") ?: ind,
                chargeLevel = levelStr,
                charging = intent.getStringExtra("charging") ?: ind,
                chargingSince = intent.getStringExtra("chargingSince") ?: ind,
                timeToFullCharge = intent.getStringExtra("timeToFullCharge") ?: ind,
                screenTime = intent.getStringExtra("screenTime") ?: ind,
                chargeLevelFloat = levelFloat,
            )
        }
    }

    init {
        app.registerReceiver(receiver, IntentFilter(batteryDataResp), Context.RECEIVER_NOT_EXPORTED)
        requestUpdate()
    }

    fun requestUpdate() {
        getApplication<Application>().sendBroadcast(
            Intent().setPackage(getApplication<Application>().packageName).setAction(batteryDataReq)
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(receiver)
    }
}
