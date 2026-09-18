/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.tom.rv2ide.artificial

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test for the JVM unit-test wiring of `:core:app`.
 *
 * It deliberately touches no Android or logging class: android.jar is stubbed in local unit
 * tests and the app's logging stack is Android-specific. Real coverage for the AI layer lands
 * with the pure helpers extracted from the providers (prompt assembly, history trimming,
 * correction detection, endpoint URL normalisation).
 */
class AiTestHarnessTest {

  @Test
  fun junitHarnessRuns() {
    assertEquals(4, 2 + 2)
  }
}
