package com.example.todoapp

import java.io.Serializable

data class Task(
    val id: Int,
    val title: String,
    var isCompleted: Boolean = false,
    val reminderTimeMillis: Long? = null   // null = no reminder
) : Serializable