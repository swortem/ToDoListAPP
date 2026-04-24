package com.example.todoapp

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
    private val onToggle: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox:  CheckBox    = view.findViewById(R.id.checkboxDone)
        val title:     TextView    = view.findViewById(R.id.tvTaskTitle)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val tvReminder: TextView   = view.findViewById(R.id.tvReminder)
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

        // Strike-through completed tasks
        holder.title.paintFlags = if (task.isCompleted)
            holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else
            holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        // Reminder badge
        if (task.reminderTimeMillis != null) {
            holder.tvReminder.visibility = View.VISIBLE
            val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            holder.tvReminder.text = "⏰ ${fmt.format(java.util.Date(task.reminderTimeMillis))}"
        } else {
            holder.tvReminder.visibility = View.GONE
        }

        holder.checkbox.setOnClickListener { onToggle(task) }
        holder.btnDelete.setOnClickListener { onDelete(task) }
    }

    override fun getItemCount() = tasks.size
}