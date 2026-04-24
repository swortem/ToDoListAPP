package com.example.todoapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TaskStorage {

    private const val PREF_NAME = "todo_prefs"
    private const val KEY_TASKS = "tasks"
    private const val KEY_NEXT_ID = "next_id"
    private val gson = Gson()

    fun saveTasks(context: Context, tasks: List<Task>, nextId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TASKS, gson.toJson(tasks))
            .putInt(KEY_NEXT_ID, nextId)
            .apply()
    }

    fun loadTasks(context: Context): List<Task> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        val type = object : TypeToken<List<Task>>() {}.type
        return gson.fromJson(json, type)
    }

    fun loadNextId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NEXT_ID, 1)
    }
}