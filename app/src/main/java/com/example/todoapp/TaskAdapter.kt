package com.example.todoapp

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
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

        holder.title.text = task.title
        holder.checkbox.isChecked = task.isCompleted

        holder.title.paintFlags = if (task.isCompleted)
            holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else
            holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        // ✅ Warna strip & badge prioritas
        val stripColor: String
        val priorityLabel: String
        when (task.priority) {
            Priority.HIGH   -> { stripColor = "#F44336"; priorityLabel = "🔴 High" }
            Priority.MEDIUM -> { stripColor = "#FF9800"; priorityLabel = "🟡 Medium" }
            Priority.LOW    -> { stripColor = "#4CAF50"; priorityLabel = "🟢 Low" }
            else            -> { stripColor = "#888888"; priorityLabel = "Medium" }
        }
        holder.priorityStrip.setBackgroundColor(Color.parseColor(stripColor))
        holder.tvPriority.text = priorityLabel
        holder.tvPriority.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(stripColor))

        // ✅ Badge kategori
        val categoryColor: String
        val categoryLabel: String
        when (task.category) {
            Category.PERSONAL -> { categoryColor = "#2196F3"; categoryLabel = "👤 Personal" }
            Category.WORK     -> { categoryColor = "#9C27B0"; categoryLabel = "💼 Work" }
            Category.SHOPPING -> { categoryColor = "#009688"; categoryLabel = "🛒 Shopping" }
            Category.OTHER    -> { categoryColor = "#607D8B"; categoryLabel = "📦 Other" }
            else              -> { categoryColor = "#607D8B"; categoryLabel = "📦 Other" }
        }
        holder.tvCategory.text = categoryLabel
        holder.tvCategory.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(categoryColor))

        // Reminder
        if (task.reminderTimeMillis != null) {
            holder.tvReminder.visibility = View.VISIBLE
            val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            holder.tvReminder.text = "⏰ ${fmt.format(java.util.Date(task.reminderTimeMillis))}"
        } else {
            holder.tvReminder.visibility = View.GONE
        }

        holder.checkbox.setOnClickListener { onDelete(task) }
        holder.btnDelete.setOnClickListener { onDelete(task) }
        holder.btnEdit.setOnClickListener   { onEdit(task) }
    }

    override fun getItemCount() = tasks.size
}