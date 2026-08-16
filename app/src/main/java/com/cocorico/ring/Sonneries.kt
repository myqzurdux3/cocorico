package com.cocorico.ring

import com.cocorico.R

object Sonneries {

    data class Sonnerie(val id: String, val nom: String, val resId: Int)

    val toutes = listOf(
        Sonnerie("coq", "Coq du village", R.raw.coq),
        Sonnerie("reveil", "Réveil-matin", R.raw.reveil_matin),
        Sonnerie("klaxon", "Klaxon d'enfer", R.raw.klaxon),
        Sonnerie("sirene", "Sirène", R.raw.sirene),
    )

    fun parId(id: String): Sonnerie = toutes.firstOrNull { it.id == id } ?: toutes[2]
}
