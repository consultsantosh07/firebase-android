// Copyright 2022 Google LLC
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

package com.google.firebase.components;

import java.util.List;

/**
 * Provides the ability to customize/decorate components as they are requested by {@link
 * ComponentRuntime}.
 *
 * <p>This makes it possible to do validation, change/add dependencies, or customize components'
 * initialization logic by decorating their {@link ComponentFactory factories}.
 */
public interface ComponentRegistrarProcessor {

  /** Default "noop" processor. */
  ComponentRegistrarProcessor NOOP = ComponentRegistrar::getComponents;

  List<Component<?>> processRegistrar(ComponentRegistrar registrar);
}
