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
import com.google.android.material.button.MaterialButton
import java.util.*

class MainActivity : AppCompatActivity() {

    private val allTasks = mutableListOf<Task>()
    private val filteredTasks = mutableListOf<Task>()
    private lateinit var adapter: TaskAdapter
    private var nextId = 1
    private var activeFilter: Category? = null  // null = All

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannel(this)
        requestNotificationPermission()
        requestIgnoreBatteryOptimization()

        val savedTasks = TaskStorage.loadTasks(this)
        allTasks.addAll(savedTasks)
        nextId = TaskStorage.loadNextId(this)

        adapter = TaskAdapter(
            filteredTasks,
            onDelete = { task ->
                NotificationHelper.cancelReminder(this, task.id)
                allTasks.remove(task)
                TaskStorage.saveTasks(this, allTasks, nextId)
                applyFilter()
            },
            onToggle = { task ->
                val index = allTasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    allTasks[index] = task.copy(isCompleted = !task.isCompleted)
                    TaskStorage.saveTasks(this, allTasks, nextId)
                    applyFilter()
                }
            },
            onEdit = { task -> showEditTaskDialog(task) }
        )

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        // Filter buttons
        findViewById<Button>(R.id.btnFilterAll).setOnClickListener      { setFilter(null) }
        findViewById<Button>(R.id.btnFilterPersonal).setOnClickListener { setFilter(Category.PERSONAL) }
        findViewById<Button>(R.id.btnFilterWork).setOnClickListener     { setFilter(Category.WORK) }
        findViewById<Button>(R.id.btnFilterShopping).setOnClickListener { setFilter(Category.SHOPPING) }
        findViewById<Button>(R.id.btnFilterOther).setOnClickListener    { setFilter(Category.OTHER) }

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            showAddTaskDialog()
        }

        applyFilter()
    }

    private fun setFilter(category: Category?) {
        activeFilter = category
        applyFilter()
    }

    private fun applyFilter() {
        filteredTasks.clear()
        filteredTasks.addAll(
            if (activeFilter == null) allTasks
            else allTasks.filter { it.category == activeFilter }
        )
        adapter.notifyDataSetChanged()
    }

    private fun showAddTaskDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val etTitle        = view.findViewById<EditText>(R.id.etTaskTitle)
        val btnPickTime    = view.findViewById<Button>(R.id.btnPickTime)
        val tvSelectedTime = view.findViewById<TextView>(R.id.tvSelectedTime)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerCategory)
        var selectedTimeMs: Long? = null

        setupSpinners(spinnerPriority, spinnerCategory)

        btnPickTime.setOnClickListener {
            pickDateTime { timeMs, label ->
                selectedTimeMs = timeMs
                tvSelectedTime.text = "Reminder: $label"
            }
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
                    val priority = Priority.values()[spinnerPriority.selectedItemPosition]
                    val category = Category.values()[spinnerCategory.selectedItemPosition]
                    val task = Task(nextId++, title,
                        reminderTimeMillis = selectedTimeMs,
                        priority = priority,
                        category = category)
                    allTasks.add(task)
                    TaskStorage.saveTasks(this, allTasks, nextId)
                    if (selectedTimeMs != null) NotificationHelper.scheduleReminder(this, task)
                    applyFilter()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditTaskDialog(task: Task) {
        val view = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val etTitle         = view.findViewById<EditText>(R.id.etTaskTitle)
        val btnPickTime     = view.findViewById<Button>(R.id.btnPickTime)
        val tvSelectedTime  = view.findViewById<TextView>(R.id.tvSelectedTime)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerCategory)
        var selectedTimeMs: Long? = task.reminderTimeMillis

        setupSpinners(spinnerPriority, spinnerCategory)

        // Pre-fill
        etTitle.setText(task.title)
        spinnerPriority.setSelection(task.priority.ordinal)
        spinnerCategory.setSelection(task.category.ordinal)
        if (task.reminderTimeMillis != null) {
            val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            tvSelectedTime.text = "Reminder: ${fmt.format(java.util.Date(task.reminderTimeMillis))}"
        }

        btnPickTime.setOnClickListener {
            pickDateTime { timeMs, label ->
                selectedTimeMs = timeMs
                tvSelectedTime.text = "Reminder: $label"
            }
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
                    NotificationHelper.cancelReminder(this, task.id)
                    val index = allTasks.indexOfFirst { it.id == task.id }
                    if (index != -1) {
                        val priority = Priority.values()[spinnerPriority.selectedItemPosition]
                        val category = Category.values()[spinnerCategory.selectedItemPosition]
                        allTasks[index] = task.copy(
                            title = newTitle,
                            reminderTimeMillis = selectedTimeMs,
                            priority = priority,
                            category = category
                        )
                        if (selectedTimeMs != null) NotificationHelper.scheduleReminder(this, allTasks[index])
                        TaskStorage.saveTasks(this, allTasks, nextId)
                        applyFilter()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Helper: reusable date-time picker
    private fun pickDateTime(onPicked: (Long, String) -> Unit) {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val cal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                onPicked(cal.timeInMillis, fmt.format(cal.time))
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    // Helper: setup spinner prioritas & kategori
    private fun setupSpinners(spinnerPriority: Spinner, spinnerCategory: Spinner) {
        ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("🔴 High", "🟡 Medium", "🟢 Low")
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPriority.adapter = it
            spinnerPriority.setSelection(1) // default Medium
        }

        ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("👤 Personal", "💼 Work", "🛒 Shopping", "📦 Other")
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = it
        }
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
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}