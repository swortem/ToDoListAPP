package com.example.todoapp

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val tasks: MutableList<Task>,
    private val onDelete: (Task) -> Unit,
    private val onToggle: (Task) -> Unit,
    private val onEdit:   (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox:      CheckBox    = view.findViewById(R.id.checkboxDone)
        val title:         TextView    = view.findViewById(R.id.tvTaskTitle)
        val btnDelete:     ImageButton = view.findViewById(R.id.btnDelete)
        val btnEdit:       ImageButton = view.findViewById(R.id.btnEdit)
        val tvReminder:    TextView    = view.findViewById(R.id.tvReminder)
        val tvCategory:    TextView    = view.findViewById(R.id.tvCategory)
        val tvPriority:    TextView    = view.findViewById(R.id.tvPriority)
        val priorityStrip: View        = view.findViewById(R.id.priorityStrip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        TaskViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task, parent, false)
        )

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        // Animasi fade-in tiap item
        val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.fade_in)
        holder.itemView.startAnimation(anim)

        holder.title.text = task.title
        holder.checkbox.isChecked = task.isCompleted

        holder.title.paintFlags = if (task.isCompleted)
            holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else
            holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        // Prioritas
        val stripColor: String
        val priorityLabel: String
        when (task.priority) {
            Priority.HIGH   -> { stripColor = "#B3261E"; priorityLabel = "🔴 High" }
            Priority.MEDIUM -> { stripColor = "#E8821A"; priorityLabel = "🟡 Medium" }
            Priority.LOW    -> { stripColor = "#386A20"; priorityLabel = "🟢 Low" }
        }
        holder.priorityStrip.setBackgroundColor(stripColor.toColorInt())
        holder.tvPriority.text = priorityLabel
        holder.tvPriority.backgroundTintList = ColorStateList.valueOf(stripColor.toColorInt())

        // Kategori
        val categoryColor: String
        val categoryLabel: String
        when (task.category) {
            Category.PERSONAL -> { categoryColor = "#1565C0"; categoryLabel = "👤 Personal" }
            Category.WORK     -> { categoryColor = "#6A1B9A"; categoryLabel = "💼 Work" }
            Category.SHOPPING -> { categoryColor = "#00695C"; categoryLabel = "🛒 Shopping" }
            Category.OTHER    -> { categoryColor = "#37474F"; categoryLabel = "📦 Other" }
        }
        holder.tvCategory.text = categoryLabel
        holder.tvCategory.backgroundTintList = ColorStateList.valueOf(categoryColor.toColorInt())

        // Reminder
        if (task.reminderTimeMillis != null) {
            holder.tvReminder.visibility = View.VISIBLE
            val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            holder.tvReminder.text = "⏰ ${fmt.format(java.util.Date(task.reminderTimeMillis))}"
        } else {
            holder.tvReminder.visibility = View.GONE
        }

        holder.checkbox.setOnClickListener { onToggle(task) }
        holder.btnDelete.setOnClickListener { onDelete(task) }
        holder.btnEdit.setOnClickListener   { onEdit(task) }
    }

    override fun getItemCount() = tasks.size
}