package com.lurkki14.repotuli

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StationAdapter : RecyclerView.Adapter<StationAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = Station.AllStations[position]
        // TODO: localize station name
        holder.textView.text = station.name
        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, StationActivity::class.java)
            intent.putExtra("station", station)
            it.context.startActivity(intent)
        }
    }

    override fun getItemCount() = Station.AllStations.size
}
