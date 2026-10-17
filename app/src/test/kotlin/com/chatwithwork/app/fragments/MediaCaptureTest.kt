package com.chatwithwork.app.fragments

import android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE
import android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
import android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE
import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaCaptureTest {
    private val origin = "${AppConfig.baseUrl}/"

    @Test
    fun `lets the app's own pages use the microphone`() {
        assertThat(MediaCapture.allows(origin, listOf(RESOURCE_AUDIO_CAPTURE))).isTrue()
    }

    @Test
    fun `keeps the microphone from other sites`() {
        val audio = listOf(RESOURCE_AUDIO_CAPTURE)
        assertThat(MediaCapture.allows("https://chatwithwork-apps.com/", audio)).isFalse()
        assertThat(MediaCapture.allows("https://chatwithwork.com.evil.example/", audio)).isFalse()
        assertThat(MediaCapture.allows(null, audio)).isFalse()
    }

    @Test
    fun `never grants the camera or anything else`() {
        assertThat(MediaCapture.allows(origin, listOf(RESOURCE_VIDEO_CAPTURE))).isFalse()
        assertThat(MediaCapture.allows(origin, listOf(RESOURCE_AUDIO_CAPTURE, RESOURCE_VIDEO_CAPTURE))).isFalse()
        assertThat(MediaCapture.allows(origin, listOf(RESOURCE_PROTECTED_MEDIA_ID))).isFalse()
        assertThat(MediaCapture.allows(origin, emptyList())).isFalse()
    }
}
