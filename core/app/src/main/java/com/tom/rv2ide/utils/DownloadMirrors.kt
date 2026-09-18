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

package com.tom.rv2ide.utils

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.slf4j.LoggerFactory

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

/**
 * CN-friendly download helpers. When mirror mode is enabled, GitHub URLs are
 * retried through public mirror prefixes first, falling back to the original URL.
 */
object DownloadMirrors {

  private val log = LoggerFactory.getLogger(DownloadMirrors::class.java)

  private const val PREFS_KEY = "use_mirror_downloads"

  /** Floor for a single mirror/direct attempt so the last candidate still gets a real chance. */
  private const val MIN_ATTEMPT_TIMEOUT_MS = 3000

  private val GITHUB_HOSTS =
      listOf(
          "https://github.com/",
          "https://raw.githubusercontent.com/",
          "https://objects.githubusercontent.com/",
          "https://codeload.github.com/",
      )

  private val MIRROR_PREFIXES = listOf("https://ghfast.top", "https://ghproxy.net")

  fun isMirrorEnabled(context: Context): Boolean =
      PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_KEY, false)

  fun setMirrorEnabled(context: Context, enabled: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putBoolean(PREFS_KEY, enabled)
        .apply()
  }

  fun candidateUrls(original: String, useMirror: Boolean): List<String> {
    val shouldProxy = useMirror && GITHUB_HOSTS.any { original.startsWith(it) }
    if (!shouldProxy) {
      return listOf(original)
    }
    return MIRROR_PREFIXES.map { "$it/$original" } + original
  }

  /**
   * GETs [original] trying mirror candidates first. Returns the body or null.
   *
   * The whole attempt sequence is bounded by [totalBudgetMs]: every candidate gets an equal
   * share of the remaining budget (never less than [MIN_ATTEMPT_TIMEOUT_MS], never more than
   * [timeoutMs]) so an unreachable mirror cannot starve the direct fallback. The effective
   * upper bound is the budget plus at most one attempt, because connect and read timeouts are
   * applied sequentially.
   */
  fun fetchText(
      context: Context,
      original: String,
      timeoutMs: Int = 10000,
      totalBudgetMs: Int = 20000,
  ): String? {
    val candidates = candidateUrls(original, isMirrorEnabled(context))
    val deadline = System.currentTimeMillis() + totalBudgetMs

    for (index in candidates.indices) {
      val candidate = candidates[index]
      val remaining = deadline - System.currentTimeMillis()
      if (remaining <= 0) {
        log.warn("GET {} skipped: fetch budget exhausted", candidate)
        break
      }

      val candidatesLeft = candidates.size - index
      val fairShare = remaining / candidatesLeft
      val attemptTimeout =
          minOf(timeoutMs.toLong(), maxOf(fairShare, MIN_ATTEMPT_TIMEOUT_MS.toLong())).toInt()

      var connection: HttpURLConnection? = null
      try {
        connection = URL(candidate).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = attemptTimeout
        connection.readTimeout = attemptTimeout
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_OK) {
          return connection.inputStream.bufferedReader().use { it.readText() }
        }
        log.warn("GET {} -> HTTP {}", candidate, code)
      } catch (e: Exception) {
        log.warn("GET {} failed: {}", candidate, e.message)
      } finally {
        connection?.disconnect()
      }
    }
    return null
  }

  /**
   * Downloads [original] to [destFile] trying mirror candidates first.
   *
   * Blocking network IO happens on the caller's dispatcher, but [onProgress] is a suspend
   * callback so callers may hop to another thread (e.g. the main thread) from it. Progress
   * is reported as (bytesRead, contentLength) only when the length is known, and is throttled
   * to whole-percent changes so a fast mirror cannot flood the UI thread with updates.
   *
   * @return true only when the complete body was written to [destFile].
   */
  suspend fun downloadFile(
      context: Context,
      original: String,
      destFile: File,
      onProgress: (suspend (Long, Long) -> Unit)? = null,
  ): Boolean {
    for (candidate in candidateUrls(original, isMirrorEnabled(context))) {
      var connection: HttpURLConnection? = null
      try {
        connection = URL(candidate).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
          log.warn("Download {} -> HTTP {}", candidate, code)
          continue
        }

        val contentLength = connection.contentLength.toLong()
        var downloaded = 0L
        var lastPercent = -1
        connection.inputStream.use { input ->
          FileOutputStream(destFile).use { output ->
            val buffer = ByteArray(8192)
            while (true) {
              // Blocking reads are not cancellable, so check between chunks instead.
              currentCoroutineContext().ensureActive()
              val read = input.read(buffer)
              if (read == -1) {
                break
              }
              output.write(buffer, 0, read)
              downloaded += read
              if (contentLength > 0 && onProgress != null) {
                val percent = (downloaded * 100 / contentLength).toInt()
                // Always report the final chunk so the UI can reach 100%.
                if (percent != lastPercent || downloaded == contentLength) {
                  lastPercent = percent
                  onProgress(downloaded, contentLength)
                }
              }
            }
          }
        }

        if (contentLength > 0 && downloaded != contentLength) {
          // A truncated body (common with flaky mirrors) must never be installed.
          log.warn("Download {} truncated: {} of {} bytes", candidate, downloaded, contentLength)
          destFile.delete()
          continue
        }

        log.info("Downloaded {} ({} bytes)", candidate, downloaded)
        return true
      } catch (e: CancellationException) {
        destFile.delete()
        throw e
      } catch (e: Exception) {
        log.warn("Download {} failed: {}", candidate, e.message)
        destFile.delete()
      } finally {
        connection?.disconnect()
      }
    }
    return false
  }
}
