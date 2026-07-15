package com.example.calories.model

data class ExercisePreset(
    val id: String,
    val name: String,
    val calories: Int,
    val durationMinutes: Int,
) {
    val subtitle: String
        get() = "${calories}kcal / ${durationMinutes} mins"
}

object StandardExercises {
    val presets: List<ExercisePreset> = listOf(
        ExercisePreset("std_slow_walk", "Slow walk", 54, 30),
        ExercisePreset("std_brisk_walk", "Brisk walk", 120, 30),
        ExercisePreset("std_jogging", "Jogging", 240, 30),
        ExercisePreset("std_cycling", "Cycling", 210, 30),
        ExercisePreset("std_swimming", "Swimming", 250, 30),
        ExercisePreset("std_yoga", "Yoga", 90, 30),
        ExercisePreset("std_strength", "Strength training", 180, 30),
        ExercisePreset("std_hiit", "HIIT", 300, 30),
        ExercisePreset("std_stairs", "Stair climbing", 160, 20),
        ExercisePreset("std_stretch", "Stretching", 40, 20),
    )
}
