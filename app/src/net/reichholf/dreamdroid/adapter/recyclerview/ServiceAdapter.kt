package net.reichholf.dreamdroid.adapter.recyclerview

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import net.reichholf.dreamdroid.helpers.enigma2.Service

/**
 * Created by Stephan on 14.05.2015.
 */
open class ServiceAdapter(
    protected var mContext: Context,
    protected var mData: ArrayList<ServiceNowNext>,
) : RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder>() {

    override fun getItemCount(): Int = mData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.service_list_item_nn, parent, false)
        itemView.isClickable = true
        itemView.isLongClickable = true
        return ServiceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = mData[position]
        val nextEvent = service.next
        val next = nextEvent?.title
        val hasNext = !next.isNullOrEmpty()

        val ref = service.serviceReference
        if (Service.isMarker(ref)) {
            holder.root.cardElevation = 0f
            holder.root.isClickable = false
            holder.parentService.visibility = View.GONE
            holder.parentMarker.visibility = View.VISIBLE
            holder.markerName.text = service.serviceName
            return
        }
        if (Service.isDirectory(ref)) {
            holder.parentService.visibility = View.VISIBLE
            holder.parentMarker.visibility = View.GONE
            holder.parentNow.visibility = View.GONE
            holder.parentNext.visibility = View.GONE
            holder.picon.visibility = View.GONE
            holder.progress.visibility = View.GONE
            holder.root.cardElevation =
                mContext.resources.getDimension(R.dimen.cardview_elevation)
            holder.root.isClickable = false
            holder.serviceName.text = service.serviceName
            return
        }
        holder.parentNow.visibility = View.VISIBLE

        Picon.setPiconForView(
            mContext,
            holder.picon,
            ref,
            service.serviceName,
            Statics.TAG_PICON,
            null,
        )
        holder.root.cardElevation =
            mContext.resources.getDimension(R.dimen.cardview_elevation)
        holder.root.isClickable = false
        holder.parentService.visibility = View.VISIBLE
        holder.parentMarker.visibility = View.GONE
        holder.serviceName.text = service.serviceName

        val now = service.now
        holder.eventNowTitle.text = now?.title
        holder.eventNowStart.text = now?.startTimeReadable
        holder.eventNowDuration.text = now?.durationReadable

        var max = -1L
        var cur = -1L

        if (now != null) {
            val nowTime = now.currentTime
            val duration = now.duration
            val start = now.start

            if (duration != null && start != null &&
                Python.NONE != duration && Python.NONE != start &&
                duration.isNotEmpty() && start.isNotEmpty()
            ) {
                try {
                    max = duration.toDouble().toLong() / 60
                    cur = max - DateTime.getRemaining(duration, start, nowTime)
                } catch (e: Exception) {
                    Log.e(DreamDroid.LOG_TAG, e.toString())
                }
            }
        }

        holder.progress.visibility = View.VISIBLE
        if (max > 0 && cur >= 0) {
            holder.progress.max = max.toInt()
            holder.progress.progress = cur.toInt()
        }

        if (hasNext) {
            holder.parentNext.visibility = View.VISIBLE
            holder.eventNextTitle.text = nextEvent!!.title
            holder.eventNextStart.text = nextEvent.startTimeReadable
            holder.eventNextDuration.text = nextEvent.durationReadable
        } else {
            holder.parentNext.visibility = View.GONE
        }
    }

    open inner class ServiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var root: MaterialCardView = itemView.findViewById(R.id.service_list_item_nn)
        var picon: ImageView = itemView.findViewById(R.id.picon)
        var progress: ProgressBar = itemView.findViewById(R.id.service_progress)
        var serviceName: TextView = itemView.findViewById(R.id.service_name)
        var eventNowTitle: TextView = itemView.findViewById(R.id.event_now_title)
        var eventNowStart: TextView = itemView.findViewById(R.id.event_now_start)
        var eventNowDuration: TextView = itemView.findViewById(R.id.event_now_duration)
        var eventNextTitle: TextView = itemView.findViewById(R.id.event_next_title)
        var eventNextStart: TextView = itemView.findViewById(R.id.event_next_start)
        var eventNextDuration: TextView = itemView.findViewById(R.id.event_next_duration)
        var markerName: TextView = itemView.findViewById(R.id.marker_name)
        var parentService: View = itemView.findViewById(R.id.parent_service)
        var parentNow: View = itemView.findViewById(R.id.event_now)
        var parentNext: View = itemView.findViewById(R.id.event_next)
        var parentMarker: View = itemView.findViewById(R.id.parent_marker)
    }
}
