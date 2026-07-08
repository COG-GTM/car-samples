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
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Mercedes-Benz "MBUX Drive Status" panel — an AAOS customization of the
 * CarGearViewer sample. Shows a PRND gear indicator, live speed readout,
 * and battery/fuel level, all driven by VHAL property events.
 */
class MainActivity : Activity() {
    companion object {
        private const val TAG = "MBUXDriveStatus"

        // CURRENT_GEAR values (from android.car.hardware.CarSensorEvent)
        private const val GEAR_NEUTRAL = 0x0001
        private const val GEAR_REVERSE = 0x0002
        private const val GEAR_PARK = 0x0004
        private const val GEAR_DRIVE = 0x0008

        // MB accent blue for the active gear
        private const val MB_BLUE = 0xFF00ADEF.toInt()
        // Dimmed color for inactive gears
        private const val MB_DIM = 0xFF555555.toInt()

        // Sample rate (Hz) used to subscribe to CONTINUOUS properties
        // (PERF_VEHICLE_SPEED, EV_BATTERY_LEVEL, FUEL_LEVEL). These cannot be
        // subscribed with SENSOR_RATE_ONCHANGE (0 Hz).
        private const val CONTINUOUS_SAMPLE_RATE_HZ = 10f
    }

    private lateinit var gearP: TextView
    private lateinit var gearR: TextView
    private lateinit var gearN: TextView
    private lateinit var gearD: TextView
    private lateinit var speedValueTextView: TextView
    private lateinit var batteryPercentTextView: TextView
    private lateinit var batteryBar: ProgressBar
    private lateinit var batteryLabel: TextView

    private lateinit var car: Car
    private lateinit var carPropertyManager: CarPropertyManager

    private var batteryCapacity: Float = 0f
    private var usingFuelFallback: Boolean = false

    private var gearListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<Any>) {
            Log.d(TAG, "Gear onChangeEvent: propertyId=${value.propertyId}, value=${value.value}")
            runOnUiThread { highlightGear(value.value as Int) }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "Gear error event, propId=$propId")
        }
    }

    private var speedListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<Any>) {
            Log.d(TAG, "Speed onChangeEvent: value=${value.value}")
            runOnUiThread {
                val ms = value.value as Float
                val kmh = ms * 3.6f
                speedValueTextView.text = String.format("%.0f", kmh)
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "Speed error event, propId=$propId")
        }
    }

    private var batteryListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<Any>) {
            Log.d(TAG, "Battery/Fuel onChangeEvent: value=${value.value}")
            runOnUiThread { updateBatteryLevel(value.value as Float) }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "Battery/Fuel error event, propId=$propId")
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
        batteryPercentTextView = findViewById(R.id.batteryPercentTextView)
        batteryBar = findViewById(R.id.batteryBar)
        batteryLabel = findViewById(R.id.batteryLabel)

        car = Car.createCar(this)
        carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

        registerGearCallback()
        registerSpeedCallback()
        registerBatteryOrFuelCallback()
    }

    override fun onDestroy() {
        super.onDestroy()
        car.disconnect()
    }

    private fun registerGearCallback() {
        carPropertyManager.registerCallback(
            gearListener,
            VehiclePropertyIds.CURRENT_GEAR,
            CarPropertyManager.SENSOR_RATE_ONCHANGE
        )
    }

    private fun registerSpeedCallback() {
        // PERF_VEHICLE_SPEED is a CONTINUOUS property, so it must be subscribed
        // at a non-zero sample rate (Hz) rather than SENSOR_RATE_ONCHANGE.
        try {
            val registered = carPropertyManager.registerCallback(
                speedListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CONTINUOUS_SAMPLE_RATE_HZ
            )
            Log.d(TAG, "PERF_VEHICLE_SPEED registered=$registered")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register for PERF_VEHICLE_SPEED: ${e.message}")
        }
    }

    private fun registerBatteryOrFuelCallback() {
        // Reading the capacity requires CAR_INFO, which may be unavailable; read
        // it best-effort so a missing capacity never blocks the level subscription.
        readEnergyCapacity()

        // EV_BATTERY_LEVEL / FUEL_LEVEL are CONTINUOUS properties -> subscribe at
        // a non-zero sample rate. Prefer EV battery, fall back to fuel level.
        if (subscribeEnergyLevel(VehiclePropertyIds.EV_BATTERY_LEVEL, fuel = false)) {
            return
        }
        if (subscribeEnergyLevel(VehiclePropertyIds.FUEL_LEVEL, fuel = true)) {
            return
        }
        batteryPercentTextView.text = getString(R.string.placeholder_dash)
    }

    private fun readEnergyCapacity() {
        batteryCapacity = try {
            carPropertyManager.getProperty<Float>(
                VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, 0
            )?.value ?: 0f
        } catch (e: Exception) {
            Log.w(TAG, "EV battery capacity unavailable: ${e.message}")
            try {
                carPropertyManager.getProperty<Float>(
                    VehiclePropertyIds.INFO_FUEL_CAPACITY, 0
                )?.value ?: 0f
            } catch (e2: Exception) {
                Log.w(TAG, "Fuel capacity unavailable: ${e2.message}")
                0f
            }
        }
        Log.d(TAG, "Energy capacity: $batteryCapacity")
    }

    private fun subscribeEnergyLevel(propertyId: Int, fuel: Boolean): Boolean {
        return try {
            val registered = carPropertyManager.registerCallback(
                batteryListener, propertyId, CONTINUOUS_SAMPLE_RATE_HZ
            )
            Log.d(TAG, "Energy level propertyId=$propertyId registered=$registered")
            if (registered) {
                usingFuelFallback = fuel
                if (fuel) batteryLabel.text = getString(R.string.fuel_label)
            }
            registered
        } catch (e: Exception) {
            Log.w(TAG, "Could not register for propertyId=$propertyId: ${e.message}")
            false
        }
    }

    private fun highlightGear(gearValue: Int) {
        gearP.setTextColor(if (gearValue == GEAR_PARK) MB_BLUE else MB_DIM)
        gearR.setTextColor(if (gearValue == GEAR_REVERSE) MB_BLUE else MB_DIM)
        gearN.setTextColor(if (gearValue == GEAR_NEUTRAL) MB_BLUE else MB_DIM)
        gearD.setTextColor(if (gearValue == GEAR_DRIVE) MB_BLUE else MB_DIM)
    }

    private fun updateBatteryLevel(level: Float) {
        if (batteryCapacity > 0f) {
            val pct = (level / batteryCapacity * 100f).toInt().coerceIn(0, 100)
            batteryPercentTextView.text = "$pct%"
            batteryBar.progress = pct
        } else {
            // If capacity is unknown, show raw level as percentage
            val pct = level.toInt().coerceIn(0, 100)
            batteryPercentTextView.text = "$pct%"
            batteryBar.progress = pct
        }
    }
}
