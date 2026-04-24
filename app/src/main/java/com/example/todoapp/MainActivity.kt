package com.example.todoapp

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class MainActivity : AppCompatActivity() {

    private val tasks = mutableListOf<Task>()
    private lateinit var adapter: TaskAdapter
    private var nextId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannel(this)
        requestNotificationPermission()

        val savedTasks = TaskStorage.loadTasks(this)
        tasks.addAll(savedTasks)
        nextId = TaskStorage.loadNextId(this)

        adapter = TaskAdapter(tasks,
            onDelete = { task ->
                NotificationHelper.cancelReminder(this, task.id)
                tasks.remove(task)
                adapter.notifyDataSetChanged()
                TaskStorage.saveTasks(this, tasks, nextId)
            },
            onToggle = { task ->
                task.isCompleted = !task.isCompleted
                adapter.notifyDataSetChanged()
                TaskStorage.saveTasks(this, tasks, nextId)
            }
        )

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun showAddTaskDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val etTitle       = view.findViewById<EditText>(R.id.etTaskTitle)
        val btnPickTime   = view.findViewById<Button>(R.id.btnPickTime)
        val tvSelectedTime = view.findViewById<TextView>(R.id.tvSelectedTime)
        var selectedTimeMs: Long? = null

        btnPickTime.setOnClickListener {
            val now = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                TimePickerDialog(this, { _, h, min ->
                    val cal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                    selectedTimeMs = cal.timeInMillis
                    val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                    tvSelectedTime.text = "Reminder: ${fmt.format(cal.time)}"
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this)
            .setTitle("New Task")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    val task = Task(nextId++, title, reminderTimeMillis = selectedTimeMs)
                    tasks.add(task)
                    adapter.notifyDataSetChanged()
                    TaskStorage.saveTasks(this, tasks, nextId)
                    if (selectedTimeMs != null) NotificationHelper.scheduleReminder(this, task)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}