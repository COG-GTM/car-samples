/*
 * Copyright (C) 2026 The Android Open Source Project
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

package androidx.car.app.sample.navigation.automotive;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.car.app.activity.CarAppActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

/** Connects platform Back to the car host's Back handler. */
public final class NavigationApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            registerActivityLifecycleCallbacks(this);
        }
    }

    @Override
    public void onActivityPostCreated(Activity activity, Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                && activity instanceof CarAppActivity carAppActivity) {
            OnBackInvokedDispatcher dispatcher = carAppActivity.getOnBackInvokedDispatcher();
            OnBackInvokedCallback callback = carAppActivity::onBackPressed;
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback);
            carAppActivity.getLifecycle().addObserver((LifecycleEventObserver) (owner, event) -> {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    dispatcher.unregisterOnBackInvokedCallback(callback);
                }
            });
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}
}
