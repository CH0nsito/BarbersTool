package com.chon.agendabarberia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chon.agendabarberia.data.repository.BarberiaRepository

@Composable
fun DetailScreen(citaId: String) {
    val cita = BarberiaRepository.getCitaById(citaId)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (cita != null) {
            val barbero = BarberiaRepository.getBarberoById(cita.barberoId)
            val servicio = BarberiaRepository.getServicioById(cita.servicioId)

            Text("Detalle de la cita", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Cliente: ${cita.clienteNombre}", style = MaterialTheme.typography.bodyLarge)
            Text("Barbero: ${barbero?.nombre ?: "-"} (${barbero?.especialidad ?: "-"})")
            Text("Servicio: ${servicio?.nombre ?: "-"}")
            Text("Duración: ${servicio?.duracionMin ?: 0} min")
            Text("Precio: $${servicio?.precio ?: 0.0}")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Fecha: ${cita.fecha}")
            Text("Hora: ${cita.hora}")
        } else {
            Text("Cita no encontrada")
        }
    }
}
