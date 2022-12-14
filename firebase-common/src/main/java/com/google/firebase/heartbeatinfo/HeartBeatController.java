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

package com.google.firebase.heartbeatinfo;

import com.google.android.gms.tasks.Task;

/**
 * Class provides information about heartbeats.
 *
 * <p>This exposes a function which returns a base-64 encoded string based on the stored heartbeats.
 */
public interface HeartBeatController {
  Task<String> getHeartBeatsHeader();
}
