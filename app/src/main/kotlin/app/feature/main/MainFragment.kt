package app.feature.main

import com.gigapi.general.Context
import com.gigapi.screens.fragment.Fragment
import core.navigation.Navigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainFragment(
    private val navigation: Navigation.Main,
    private val context: Context,
    private val onGameScreen: () -> Unit
): Fragment() {

    override fun onCreate() {
        println("MainFragment onCreate")
        lifecycleScope.launch {
            delay(1000.milliseconds)
            onGameScreen.invoke()
        }
    }

    override fun onRender(deltaTime: Float) {

    }

    override fun onResume() {

    }

    override fun onPause() {

    }

    override fun onDestroy() {
        println("MainFragment onDestroy")
    }
}