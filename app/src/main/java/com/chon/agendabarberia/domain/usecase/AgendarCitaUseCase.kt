package com.chon.agendabarberia.domain.usecase

import com.chon.agendabarberia.data.repository.BarberiaRepository
import com.chon.agendabarberia.domain.model.Cita

class AgendarCitaUseCase {
    operator fun invoke(
        clienteNombre: String,
        barberoId: String,
        servicioId: String,
        fecha: String,
        hora: String
    ): Cita = BarberiaRepository.agendarCita(clienteNombre, barberoId, servicioId, fecha, hora)
}
