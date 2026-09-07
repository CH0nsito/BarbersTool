package com.chon.agendabarberia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.chon.agendabarberia.data.repository.BarberiaRepository
import com.chon.agendabarberia.domain.usecase.GetCitasUseCase

@Composable
fun HomeScreen(navController: NavController, getCitasUseCase: GetCitasUseCase = GetCitasUseCase()) {
    val citas = getCitasUseCase()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AgendaBarbería", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Citas agendadas", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        if (citas.isEmpty()) {
            Text("Aún no hay citas agendadas.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(citas) { cita ->
                    val barbero = BarberiaRepository.getBarberoById(cita.barberoId)
                    val servicio = BarberiaRepository.getServicioById(cita.servicioId)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(cita.clienteNombre, style = MaterialTheme.typography.titleMedium)
                            Text("${servicio?.nombre ?: "Servicio"} con ${barbero?.nombre ?: "barbero"}")
                            Text("${cita.fecha} · ${cita.hora}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { navController.navigate("detalle/${cita.id}") }) {
                                Text("Ver detalle")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { navController.navigate("nuevaCita") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Agendar nueva cita")
        }
    }
}
