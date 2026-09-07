package com.chon.agendabarberia.domain.usecase

import com.chon.agendabarberia.data.repository.BarberiaRepository
import com.chon.agendabarberia.domain.model.Cita

class GetCitasUseCase {
    operator fun invoke(): List<Cita> = BarberiaRepository.getCitas()
}
