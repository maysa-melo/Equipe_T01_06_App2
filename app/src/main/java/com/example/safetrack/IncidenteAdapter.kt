package com.example.safetrack
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*


class IncidenteAdapter(private val lista: List<Incidente>) : RecyclerView.Adapter<IncidenteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoria: TextView = view.findViewById(R.id.itemCategoria)
        val descricao: TextView = view.findViewById(R.id.itemDescricao)
        val data: TextView = view.findViewById(R.id.itemData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incidente, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.categoria.text = item.categoria
        holder.descricao.text = item.descricao
        holder.data.text = item.dataTexto ?: "Data indisponível"
    }
}
