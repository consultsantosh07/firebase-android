// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.appdistribution.impl;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;

/** Class that handles storage for App Distribution SignIn persistence. */
class SharedPreferencesStorage {
  // the name of the preferences is a historical artifact for backwards compatibility
  private static final String PREFERENCES_NAME = "FirebaseAppDistributionSignInStorage";
  private static final String SIGNIN_TAG = "firebase_app_distribution_signin";
  private static final String DEFAULT_TRIGGER_INFO_TAG =
      "firebase_app_distribution_feedback_default_trigger_info";

  private final SharedPreferences preferences;

  SharedPreferencesStorage(Context applicationContext) {
    preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  void setSignInStatus(boolean testerSignedIn) {
    preferences.edit().putBoolean(SIGNIN_TAG, testerSignedIn).apply();
  }

  boolean getSignInStatus() {
    return preferences.getBoolean(SIGNIN_TAG, false);
  }

  void setDefaultTriggerInfo(@Nullable String defaultTriggerInfo) {
    preferences.edit().putString(DEFAULT_TRIGGER_INFO_TAG, defaultTriggerInfo).apply();
  }

  @Nullable
  String geDefaultTriggerInfo() {
    return preferences.getString(DEFAULT_TRIGGER_INFO_TAG, /* default= */ (String) null);
  }
}
