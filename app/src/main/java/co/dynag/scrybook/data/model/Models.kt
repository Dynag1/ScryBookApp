package co.dynag.scrybook.data.model

data class Chapitre(
    val id: Long = 0,
    val nom: String = "",
    val numero: String = "",
    val resume: String = "",
    val contenuHtml: String = ""
)

data class Personnage(
    val id: Long = 0,
    val alias: String = "",
    val nom: String = "",
    val prenom: String = "",
    val sexe: String = "",
    val age: Int = 0,
    val descPhys: String = "",
    val descGlobal: String = "",
    val skill: String = ""
)

data class Lieu(
    val id: Long = 0,
    val nom: String = "",
    val desc: String = ""
)

data class Info(
    val id: Long = 1,
    val titre: String = "",
    val stitre: String = "",
    val auteur: String = "",
    val date: String = "",
    val resume: String = "",
    val couverture: String = ""
)

data class Param(
    val id: Long = 1,
    val police: String = "serif",
    val taille: String = "16",
    val saveTime: String = "30",
    val langue: String = "system",
    val theme: String = "system",
    val format: String = "A4"
)

data class ProjectFile(
    val name: String,
    val path: String,
    val lastModified: Long,
    val originalUri: String? = null
)
