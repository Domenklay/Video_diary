package com.quietmoments.diary

import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayAuthorizationSupportTest {
    @Test fun invalidServiceHasAnActionableRussianExplanation() {
        val exception = IllegalStateException(
            "API: 17: API: Auth.Api.Identity.Authorization.API is not available on " +
                "this device. Connection failed with: " +
                "ConnectionResult{statusCode=SERVICE_INVALID, resolution=null}"
        )
        val message = GooglePlayAuthorizationSupport.loginFailure(exception)
        assertTrue(message.contains("SERVICE_INVALID"))
        assertTrue(message.contains("эмуляторах"))
        assertTrue(message.contains("ZIP"))
    }

    @Test fun unavailableAuthorizationApiSuggestsManualBackup() {
        val exception = IllegalStateException("API: 17: API is not available on this device")
        assertTrue(GooglePlayAuthorizationSupport.loginFailure(exception).contains("ZIP"))
    }

    @Test fun oauthMisconfigurationIsNotConfusedWithInvalidPlayServices() {
        val exception = IllegalStateException("DEVELOPER_ERROR")
        val message = GooglePlayAuthorizationSupport.loginFailure(exception)
        assertTrue(message.contains("SHA-1"))
    }
}
