package com.example.safetrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val posicoesUsadas = mutableListOf<LatLng>()
    private val categorias = listOf("Todos", "Elétrico", "Químico", "Infraestrutura")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)
        firestore = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val spinnerFiltro = findViewById<Spinner>(R.id.spinnerFiltro)
        val adapter = object : ArrayAdapter<String>(this, 0, categorias) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.spinner_filtro, parent, false)
                val spinnerText = view.findViewById<TextView>(R.id.spinnerText)
                spinnerText.text = "Classificação: ${getItem(position)}"
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)
                (view as TextView).text = getItem(position)
                return view
            }
        }

        spinnerFiltro.adapter = adapter

        // Listener do spinner para atualizar filtro quando selecionado
        spinnerFiltro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val categoriaSelecionada = categorias[position]
                // Só carrega se o mapa já estiver pronto
                if (::mMap.isInitialized) {
                    carregarIncidentesDoFirestore(categoriaSelecionada)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val btnIrRelatorios = findViewById<Button>(R.id.btnIrRelatorios)
        btnIrRelatorios.setOnClickListener {
            val intent = Intent(this, Relatorio::class.java)
            startActivity(intent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.custom_info_window, null)

                val tvCategoria = view.findViewById<TextView>(R.id.tvCategoria)
                val tvDescricao = view.findViewById<TextView>(R.id.tvDescricao)
                val tvData = view.findViewById<TextView>(R.id.tvData)

                tvCategoria.text = marker.title ?: "Sem categoria"
                tvDescricao.text = marker.snippet ?: "Sem descrição"
                tvData.text = marker.tag as? String ?: "Data não disponível"

                return view
            }
        })

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        mMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val posicaoAtual = LatLng(location.latitude, location.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posicaoAtual, 15f))
            } else {
                Toast.makeText(this, "Localização atual não disponível.", Toast.LENGTH_SHORT).show()
                val fallback = LatLng(-22.8337506, -47.0518788)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback, 15f))
            }
        }

        // Carrega os incidentes já com filtro "Todos" quando o mapa estiver pronto
        carregarIncidentesDoFirestore("Todos")
    }

    private fun carregarIncidentesDoFirestore(filtroCategoria: String = "Todos") {
        mMap.clear()
        posicoesUsadas.clear()

        firestore.collection("usuarios").get()
            .addOnSuccessListener { usuarios ->
                for (usuario in usuarios) {
                    val idUsuario = usuario.id
                    val incidentesRef = firestore.collection("usuarios").document(idUsuario).collection("incidentes")

                    incidentesRef.get()
                        .addOnSuccessListener { incidentes ->
                            for (incidente in incidentes) {
                                val categoria = incidente.getString("categoria")
                                val descricao = incidente.getString("descricao")
                                val localizacao = incidente.getGeoPoint("localizacao")

                                if (categoria != null && localizacao != null &&
                                    (filtroCategoria == "Todos" || categoria.equals(filtroCategoria, ignoreCase = true))) {

                                    val posicaoOriginal = LatLng(localizacao.latitude, localizacao.longitude)
                                    val posicao = aplicarDeslocamentoSeNecessario(posicaoOriginal)

                                    val cor = when (categoria.lowercase()) {
                                        "elétrico" -> BitmapDescriptorFactory.HUE_RED
                                        "químico" -> BitmapDescriptorFactory.HUE_YELLOW
                                        "infraestrutura" -> BitmapDescriptorFactory.HUE_GREEN
                                        else -> BitmapDescriptorFactory.HUE_BLUE
                                    }

                                    val localizacoesRef = firestore.collection("usuarios")
                                        .document(idUsuario).collection("localizacoes")

                                    localizacoesRef.limit(1).get()
                                        .addOnSuccessListener { localizacoes ->
                                            val dataRegistro = if (!localizacoes.isEmpty) {
                                                val timestamp = localizacoes.first().getString("timestamp")
                                                timestamp?.split(" ")?.firstOrNull() ?: "Data indisponível"
                                            } else {
                                                "Data indisponível"
                                            }

                                            val marker = mMap.addMarker(
                                                MarkerOptions()
                                                    .position(posicao)
                                                    .title("Risco: $categoria")
                                                    .snippet(descricao?.take(80) ?: "Sem descrição")
                                                    .icon(BitmapDescriptorFactory.defaultMarker(cor))
                                            )
                                            marker?.tag = dataRegistro
                                        }
                                        .addOnFailureListener {
                                            Log.e("Firestore", "Erro ao buscar data de localização: ", it)
                                        }
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e("Firestore", "Erro ao buscar incidentes: ", it)
                        }
                }
            }
            .addOnFailureListener {
                Log.e("Firestore", "Erro ao buscar usuários", it)
            }
    }

    private fun aplicarDeslocamentoSeNecessario(posicaoOriginal: LatLng): LatLng {
        var novaPosicao = posicaoOriginal
        val deslocamento = 0.00005
        var tentativas = 0

        while (posicoesUsadas.any { pos ->
                val diffLat = Math.abs(pos.latitude - novaPosicao.latitude)
                val diffLng = Math.abs(pos.longitude - novaPosicao.longitude)
                diffLat < 0.00001 && diffLng < 0.00001
            }) {
            novaPosicao = LatLng(
                posicaoOriginal.latitude + deslocamento * tentativas,
                posicaoOriginal.longitude + deslocamento * tentativas
            )
            tentativas++
            if (tentativas > 10) break
        }

        posicoesUsadas.add(novaPosicao)
        return novaPosicao
    }
}