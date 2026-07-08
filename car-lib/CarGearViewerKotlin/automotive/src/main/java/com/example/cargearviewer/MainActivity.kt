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
import java.util.Locale

/**
 * A simple activity that demonstrates connecting to car API and processing car property change
 * events.
 *
 * <p>Please see https://developer.android.com/reference/android/car/packages for API documentation.
 */
class MainActivity : Activity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val GEAR_P = 0x0004
        private const val GEAR_R = 0x0002
        private const val GEAR_N = 0x0001
        private const val GEAR_D = 0x0008
    }

    private lateinit var gearPTextView: TextView
    private lateinit var gearRTextView: TextView
    private lateinit var gearNTextView: TextView
    private lateinit var gearDTextView: TextView
    private lateinit var speedValueTextView: TextView
    private lateinit var batteryPercentTextView: TextView
    private lateinit var batteryBar: ProgressBar

    /** Car API. */
    private lateinit var car: Car

    /**
     * An API to read VHAL (vehicle hardware access layer) properties. List of vehicle properties
     * can be found in {@link VehiclePropertyIds}.
     *
     * <p>https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager
     */
    private lateinit var carPropertyManager: CarPropertyManager

    private var energyLevel: Float? = null
    private var energyCapacity: Float? = null

    private val carPropertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<Any>) {
            Log.d(TAG, "Received on changed car property event")
            when (value.propertyId) {
                VehiclePropertyIds.CURRENT_GEAR -> highlightGear(value.value as? Int ?: 0)
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> updateSpeed(value.value)
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                VehiclePropertyIds.FUEL_LEVEL -> updateEnergyLevel(value.value)
                VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                VehiclePropertyIds.INFO_FUEL_CAPACITY -> updateEnergyCapacity(value.value)
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "Received error car property event, propId=$propId")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gearPTextView = findViewById(R.id.gearP)
        gearRTextView = findViewById(R.id.gearR)
        gearNTextView = findViewById(R.id.gearN)
        gearDTextView = findViewById(R.id.gearD)
        speedValueTextView = findViewById(R.id.speedValueTextView)
        batteryPercentTextView = findViewById(R.id.batteryPercentTextView)
        batteryBar = findViewById(R.id.batteryBar)

        // createCar() returns a "Car" object to access car service APIs. It can return null if
        // car service is not yet ready but that is not a common case and can happen on rare cases
        // (for example car service crashes) so the receiver should be ready for a null car object.
        //
        // Other variants of this API allows more control over car service functionality (such as
        // handling car service crashes graciously). Please see the SDK documentation for this.
        car = Car.createCar(this)

        carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

        highlightGear(0)
        speedValueTextView.text = "—"
        batteryPercentTextView.text = "—"
        batteryBar.progress = 0

        registerProperty(VehiclePropertyIds.CURRENT_GEAR)
        registerProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED)
        registerEnergyProperties()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::carPropertyManager.isInitialized) {
            try {
                carPropertyManager.unregisterCallback(carPropertyListener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister callback", e)
            }
        }
        car.disconnect()
    }

    private fun registerProperty(propertyId: Int): Boolean {
        return try {
            carPropertyManager.registerCallback(
                carPropertyListener,
                propertyId,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register propertyId=$propertyId", e)
            false
        }
    }

    private fun registerEnergyProperties() {
        if (registerProperty(VehiclePropertyIds.EV_BATTERY_LEVEL)) {
            energyCapacity = readFloatProperty(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)
            energyLevel = readFloatProperty(VehiclePropertyIds.EV_BATTERY_LEVEL)
            renderEnergy()
            return
        }

        if (registerProperty(VehiclePropertyIds.FUEL_LEVEL)) {
            energyCapacity = readFloatProperty(VehiclePropertyIds.INFO_FUEL_CAPACITY)
            energyLevel = readFloatProperty(VehiclePropertyIds.FUEL_LEVEL)
            renderEnergy()
        }
    }

    private fun readFloatProperty(propertyId: Int): Float? {
        return try {
            carPropertyManager.getProperty(Float::class.javaObjectType, propertyId, 0)?.value
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read propertyId=$propertyId", e)
            null
        }
    }

    private fun updateSpeed(rawValue: Any?) {
        val metersPerSecond = rawValue as? Float ?: return
        val kmh = metersPerSecond * 3.6f
        speedValueTextView.text = String.format(Locale.US, "%.0f", kmh)
    }

    private fun updateEnergyLevel(rawValue: Any?) {
        energyLevel = rawValue.asFloat()
        renderEnergy()
    }

    private fun updateEnergyCapacity(rawValue: Any?) {
        energyCapacity = rawValue.asFloat()
        renderEnergy()
    }

    private fun renderEnergy() {
        val level = energyLevel
        val capacity = energyCapacity
        if (level == null || capacity == null || capacity <= 0f) {
            batteryPercentTextView.text = "—"
            batteryBar.progress = 0
            return
        }

        val percent = (level / capacity) * 100f
        val clampedPercent = percent.coerceIn(0f, 100f)
        batteryPercentTextView.text = String.format(Locale.US, "%.0f%%", clampedPercent)
        batteryBar.progress = clampedPercent.toInt()
    }

    private fun highlightGear(gearValue: Int) {
        gearPTextView.setTextColor(getGearColor(gearValue, GEAR_P))
        gearRTextView.setTextColor(getGearColor(gearValue, GEAR_R))
        gearNTextView.setTextColor(getGearColor(gearValue, GEAR_N))
        gearDTextView.setTextColor(getGearColor(gearValue, GEAR_D))
    }

    private fun getGearColor(activeGear: Int, expectedGear: Int): Int {
        return if (activeGear == expectedGear) {
            getColor(R.color.mb_blue)
        } else {
            getColor(R.color.mb_dim)
        }
    }

    private fun Any?.asFloat(): Float? {
        return when (this) {
            is Float -> this
            is Double -> this.toFloat()
            is Int -> this.toFloat()
            is Number -> this.toFloat()
            else -> null
        }
    }
}
