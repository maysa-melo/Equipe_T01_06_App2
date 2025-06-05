package com.example.safetrack
import com.google.firebase.Timestamp

data class Incidente(
    val categoria: String,
    val descricao: String,
    val localizacao: String,
    val data: Timestamp?,
    var dataTexto: String = ""
)

