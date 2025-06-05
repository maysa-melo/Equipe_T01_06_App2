package com.example.safetrack

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import android.app.DatePickerDialog
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Relatorio : AppCompatActivity() {

    private lateinit var dtaInicio: EditText
    private lateinit var dtaFinal: EditText
    private lateinit var spnCategoria: Spinner
    private lateinit var btnGerarRelatorio: Button
    private lateinit var ViewRelatorio: RecyclerView
    private lateinit var firestore: FirebaseFirestore

    private val listaIncidentes = mutableListOf<Incidente>()
    private lateinit var adapter: IncidenteAdapter

    private var dataInicio: Timestamp? = null
    private var dataFim: Timestamp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_relatorio)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializa Firebase
        FirebaseApp.initializeApp(this)
        firestore = FirebaseFirestore.getInstance()

        // Inicializações
        dtaInicio = findViewById(R.id.dtaInicio)
        dtaFinal = findViewById(R.id.dtaFim)
        spnCategoria = findViewById(R.id.spnCategoria)
        btnGerarRelatorio = findViewById(R.id.btnGerarRelatorio)
        ViewRelatorio = findViewById(R.id.ViewRelatorio)

        // Configurar RecyclerView
        adapter = IncidenteAdapter(listaIncidentes)
        ViewRelatorio.layoutManager = LinearLayoutManager(this)
        ViewRelatorio.adapter = adapter

        // Datas
        dtaInicio.setOnClickListener { mostrarDatePicker(true) }
        dtaFinal.setOnClickListener { mostrarDatePicker(false) }

        // Categorias do Spinner
        val categorias = listOf("Todos", "Elétrico", "Químico", "Infraestrutura")
        spnCategoria.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)

        btnGerarRelatorio.setOnClickListener {
            buscarIncidentes()
        }
    }

    private fun mostrarDatePicker(isInicio: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            val formatado = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
            if (isInicio) {
                dtaInicio.setText(formatado)
                dataInicio = Timestamp(cal.time)
            } else {
                dtaFinal.setText(formatado)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                dataFim = Timestamp(cal.time)
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun formatarData(timestampStr: String?): Date? {
        return try {
            val data = timestampStr?.split(" ")?.firstOrNull()
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(data ?: "")
        } catch (e: Exception) {
            null
        }
    }

    private fun buscarIncidentes() {
        val categoriaSelecionada = spnCategoria.selectedItem.toString()

        listaIncidentes.clear()
        adapter.notifyDataSetChanged()

        firestore.collection("usuarios")
            .get()
            .addOnSuccessListener { usuarios ->

                if (usuarios.isEmpty) {
                    Toast.makeText(this, "Nenhum dado encontrado", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                var usuariosProcessados = 0

                // Para controlar quantos incidentes processamos no total
                var totalIncidentesParaProcessar = 0
                var incidentesProcessados = 0

                for (usuario in usuarios) {
                    val idUsuario = usuario.id

                    firestore.collection("usuarios")
                        .document(idUsuario)
                        .collection("incidentes")
                        .get()
                        .addOnSuccessListener { incidentes ->

                            if (incidentes.isEmpty) {
                                // Se não tem incidentes, já conta esse usuário como processado
                                usuariosProcessados++
                                if (usuariosProcessados == usuarios.size()) {
                                    adapter.notifyDataSetChanged()
                                }
                                return@addOnSuccessListener
                            }

                            totalIncidentesParaProcessar += incidentes.size()

                            for (doc in incidentes) {
                                val categoria = doc.getString("categoria") ?: ""
                                val descricao = doc.getString("descricao") ?: ""
                                val geoPoint = doc.getGeoPoint("localizacao")
                                val localizacao =
                                    geoPoint?.let { "${it.latitude}, ${it.longitude}" }
                                        ?: "Localização indisponível"

                                firestore.collection("usuarios")
                                    .document(idUsuario)
                                    .collection("localizacoes")
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { localizacoes ->

                                        val timestampString =
                                            localizacoes.firstOrNull()?.getString("timestamp")

                                        val dataConvertida = formatarData(timestampString)
                                        val tsConvertida = dataConvertida?.let { Timestamp(it) }

                                        val dentroDoPeriodo =
                                            if (dataInicio != null || dataFim != null) {
                                                (dataInicio == null || (tsConvertida != null && tsConvertida.compareTo(dataInicio) >= 0)) &&
                                                        (dataFim == null || (tsConvertida != null && tsConvertida.compareTo(dataFim) <= 0))
                                            } else true

                                        val categoriaCorreta =
                                            categoriaSelecionada == "Todos" || categoria == categoriaSelecionada

                                        if (dentroDoPeriodo && categoriaCorreta) {
                                            val incidente =
                                                Incidente(categoria, descricao, localizacao, null)
                                            incidente.dataTexto = timestampString?.split(" ")?.firstOrNull() ?: "Data indisponível"
                                            listaIncidentes.add(incidente)
                                        }

                                        incidentesProcessados++

                                        // Só atualiza adapter quando todos incidentes de todos usuários foram processados
                                        if (usuariosProcessados == usuarios.size() && incidentesProcessados == totalIncidentesParaProcessar) {
                                            adapter.notifyDataSetChanged()
                                        }

                                    }
                                    .addOnFailureListener {

                                        incidentesProcessados++

                                        if (usuariosProcessados == usuarios.size() && incidentesProcessados == totalIncidentesParaProcessar) {
                                            adapter.notifyDataSetChanged()
                                        }
                                    }
                            }

                            // Aqui marca que usuário já teve seus incidentes buscados
                            usuariosProcessados++

                            // Se não houver incidentes, já atualiza o adapter
                            if (incidentes.isEmpty && usuariosProcessados == usuarios.size()) {
                                adapter.notifyDataSetChanged()
                            }

                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Erro ao buscar dados", Toast.LENGTH_SHORT).show()

                            usuariosProcessados++

                            if (usuariosProcessados == usuarios.size()) {
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao buscar usuários", Toast.LENGTH_SHORT).show()
            }
    }
}