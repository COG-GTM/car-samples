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
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Activity that demonstrates connecting to the car API and visualizing drivetrain and energy
 * telemetry.
 */
class MainActivity : Activity() {
    companion object {
        private const val TAG = "MainActivity"

        private const val GEAR_PARK = 4
        private const val GEAR_DRIVE = 8
        private const val GEAR_REVERSE = 2
        private const val GEAR_NEUTRAL = 1

        private const val DEFAULT_PLACEHOLDER = "—"
    }

    private lateinit var gearPTextView: TextView
    private lateinit var gearRTextView: TextView
    private lateinit var gearNTextView: TextView
    private lateinit var gearDTextView: TextView
    private lateinit var speedValueTextView: TextView
    private lateinit var batteryLabelTextView: TextView
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

    private var activeEnergyPropertyId: Int = VehiclePropertyIds.EV_BATTERY_LEVEL
    private var activeEnergyCapacity: Float? = null
    private var activeEnergyLabelResId: Int = R.string.battery_label

    private val carPropertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<Any>) {
            Log.d(TAG, "Received changed car property event for ${value.propertyId}")
            runOnUiThread {
                when (value.propertyId) {
                    VehiclePropertyIds.CURRENT_GEAR -> highlightGear(value.value as Int)
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> updateSpeed(value.value)
                    activeEnergyPropertyId -> updateEnergyLevel(value.value, activeEnergyCapacity)
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "Received error car property event, propId=$propId, zone=$zone")
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
        batteryLabelTextView = findViewById(R.id.batteryLabelTextView)
        batteryPercentTextView = findViewById(R.id.batteryPercentTextView)
        batteryBar = findViewById(R.id.batteryBar)

        car = Car.createCar(this)
        carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

        val evBatteryCapacity = readFloatProperty(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)
        if (evBatteryCapacity != null && evBatteryCapacity > 0f) {
            activeEnergyPropertyId = VehiclePropertyIds.EV_BATTERY_LEVEL
            activeEnergyCapacity = evBatteryCapacity
            activeEnergyLabelResId = R.string.battery_label
        } else {
            activeEnergyPropertyId = VehiclePropertyIds.FUEL_LEVEL
            activeEnergyCapacity = readFloatProperty(VehiclePropertyIds.INFO_FUEL_CAPACITY)
            activeEnergyLabelResId = R.string.fuel_label
        }

        registerPropertyCallback(VehiclePropertyIds.CURRENT_GEAR, "CURRENT_GEAR")
        registerPropertyCallback(VehiclePropertyIds.PERF_VEHICLE_SPEED, "PERF_VEHICLE_SPEED")
        registerPropertyCallback(
            activeEnergyPropertyId,
            if (activeEnergyPropertyId == VehiclePropertyIds.EV_BATTERY_LEVEL) {
                "EV_BATTERY_LEVEL"
            } else {
                "FUEL_LEVEL"
            }
        )

        highlightGear(GEAR_PARK)
        speedValueTextView.text = "0"
        batteryLabelTextView.text = getString(activeEnergyLabelResId)
        batteryPercentTextView.text = DEFAULT_PLACEHOLDER
        batteryBar.progress = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::carPropertyManager.isInitialized) {
            try {
                carPropertyManager.unregisterCallback(carPropertyListener)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to unregister car property callback", e)
            }
        }
        if (::car.isInitialized) {
            car.disconnect()
        }
    }

    private fun registerPropertyCallback(propertyId: Int, propertyName: String) {
        try {
            val registered = carPropertyManager.registerCallback(
                carPropertyListener,
                propertyId,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            if (!registered) {
                Log.w(TAG, "registerCallback returned false for $propertyName")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register callback for $propertyName", e)
        }
    }

    private fun readFloatProperty(propertyId: Int): Float? {
        return try {
            carPropertyManager.getProperty(Float::class.javaObjectType, propertyId, 0)?.value
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read property $propertyId", e)
            null
        }
    }

    private fun highlightGear(gearValue: Int) {
        val activeColor = getColor(R.color.mb_blue)
        val inactiveColor = getColor(R.color.mb_dim)

        styleGearTextView(gearPTextView, gearValue == GEAR_PARK, activeColor, inactiveColor)
        styleGearTextView(gearRTextView, gearValue == GEAR_REVERSE, activeColor, inactiveColor)
        styleGearTextView(gearNTextView, gearValue == GEAR_NEUTRAL, activeColor, inactiveColor)
        styleGearTextView(gearDTextView, gearValue == GEAR_DRIVE, activeColor, inactiveColor)
    }

    private fun styleGearTextView(
        textView: TextView,
        active: Boolean,
        activeColor: Int,
        inactiveColor: Int
    ) {
        textView.setTextColor(if (active) activeColor else inactiveColor)
        textView.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun updateSpeed(rawValue: Any) {
        val speedMetersPerSecond = rawValue as? Float ?: return
        val speedKmh = speedMetersPerSecond * 3.6f
        speedValueTextView.text = String.format(Locale.US, "%.0f", speedKmh)
    }

    private fun updateEnergyLevel(rawValue: Any, capacity: Float?) {
        val level = rawValue as? Float
        if (level == null) {
            batteryPercentTextView.text = DEFAULT_PLACEHOLDER
            batteryBar.progress = 0
            return
        }

        val percent = when {
            capacity != null && capacity > 0f -> (level / capacity) * 100f
            level in 0f..1f -> level * 100f
            else -> null
        }

        if (percent == null || percent.isNaN() || percent.isInfinite()) {
            batteryPercentTextView.text = DEFAULT_PLACEHOLDER
            batteryBar.progress = 0
            return
        }

        val clampedPercent = percent.coerceIn(0f, 100f)
        batteryPercentTextView.text = String.format(Locale.US, "%.0f%%", clampedPercent)
        batteryBar.progress = clampedPercent.roundToInt()
    }
}
