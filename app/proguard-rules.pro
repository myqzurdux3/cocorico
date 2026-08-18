# Règles R8 propres à Cocorico. Les règles par défaut d'AGP couvrent Compose,
# Room et les coroutines ; ce fichier ne traite que ce que R8 ne peut pas
# déduire tout seul.

# --- Enums dont les noms sont persistés ---
#
# `ChallengeId` et `Difficulty` sont écrits **en toutes lettres** sur le disque
# (DataStore pour la configuration, colonne `defi` pour l'historique) puis relus
# par `valueOf`. Si R8 renomme leurs constantes, la relecture échoue sur toutes
# les données déjà enregistrées : le défi choisi et l'historique repartent aux
# valeurs par défaut, en silence, chez quelqu'un qui met simplement à jour.
#
# Les règles par défaut d'AGP gardent `values()` et `valueOf()`, mais pas les
# noms des constantes elles-mêmes.
-keepclassmembers enum com.cocorico.data.ChallengeId { *; }
-keepclassmembers enum com.cocorico.data.Difficulty { *; }

# --- Entité Room ---
#
# Room génère du code qui construit `WakeRecord` par son constructeur et lit ses
# propriétés par leur nom ; le schéma versionné, lui, est figé.
-keep class com.cocorico.data.WakeRecord { *; }

# --- Composants déclarés au manifeste ---
#
# Android les instancie par réflexion à partir de leur nom de classe. Un
# renommage casse le démarrage de l'alarme sans la moindre trace à la
# compilation.
-keep class com.cocorico.alarm.AlarmReceiver
-keep class com.cocorico.alarm.BootReceiver
-keep class com.cocorico.alarm.AlarmService
-keep class com.cocorico.ui.MainActivity
-keep class com.cocorico.ui.AlarmActivity

# --- Traces ---
#
# Sans cette ligne, une trace d'exception remontée par un utilisateur est
# illisible. Le fichier de correspondance reste local, il n'est pas distribué.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
