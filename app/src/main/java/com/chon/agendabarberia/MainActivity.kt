package com.chon.agendabarberia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chon.agendabarberia.ui.screens.DetailScreen
import com.chon.agendabarberia.ui.screens.HomeScreen
import com.chon.agendabarberia.ui.screens.NuevaCitaScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { HomeScreen(navController) }
                        composable("nuevaCita") { NuevaCitaScreen(navController) }
                        composable("detalle/{citaId}") { backStackEntry ->
                            val citaId = backStackEntry.arguments?.getString("citaId") ?: ""
                            DetailScreen(citaId = citaId)
                        }
                    }
                }
            }
        }
    }
}
