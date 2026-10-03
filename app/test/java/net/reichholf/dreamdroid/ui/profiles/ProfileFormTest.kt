package net.reichholf.dreamdroid.ui.profiles

import kotlinx.coroutines.test.runTest
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.VpsMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** [ProfileForm] and [ProfileTextFields] carry the profile's switches to the form and back. */
class ProfileFormTest {
    @Test
    fun newProfilesHaveNoVpsDefault() {
        assertEquals(VpsMode.Off, Profile.getDefault().vpsDefault)
        assertEquals(VpsMode.Off, ProfileForm.from(Profile.getDefault()).vpsDefault)
    }

    @ParameterizedTest
    @EnumSource(VpsMode::class)
    fun vpsDefaultRoundTripsThroughTheForm(mode: VpsMode) = runTest {
        val source = Profile.getDefault().apply {
            host = "10.0.0.1"
            vpsDefault = mode
        }
        val fields = ProfileTextFields(backgroundScope).apply { fill(source) }
        val form = ProfileForm.from(source)

        val saved = Profile.getDefault().also { fields.applyTo(it, form) }

        assertEquals(mode, form.vpsDefault)
        assertEquals(mode, saved.vpsDefault)
    }

    @Test
    fun editedVpsDefaultIsSaved() = runTest {
        val profile = Profile.getDefault()
        val fields = ProfileTextFields(backgroundScope).apply { fill(profile) }
        val form = ProfileForm.from(profile).copy(vpsDefault = VpsMode.Safe)

        fields.applyTo(profile, form)

        assertEquals(VpsMode.Safe, profile.vpsDefault)
    }
}
