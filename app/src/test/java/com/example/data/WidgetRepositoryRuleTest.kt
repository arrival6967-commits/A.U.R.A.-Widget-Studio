package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WidgetRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveWidget_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = WidgetRepository(firestore)

        val sample = WidgetDesign(
            title = "Liquid Weather",
            subtitle = "Sunny • +24°C",
            primaryValue = "+24°C",
            secondaryValue = "UV Index 4",
            prompt = "Create a Liquid Glass weather widget",
            stylePreset = WidgetStylePreset.LIQUID_GLASS.name,
            category = WidgetCategory.WEATHER.name,
            sizePreset = WidgetSizePreset.WIDE_4X2.name,
            primaryColorHex = "#38BDF8",
            secondaryColorHex = "#A855F7",
            surfaceColorHex = "#0F172A",
            glassOpacity = 0.4,
            blurRadiusDp = 24.0,
            specularIntensity = 0.85,
            cornerRadiusDp = 28.0
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createWidget(sample)
        }
        assertTrue("Create widget should succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val widgetId = createResult.getOrThrow()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWidgets(aliceUid).first { list -> list.any { it.id == widgetId } }
        }
        assertTrue(emitted.any { it.id == widgetId && it.title == "Liquid Weather" })

        val updated = emitted.first { it.id == widgetId }.copy(
            glassOpacity = 0.65,
            primaryColorHex = "#F472B6"
        )
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateWidget(updated)
        }
        assertTrue("Update widget should succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)
    }

    @Test
    fun getWidgetById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WidgetRepository(firestore)
        val widgetId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createWidget(WidgetDesign(title = "Alice Private Widget")).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = WidgetRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getWidgetById(aliceUid, widgetId)
        }
        assertTrue("Cross-user access must fail", result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeWidgets_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = WidgetRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeWidgets(aliceUid).first()
            }
            fail("Expected exception when observing widgets unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun saveAndObserveWorkspace_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = WidgetRepository(firestore)

        val workspace = UserWorkspace(
            activeWallpaperPreset = WallpaperPreset.SOLAR_FLARE.name,
            extractedPaletteHex = WallpaperPreset.SOLAR_FLARE.defaultPalette,
            darkThemeMode = DarkThemeMode.DARK.name,
            globalGlassOpacity = 0.50,
            globalBlurDp = 28.0,
            syncDeviceName = "Galaxy S26 Ultra"
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveWorkspace(workspace)
        }
        assertTrue("Save workspace should succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWorkspace(aliceUid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals(DarkThemeMode.DARK.name, observed?.darkThemeMode)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_widget@test.com"
        const val BOB_EMAIL = "bob_widget@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
