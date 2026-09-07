package com.chon.agendabarberia.domain.model

data class Cita(
    val id: String,
    val clienteNombre: String,
    val barberoId: String,
    val servicioId: String,
    val fecha: String,   // formato dd/MM/yyyy
    val hora: String     // formato HH:mm
)
