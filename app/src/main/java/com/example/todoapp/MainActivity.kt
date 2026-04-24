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
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class MainActivity : AppCompatActivity() {

    private val allTasks      = mutableListOf<Task>()
    private val filteredTasks = mutableListOf<Task>()
    private lateinit var adapter: TaskAdapter
    private var nextId       = 1
    private var activeFilter: Category? = null
    private var searchQuery  = ""

    private lateinit var filterViews: Map<Category?, TextView>
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var rootView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannel(this)
        requestNotificationPermission()
        requestIgnoreBatteryOptimization()

        val savedTasks = TaskStorage.loadTasks(this)
        allTasks.addAll(savedTasks)
        nextId = TaskStorage.loadNextId(this)

        rootView     = findViewById(android.R.id.content)
        recyclerView = findViewById(R.id.recyclerView)
        emptyState   = findViewById(R.id.emptyState)

        adapter = TaskAdapter(
            filteredTasks,
            onDelete = { task -> deleteTask(task) },
            onToggle = { task ->
                val index = allTasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    allTasks[index] = task.copy(isCompleted = !task.isCompleted)
                    TaskStorage.saveTasks(this, allTasks, nextId)
                    applyFilter()
                    updateSummary()
                }
            },
            onEdit = { task -> showEditTaskDialog(task) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        // ✅ Swipe to delete
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val task = filteredTasks[position]
                deleteTaskWithUndo(task)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)

        // Filter chips
        filterViews = mapOf(
            null              to findViewById(R.id.btnFilterAll),
            Category.PERSONAL to findViewById(R.id.btnFilterPersonal),
            Category.WORK     to findViewById(R.id.btnFilterWork),
            Category.SHOPPING to findViewById(R.id.btnFilterShopping),
            Category.OTHER    to findViewById(R.id.btnFilterOther)
        )
        filterViews.forEach { (cat, view) ->
            view.setOnClickListener { setFilter(cat) }
        }

        // ✅ Search bar
        findViewById<TextInputEditText>(R.id.etSearch).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString()?.trim() ?: ""
                    applyFilter()
                }
            }
        )

        findViewById<ExtendedFloatingActionButton>(R.id.btnAdd).setOnClickListener {
            showAddTaskDialog()
        }

        applyFilter()
        updateSummary()
    }

    private fun deleteTask(task: Task) {
        NotificationHelper.cancelReminder(this, task.id)
        allTasks.remove(task)
        TaskStorage.saveTasks(this, allTasks, nextId)
        applyFilter()
        updateSummary()
    }

    // ✅ Hapus dengan Undo snackbar
    private fun deleteTaskWithUndo(task: Task) {
        NotificationHelper.cancelReminder(this, task.id)
        allTasks.remove(task)
        TaskStorage.saveTasks(this, allTasks, nextId)
        applyFilter()
        updateSummary()

        Snackbar.make(rootView, getString(R.string.task_deleted), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.undo)) {
                // Restore task
                allTasks.add(task)
                if (task.reminderTimeMillis != null &&
                    task.reminderTimeMillis > System.currentTimeMillis()) {
                    NotificationHelper.scheduleReminder(this, task)
                }
                TaskStorage.saveTasks(this, allTasks, nextId)
                applyFilter()
                updateSummary()
            }
            .show()
    }

    private fun setFilter(category: Category?) {
        activeFilter = category
        filterViews.forEach { (cat, view) ->
            if (cat == category) {
                view.setBackgroundResource(R.drawable.bg_filter_active)
                view.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                view.setBackgroundResource(R.drawable.bg_filter_inactive)
                view.setTextColor(
                    ContextCompat.getColor(this,
                        com.google.android.material.R.color.m3_sys_color_dynamic_dark_on_surface)
                )
            }
        }
        applyFilter()
    }

    private fun applyFilter() {
        filteredTasks.clear()
        val source = if (activeFilter == null) allTasks
        else allTasks.filter { it.category == activeFilter }

        // Search filter
        val searched = if (searchQuery.isEmpty()) source
        else source.filter {
            it.title.contains(searchQuery, ignoreCase = true)
        }

        // Sort: belum selesai dulu, lalu by prioritas
        filteredTasks.addAll(
            searched.sortedWith(compareBy({ it.isCompleted }, { it.priority.ordinal }))
        )

        adapter.notifyDataSetChanged()

        // ✅ Toggle empty state
        if (filteredTasks.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility   = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility   = View.GONE
        }
    }

    private fun updateSummary() {
        val total     = allTasks.size
        val completed = allTasks.count { it.isCompleted }
        findViewById<TextView>(R.id.tvSummary).text = "$completed of $total tasks completed"
        val progress  = if (total == 0) 0 else (completed * 100 / total)
        findViewById<LinearProgressIndicator>(R.id.progressBar).setProgressCompat(progress, true)
    }

    private fun showAddTaskDialog() {
        val view            = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val etTitle         = view.findViewById<EditText>(R.id.etTaskTitle)
        val btnPickTime     = view.findViewById<Button>(R.id.btnPickTime)
        val tvSelectedTime  = view.findViewById<TextView>(R.id.tvSelectedTime)
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
                    updateSummary()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditTaskDialog(task: Task) {
        val view            = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val etTitle         = view.findViewById<EditText>(R.id.etTaskTitle)
        val btnPickTime     = view.findViewById<Button>(R.id.btnPickTime)
        val tvSelectedTime  = view.findViewById<TextView>(R.id.tvSelectedTime)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerCategory)
        var selectedTimeMs: Long? = task.reminderTimeMillis

        setupSpinners(spinnerPriority, spinnerCategory)
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
                        updateSummary()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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

    private fun setupSpinners(spinnerPriority: Spinner, spinnerCategory: Spinner) {
        ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("🔴 High", "🟡 Medium", "🟢 Low")
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPriority.adapter = it
            spinnerPriority.setSelection(1)
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