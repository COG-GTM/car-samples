/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.cargearviewer

import android.app.Activity
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Mercedes-Benz "MBUX Drive Status" panel — an Android Automotive OS (AAOS)
 * customization of the CarGearViewer sample. It renders a PRND gear indicator,
 * a live speed readout, and a battery/fuel level tile, all driven by VHAL
 * vehicle property change events.
 *
 * <p>Please see https://developer.android.com/reference/android/car/packages for API documentation.
 */
class MainActivity : Activity() {
    companion object {
        private const val TAG = "MBUXDriveStatus"

        // CURRENT_GEAR values (from android.car.hardware.CarSensorEvent).
        private const val GEAR_NEUTRAL = 0x0001
        private const val GEAR_REVERSE = 0x0002
        private const val GEAR_PARK = 0x0004
        private const val GEAR_DRIVE = 0x0008

        // PERF_VEHICLE_SPEED, EV_BATTERY_LEVEL and FUEL_LEVEL are CONTINUOUS
        // properties and must be subscribed at a non-zero sample rate (Hz)
        // rather than SENSOR_RATE_ONCHANGE.
        private const val CONTINUOUS_SAMPLE_RATE_HZ = 10f

        private const val MS_TO_KMH = 3.6f
    }

    private lateinit var gearP: TextView
    private lateinit var gearR: TextView
    private lateinit var gearN: TextView
    private lateinit var gearD: TextView
    private lateinit var speedValueTextView: TextView
    private lateinit var batteryLabel: TextView
    private lateinit var batteryPercentTextView: TextView
    private lateinit var batteryBar: ProgressBar

    private var activeColor: Int = 0
    private var dimColor: Int = 0

    /** Car API. */
    private lateinit var car: Car

    /**
     * An API to read VHAL (vehicle hardware access layer) properties. List of vehicle properties
     * can be found in {@link VehiclePropertyIds}.
     *
     * <p>https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager
     */
    private lateinit var carPropertyManager: CarPropertyManager

    /** Energy capacity used to convert a raw EV/fuel level into a percentage. */
    private var energyCapacity: Float = 0f

    // A single callback fans out to the right UI element by branching on propertyId.
    private val carPropertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<Any>) {
            Log.d(TAG, "onChangeEvent propertyId=${value.propertyId} value=${value.value}")
            runOnUiThread {
                when (value.propertyId) {
                    VehiclePropertyIds.CURRENT_GEAR -> highlightGear(value.value as Int)
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> updateSpeed(value.value as Float)
                    VehiclePropertyIds.EV_BATTERY_LEVEL,
                    VehiclePropertyIds.FUEL_LEVEL -> updateEnergyLevel(value.value as Float)
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "Received error car property event, propId=$propId")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gearP = findViewById(R.id.gearP)
        gearR = findViewById(R.id.gearR)
        gearN = findViewById(R.id.gearN)
        gearD = findViewById(R.id.gearD)
        speedValueTextView = findViewById(R.id.speedValueTextView)
        batteryLabel = findViewById(R.id.batteryLabel)
        batteryPercentTextView = findViewById(R.id.batteryPercentTextView)
        batteryBar = findViewById(R.id.batteryBar)

        activeColor = ContextCompat.getColor(this, R.color.mb_blue)
        dimColor = ContextCompat.getColor(this, R.color.mb_dim)

        // createCar() returns a "Car" object to access car service APIs. It can return null if
        // car service is not yet ready but that is not a common case and can happen on rare cases
        // (for example car service crashes) so the receiver should be ready for a null car object.
        car = Car.createCar(this)
        carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

        registerGear()
        registerSpeed()
        registerEnergy()
    }

    override fun onDestroy() {
        super.onDestroy()

        car.disconnect()
    }

    /** Subscribes to gear change events (the sample's original behavior). */
    private fun registerGear() {
        carPropertyManager.registerCallback(
            carPropertyListener,
            VehiclePropertyIds.CURRENT_GEAR,
            CarPropertyManager.SENSOR_RATE_ONCHANGE
        )
    }

    private fun registerSpeed() {
        try {
            carPropertyManager.registerCallback(
                carPropertyListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CONTINUOUS_SAMPLE_RATE_HZ
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not subscribe to PERF_VEHICLE_SPEED: ${e.message}")
        }
    }

    /**
     * Prefers the EV battery level (fits the MB EQ line); if the emulator image
     * does not expose EV properties, falls back to FUEL_LEVEL and relabels the
     * tile. Capacity is read once, best-effort, to derive a percentage.
     */
    private fun registerEnergy() {
        if (subscribeEnergyLevel(
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                R.string.battery_label
            )
        ) {
            return
        }
        if (subscribeEnergyLevel(
                VehiclePropertyIds.FUEL_LEVEL,
                VehiclePropertyIds.INFO_FUEL_CAPACITY,
                R.string.fuel_label
            )
        ) {
            return
        }
        batteryPercentTextView.text = getString(R.string.placeholder_dash)
    }

    private fun subscribeEnergyLevel(levelId: Int, capacityId: Int, labelRes: Int): Boolean {
        return try {
            val registered = carPropertyManager.registerCallback(
                carPropertyListener, levelId, CONTINUOUS_SAMPLE_RATE_HZ
            )
            if (registered) {
                energyCapacity = readCapacity(capacityId)
                batteryLabel.text = getString(labelRes)
                Log.d(TAG, "Subscribed to energy level propertyId=$levelId capacity=$energyCapacity")
            }
            registered
        } catch (e: Exception) {
            Log.w(TAG, "Could not subscribe to energy propertyId=$levelId: ${e.message}")
            false
        }
    }

    private fun readCapacity(capacityId: Int): Float {
        return try {
            carPropertyManager.getProperty<Float>(capacityId, 0)?.value ?: 0f
        } catch (e: Exception) {
            Log.w(TAG, "Energy capacity propertyId=$capacityId unavailable: ${e.message}")
            0f
        }
    }

    /** Highlights the active gear in MB blue and dims the rest. */
    private fun highlightGear(gearValue: Int) {
        gearP.setTextColor(if (gearValue == GEAR_PARK) activeColor else dimColor)
        gearR.setTextColor(if (gearValue == GEAR_REVERSE) activeColor else dimColor)
        gearN.setTextColor(if (gearValue == GEAR_NEUTRAL) activeColor else dimColor)
        gearD.setTextColor(if (gearValue == GEAR_DRIVE) activeColor else dimColor)
    }

    private fun updateSpeed(speedMetersPerSecond: Float) {
        val kmh = speedMetersPerSecond * MS_TO_KMH
        speedValueTextView.text = String.format("%.0f", kmh)
    }

    private fun updateEnergyLevel(level: Float) {
        // Guard against a missing/zero capacity: EV_BATTERY_LEVEL/FUEL_LEVEL are
        // absolute (kWh / ml), so without capacity we cannot derive a percentage.
        if (energyCapacity <= 0f) {
            batteryPercentTextView.text = getString(R.string.placeholder_dash)
            return
        }
        val pct = (level / energyCapacity * 100f).toInt().coerceIn(0, 100)
        batteryPercentTextView.text = "$pct%"
        batteryBar.progress = pct
    }
}
