package com.example.todoapp

import java.io.Serializable
enum class Priority { HIGH, MEDIUM, LOW }
enum class Category { PERSONAL, WORK, SHOPPING, OTHER }
data class Task(
    val id: Int,
    val title: String,
    var isCompleted: Boolean = false,
    val reminderTimeMillis: Long? = null,  // null = no reminder
    val priority: Priority = Priority.MEDIUM,
    val category: Category = Category.OTHER
) : Serializable