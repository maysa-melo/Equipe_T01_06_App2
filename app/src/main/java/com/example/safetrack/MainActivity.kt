package com.example.safetrack

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa Firebase
        FirebaseApp.initializeApp(this)
        firestore = FirebaseFirestore.getInstance()

        // Inicializa o mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Posição inicial (ajuste conforme sua preferência)
        val posicaoInicial = LatLng(-22.8337506, -47.0518788)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posicaoInicial, 15f))

        // Carrega os dados do Firestore
        carregarIncidentesDoFirestore()
    }

    private fun carregarIncidentesDoFirestore() {
        firestore.collection("usuários")
            .get()
            .addOnSuccessListener { usuarios ->
                for (usuario in usuarios) {
                    val idUsuario = usuario.id

                    firestore.collection("usuários")
                        .document(idUsuario)
                        .collection("incidentes")
                        .get()
                        .addOnSuccessListener { incidentes ->
                            for (incidente in incidentes) {
                                val categoria = incidente.getString("categoria")
                                val descricao = incidente.getString("descricao")
                                val localizacao = incidente.getGeoPoint("localizacao")

                                if (categoria != null && localizacao != null) {
                                    val posicao = LatLng(localizacao.latitude, localizacao.longitude)
                                    val cor = when (categoria.lowercase()) {
                                        "elétrico" -> BitmapDescriptorFactory.HUE_RED
                                        "químico" -> BitmapDescriptorFactory.HUE_YELLOW
                                        "infraestrutura" -> BitmapDescriptorFactory.HUE_GREEN
                                        else -> BitmapDescriptorFactory.HUE_BLUE
                                    }

                                    mMap.addMarker(
                                        MarkerOptions()
                                            .position(posicao)
                                            .title("Risco: $categoria")
                                            .snippet(descricao ?: "")
                                            .icon(BitmapDescriptorFactory.defaultMarker(cor))
                                    )
                                }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Erro ao buscar incidentes", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao buscar usuários", Toast.LENGTH_SHORT).show()
            }
    }
}
