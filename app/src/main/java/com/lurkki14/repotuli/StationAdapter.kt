package com.lurkki14.repotuli

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StationAdapter() : RecyclerView.Adapter<StationAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = stations[position]
        // TODO: localize station name
        holder.textView.text = station.name
        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, StationActivity::class.java)
            intent.putExtra("station", station)
            it.context.startActivity(intent)
        }
    }

    override fun getItemCount() = stations.size

    private val stations = listOf(
        Station("KEV", "Kevo", 55, 165),
        Station("KIL", "Kilpisjärvi", 61, 210),
        Station("IVA", "Ivalo", 72, 275),
        Station("MUO", "Muonio", 75, 300),
        Station("SOD", "Sodankylä", 74, 290),
        Station("PEL", "Pello", 73, 285),
        Station("RAN", "Ranua", 70, 240),
        Station("OUJ", "Oulujärvi", 68, 200),
        Station("MEK", "Mekrijärvi", 64, 150),
        Station("HAN", "Hankasalmi", 63, 140),
        Station("NUR", "Nurmijärvi", 60, 120),
        Station("TAR", "Tartto", 55, 100)
    )
}
