package io.github.plumblossomcat.torbox

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.plumblossomcat.torbox.ui.screens.login.LoginContent
import io.github.plumblossomcat.torbox.ui.screens.login.LoginState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class LoginScreenshotTest(private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun empty() = compose.shoot("01_login", dark) {
        LoginContent(LoginState(), null, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun error() = compose.shoot("01_login_error", dark) {
        LoginContent(
            LoginState(key = "3f6c1d2e-8b7a-4c59-9e21-5d0f4a7b8c90", error = "TorBox didn't accept that key. Copy it again from your TorBox settings."),
            "TorBox rejected your API key. It may have been reset — sign in with your current key.",
            {}, {}, {}, {}, {}, {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}

const val PHONE = "w393dp-h851dp-xhdpi"
