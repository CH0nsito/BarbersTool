package com.chon.agendabarberia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.chon.agendabarberia.data.repository.BarberiaRepository
import com.chon.agendabarberia.domain.usecase.AgendarCitaUseCase

@Composable
fun NuevaCitaScreen(navController: NavController, agendarCitaUseCase: AgendarCitaUseCase = AgendarCitaUseCase()) {
    val barberos = BarberiaRepository.getBarberos()
    val servicios = BarberiaRepository.getServicios()

    var clienteNombre by remember { mutableStateOf("") }
    var barberoSeleccionado by remember { mutableStateOf(barberos.first()) }
    var servicioSeleccionado by remember { mutableStateOf(servicios.first()) }
    var fecha by remember { mutableStateOf("") }
    var hora by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Agendar nueva cita", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = clienteNombre,
            onValueChange = { clienteNombre = it },
            label = { Text("Nombre del cliente") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Barbero", style = MaterialTheme.typography.labelLarge)
        barberos.forEach { barbero ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = barberoSeleccionado.id == barbero.id,
                    onClick = { barberoSeleccionado = barbero }
                )
                Text("${barbero.nombre} (${barbero.especialidad})")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("Servicio", style = MaterialTheme.typography.labelLarge)
        servicios.forEach { servicio ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = servicioSeleccionado.id == servicio.id,
                    onClick = { servicioSeleccionado = servicio }
                )
                Text("${servicio.nombre} · ${servicio.duracionMin} min · $${servicio.precio}")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = fecha,
            onValueChange = { fecha = it },
            label = { Text("Fecha (dd/MM/yyyy)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hora,
            onValueChange = { hora = it },
            label = { Text("Hora (HH:mm)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (clienteNombre.isNotBlank() && fecha.isNotBlank() && hora.isNotBlank()) {
                    agendarCitaUseCase(
                        clienteNombre = clienteNombre,
                        barberoId = barberoSeleccionado.id,
                        servicioId = servicioSeleccionado.id,
                        fecha = fecha,
                        hora = hora
                    )
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirmar cita")
        }
    }
}
