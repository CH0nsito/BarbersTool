package com.chon.agendabarberia.data.repository

import androidx.compose.runtime.mutableStateListOf
import com.chon.agendabarberia.domain.model.Barbero
import com.chon.agendabarberia.domain.model.Cita
import com.chon.agendabarberia.domain.model.Servicio

/**
 * Fuente de datos de la barbería. En una versión futura, esto se conectaría
 * a una API (data/remote) o a una base de datos local con Room (data/local).
 */
object BarberiaRepository {

    private val barberos = listOf(
        Barbero("b1", "Luis Ramírez", "Cortes clásicos"),
        Barbero("b2", "Ana Torres", "Fade y diseño"),
        Barbero("b3", "Jorge Medina", "Barba y afeitado")
    )

    private val servicios = listOf(
        Servicio("s1", "Corte clásico", 30, 120.0),
        Servicio("s2", "Corte + barba", 45, 180.0),
        Servicio("s3", "Fade / diseño", 40, 150.0),
        Servicio("s4", "Afeitado tradicional", 25, 100.0)
    )

    // Citas ya agendadas (estado observable por Compose)
    private val citas = mutableStateListOf(
        Cita("c1", "Miguel Soto", "b1", "s1", "10/09/2026", "10:00"),
        Cita("c2", "Karla Ibarra", "b2", "s3", "10/09/2026", "12:30")
    )

    fun getBarberos(): List<Barbero> = barberos
    fun getServicios(): List<Servicio> = servicios
    fun getCitas(): List<Cita> = citas
    fun getCitaById(id: String): Cita? = citas.find { it.id == id }
    fun getBarberoById(id: String): Barbero? = barberos.find { it.id == id }
    fun getServicioById(id: String): Servicio? = servicios.find { it.id == id }

    fun agendarCita(clienteNombre: String, barberoId: String, servicioId: String, fecha: String, hora: String): Cita {
        val nuevaCita = Cita(
            id = "c${citas.size + 1}",
            clienteNombre = clienteNombre,
            barberoId = barberoId,
            servicioId = servicioId,
            fecha = fecha,
            hora = hora
        )
        citas.add(nuevaCita)
        return nuevaCita
    }
}
