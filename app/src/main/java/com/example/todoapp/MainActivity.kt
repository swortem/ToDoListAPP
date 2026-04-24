package com.example.todoapp

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
            },
            onEdit = { task ->
                showEditTaskDialog(task)
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
                    if (selectedTimeMs != null && selectedTimeMs!! <= System.currentTimeMillis()) {
                        Toast.makeText(this, "Reminder time must be in the future!", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
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
    private fun showEditTaskDialog(task: Task) {
        val view = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val etTitle        = view.findViewById<EditText>(R.id.etTaskTitle)
        val btnPickTime    = view.findViewById<Button>(R.id.btnPickTime)
        val tvSelectedTime = view.findViewById<TextView>(R.id.tvSelectedTime)

        // Pre-fill data lama
        etTitle.setText(task.title)
        var selectedTimeMs: Long? = task.reminderTimeMillis
        if (task.reminderTimeMillis != null) {
            val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            tvSelectedTime.text = "Reminder: ${fmt.format(java.util.Date(task.reminderTimeMillis))}"
        }

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
            .setTitle("Edit Task")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    if (selectedTimeMs != null && selectedTimeMs!! <= System.currentTimeMillis()) {
                        Toast.makeText(this, "Reminder time must be in the future!", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    // Cancel alarm lama dulu
                    NotificationHelper.cancelReminder(this, task.id)

                    // Update task di list (cari by id)
                    val index = tasks.indexOfFirst { it.id == task.id }
                    if (index != -1) {
                        tasks[index] = task.copy(
                            title = newTitle,
                            reminderTimeMillis = selectedTimeMs
                        )
                        // Schedule alarm baru kalau ada reminder
                        if (selectedTimeMs != null) {
                            NotificationHelper.scheduleReminder(this, tasks[index])
                        }
                        adapter.notifyItemChanged(index)
                        TaskStorage.saveTasks(this, tasks, nextId)
                    }
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
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}