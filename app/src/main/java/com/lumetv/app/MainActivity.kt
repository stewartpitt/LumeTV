package com.lumetv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumetv.app.ui.AppViewModel
import com.lumetv.app.ui.HomeScreen
import com.lumetv.app.ui.LoginScreen
import com.lumetv.app.ui.LumeTheme
import com.lumetv.app.ui.MovieDetailScreen
import com.lumetv.app.ui.PlayerScreen
import com.lumetv.app.ui.Screen
import com.lumetv.app.ui.SeriesDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            LumeTheme {
                BackHandler(enabled = vm.stack.size > 1) { vm.back() }
                when (val screen = vm.current) {
                    Screen.Login -> LoginScreen(vm)
                    Screen.Home -> HomeScreen(vm)
                    is Screen.MovieDetail -> MovieDetailScreen(vm, screen.item)
                    is Screen.SeriesDetail -> SeriesDetailScreen(vm, screen.item)
                    is Screen.Player -> PlayerScreen(vm, screen.request)
                }
            }
        }
    }

}
