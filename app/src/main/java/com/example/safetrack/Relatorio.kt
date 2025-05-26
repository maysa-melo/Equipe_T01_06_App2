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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Relatorio : AppCompatActivity() {

    private lateinit var dtaInicio: EditText
    private lateinit var dtaFinal: EditText
    private lateinit var spnCategoria: Spinner
    private lateinit var btnGerarRelatorio: Button
    private lateinit var ViewRelatorio: RecyclerView
    private lateinit var firestore: FirebaseFirestore

    private val db = FirebaseFirestore.getInstance()
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
        val categorias = listOf("Elétrico", "Químico", "Infraestrutura")
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
            }else {
                dtaFinal.setText(formatado)
                // Adiciona 23:59:59 para pegar o fim do dia
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                dataFim = Timestamp(cal.time)
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun buscarIncidentes() {
            val categoriaSelecionada = spnCategoria.selectedItem.toString()

            firestore.collection("usuários")
                .get()
                    .addOnSuccessListener { usuarios ->
                        for (usuario in usuarios) {
                            val idUsuario = usuario.id

                            firestore.collection("usuarios")
                                .document(idUsuario)
                                .collection("incidentes")
                                .get()
                                .addOnSuccessListener { incidentes ->
                                    listaIncidentes.clear()
                                    for (doc in incidentes) {
                                        val categoria = doc.getString("categoria") ?: ""
                                        val descricao = doc.getString("descricao") ?: ""
                                        val localizacao = doc.getString("localizacao") ?: ""
                                        val data = doc.getTimestamp("data") ?: continue

                                        // Filtra pela data
                                        val dentroDoPeriodo = dataInicio?.let { data >= it } ?: true &&
                                                dataFim?.let { data <= it } ?: true

                                        // Filtra pela categoria
                                        val categoriaCorreta = categoriaSelecionada == "Todos" || categoria == categoriaSelecionada

                                        if (dentroDoPeriodo && categoriaCorreta) {
                                            listaIncidentes.add(Incidente(categoria, descricao, localizacao, data))
                                        }
                                    }
                                    adapter.notifyDataSetChanged()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Erro ao buscar dados", Toast.LENGTH_SHORT).show()
                                }
            }


        }
    }
}