#!/usr/bin/env python3
"""Génère les quatre sonneries de remplacement de Cocorico.

La machine de développement n'a ni accès réseau ni encodeur audio (pas de
ffmpeg, pas de sox) : impossible de suivre la consigne initiale « sourcer
quatre .ogg sur freesound.org ». Ce script synthétise donc quatre sonneries
avec numpy et le module `wave` de la bibliothèque standard, et les écrit
directement dans `app/src/main/res/raw/` au format WAV PCM 16 bits, mono,
22 050 Hz, 8 secondes.

Le nom de ressource Android ignore l'extension : `klaxon.wav` reste bien
`R.raw.klaxon`, donc `Sonneries.kt` n'a besoin d'aucun changement.

Le coq et le réveil-matin ont été resynthétisés le 18 août 2026 : le premier
ne ressemblait pas à un coq (trois sinusoïdes pures), le second sonnait comme
un buzzer et non comme un réveil à cloches. Les deux autres restent des
sonneries de remplacement sans prétention, à
substituer par de vrais enregistrements avant toute publication.

Dépend de `numpy`, seule dépendance externe du dépôt hors Gradle.

Usage : `python3 tools/generer_sonneries.py`
"""

from __future__ import annotations

import os
import wave

import numpy as np

SR = 22_050  # Hz
DUREE = 8.0  # secondes
N = int(SR * DUREE)
T = np.arange(N, dtype=np.float64) / SR

ICI = os.path.dirname(os.path.abspath(__file__))
DOSSIER_RAW = os.path.abspath(os.path.join(ICI, "..", "app", "src", "main", "res", "raw"))

# Amplitude plancher (RMS) et de crête en dessous desquelles on considère
# qu'un fichier n'est pas « réellement audible » — sert de garde-fou après
# génération, voir `verifier_audible`.
RMS_MIN = 1500.0  # sur une échelle int16 (max 32767)
CRETE_MIN = 8000.0


def onde(phase: np.ndarray, forme: str) -> np.ndarray:
    """Calcule une onde périodique à partir d'une phase en radians."""
    if forme == "sine":
        return np.sin(phase)
    if forme == "square":
        return np.sign(np.sin(phase))
    if forme == "saw":
        frac = (phase / (2 * np.pi)) % 1.0
        return 2.0 * frac - 1.0
    raise ValueError(f"forme inconnue : {forme}")


def ajouter_impulsion(
    buf: np.ndarray,
    t0: float,
    duree: float,
    f0: float,
    f1: float,
    amp: float,
    forme: str = "sine",
) -> None:
    """Ajoute une impulsion (glissando f0->f1) à `buf`, avec une enveloppe
    en cloche (sin(pi*x)) qui vaut zéro à ses deux bords : chaque impulsion
    démarre et finit sans claquement, quelle que soit la phase de l'onde
    porteuse."""
    idx = (T >= t0) & (T < t0 + duree)
    if not np.any(idx):
        return
    local = T[idx] - t0
    frac = local / duree
    # Intégrale de la rampe de fréquence linéaire f0->f1 : donne la phase
    # instantanée continue (pas de saut au changement de fréquence).
    phase = 2 * np.pi * (f0 * local + (f1 - f0) * local * local / (2 * duree))
    enveloppe = np.sin(np.pi * frac)
    buf[idx] += amp * enveloppe * onde(phase, forme)


def fondu_bords(buf: np.ndarray, ms: float = 8.0) -> np.ndarray:
    """Force les deux échantillons de bord à zéro par une courte rampe :
    garantit une boucle sans claquement (passage par zéro en tête et en
    queue) sans introduire de silence perceptible (8 ms sur 8 s)."""
    n = int(SR * ms / 1000)
    n = min(n, len(buf) // 4)
    rampe = np.linspace(0.0, 1.0, n)
    buf = buf.copy()
    buf[:n] *= rampe
    buf[-n:] *= rampe[::-1]
    return buf


def normaliser(buf: np.ndarray, crete: float) -> np.ndarray:
    """Ramène le pic d'amplitude à `crete` (évite l'écrêtage tout en restant fort)."""
    pic = np.max(np.abs(buf))
    if pic < 1e-9:
        return buf
    return buf * (crete / pic)


# --------------------------------------------------------------------------
# coq.wav — chant de coq. La version précédente n'était que trois glissandos
# sinusoïdaux : un sifflement propre, sans rapport avec un coq. Un cri de coq
# est **riche et râpeux**, pas pur. Trois ingrédients le font entendre :
#
#   1. un empilement d'harmoniques (une sinusoïde seule n'a pas de timbre) ;
#   2. un micro-tremblement de la hauteur, qui donne le grain « animal » ;
#   3. une modulation d'amplitude rapide et un souffle, qui donnent le côté
#      forcé, éraillé — c'est ce qui rend le cri agaçant plutôt que joli.
#
# La structure est celle d'un vrai « co-co-ri-cooo » : deux syllabes brèves,
# une montée, puis une longue tenue descendante et forcée.
# --------------------------------------------------------------------------
def lisser(x: np.ndarray, largeur: int) -> np.ndarray:
    """Moyenne glissante. Sert à fabriquer du bruit **lent** : un bruit blanc
    module la hauteur en un souffle inaudible, un bruit lissé la fait trembler
    comme une vraie voix."""
    largeur = max(1, min(largeur, len(x)))
    noyau = np.ones(largeur) / largeur
    return np.convolve(x, noyau, mode="same")


def enveloppe_cri(frac: np.ndarray, attaque: float) -> np.ndarray:
    """Attaque brutale puis décroissance, au lieu de la cloche symétrique de
    `ajouter_impulsion`. Un cri commence d'un coup ; une montée progressive
    l'adoucirait, et c'est exactement ce qu'on ne veut pas ici. Vaut zéro aux
    deux bords, donc pas de claquement."""
    attaque = max(attaque, 1e-4)
    montee = np.clip(frac / attaque, 0.0, 1.0)
    descente = np.clip((1.0 - frac) / (1.0 - attaque), 0.0, 1.0) ** 0.55
    return montee * descente


def cri_coq(
    buf: np.ndarray,
    t0: float,
    duree: float,
    f0: float,
    f1: float,
    amp: float,
    rng: np.random.Generator,
    rasp: float = 1.0,
    harmoniques: int = 16,
    attaque: float = 0.05,
) -> None:
    idx = (T >= t0) & (T < t0 + duree)
    if not np.any(idx):
        return
    local = T[idx] - t0
    n = local.size
    frac = local / duree

    # Hauteur : glissando, plus un tremblement lent de +/- 3 %. Sans lui, le
    # cri sonne synthétique quel que soit le reste.
    tremble = lisser(rng.standard_normal(n), max(2, int(SR * 0.012)))
    tremble /= max(float(np.max(np.abs(tremble))), 1e-9)
    freq = (f0 + (f1 - f0) * frac) * (1.0 + 0.03 * rasp * tremble)
    # Phase par intégration : la fréquence varie à chaque échantillon, une
    # multiplication directe produirait des sauts de phase donc des clics.
    phase = 2 * np.pi * np.cumsum(freq) / SR

    # Timbre : harmoniques décroissant lentement. Un coq crie « clair », donc
    # les aigus doivent rester présents — d'où l'exposant faible.
    sig = np.zeros(n)
    for k in range(1, harmoniques + 1):
        sig += np.sin(k * phase) / (k ** 0.62)
    sig /= max(float(np.max(np.abs(sig))), 1e-9)

    # Éraillement : modulation d'amplitude à 45 Hz — trop rapide pour être
    # entendue comme un rythme, assez pour être entendue comme une rugosité.
    rugosite = 1.0 - 0.45 * rasp * (0.5 + 0.5 * np.sin(2 * np.pi * 45 * local))
    # Souffle : bruit suivant l'amplitude du cri, jamais indépendant, sinon on
    # entend un sifflement de fond au lieu d'une voix.
    souffle = 0.22 * rasp * lisser(rng.standard_normal(n), 3) * np.abs(sig)

    buf[idx] += amp * enveloppe_cri(frac, attaque) * (sig * rugosite + souffle)


def generer_coq() -> np.ndarray:
    # Graine fixe : le fichier livré doit être reproductible d'une exécution à
    # l'autre, sinon la sonnerie change à chaque régénération du dépôt.
    rng = np.random.default_rng(20260818)
    buf = np.zeros(N)
    periode = DUREE / 2
    for i in range(2):
        base = i * periode + 0.20
        # « co- » « co- » : deux appels brefs et secs.
        cri_coq(buf, base + 0.00, 0.17, 780, 900, 0.55, rng, attaque=0.06)
        cri_coq(buf, base + 0.26, 0.17, 820, 960, 0.60, rng, attaque=0.06)
        # « -ri- » : la montée, le sommet du cri.
        cri_coq(buf, base + 0.52, 0.26, 900, 1350, 0.85, rng, attaque=0.05)
        # « -cooo » : la tenue forcée qui retombe. Deux fois plus longue que
        # tout le reste, et la plus râpeuse : c'est elle qu'on retient.
        cri_coq(buf, base + 0.84, 1.05, 1300, 620, 1.00, rng, rasp=1.35, attaque=0.03)
    return fondu_bords(normaliser(buf, 0.85))


# --------------------------------------------------------------------------
# reveil_matin.wav — réveil mécanique à deux cloches. La version précédente
# était une onde carrée à 2 800 Hz avec un trémolo : le bruit d'un buzzer
# électronique, pas d'un réveil à marteau. Ce qui fait entendre « réveil à
# cloches », c'est autre chose :
#
#   1. des partiels **inharmoniques** — une cloche n'est pas une corde, ses
#      résonances ne sont pas des multiples entiers de la fondamentale ;
#   2. une attaque en une milliseconde et une décroissance exponentielle,
#      chaque aigu mourant plus vite que le grave ;
#   3. deux cloches légèrement désaccordées, frappées **en alternance** par un
#      marteau — c'est le battement rapide gauche-droite qu'on reconnaît.
# --------------------------------------------------------------------------
PARTIELS_CLOCHE = (1.00, 2.76, 5.40, 8.93, 13.34)
GAINS_CLOCHE = (1.00, 0.62, 0.38, 0.24, 0.14)
# Les partiels aigus s'éteignent plus vite : c'est ce qui fait qu'une cloche
# « s'assombrit » en mourant au lieu de simplement baisser.
DECROISSANCES_CLOCHE = (1.0, 1.7, 2.4, 3.2, 4.2)


def frapper_cloche(
    buf: np.ndarray,
    t0: float,
    f0: float,
    amp: float,
    rng: np.random.Generator,
    duree: float = 0.22,
) -> None:
    idx = (T >= t0) & (T < t0 + duree)
    if not np.any(idx):
        return
    local = T[idx] - t0
    frac = local / duree

    son = np.zeros(local.size)
    for ratio, gain, vitesse in zip(PARTIELS_CLOCHE, GAINS_CLOCHE, DECROISSANCES_CLOCHE):
        son += gain * np.exp(-5.0 * vitesse * frac) * np.sin(2 * np.pi * f0 * ratio * local)

    # Le claquement du marteau sur le métal : très bref, sans lui la frappe
    # sonne « soufflée » et le rythme du mécanisme ne s'entend plus.
    claquement = rng.standard_normal(local.size) * np.exp(-450.0 * local)

    # Attaque d'une milliseconde : assez pour ne pas produire un clic
    # numérique, assez peu pour rester une frappe.
    attaque = np.clip(local / 0.001, 0.0, 1.0)
    buf[idx] += amp * attaque * (son + 0.30 * claquement)


def generer_reveil() -> np.ndarray:
    rng = np.random.default_rng(20260819)
    buf = np.zeros(N)
    # Deux cloches volontairement désaccordées : identiques, elles se
    # confondraient en un seul timbre et l'alternance deviendrait inaudible.
    cloches = (1_760.0, 2_030.0)
    frappes_par_seconde = 11.0
    periode = DUREE / 4
    for salve in range(4):
        base = salve * periode + 0.10
        duree_salve = 1.55
        nombre = int(duree_salve * frappes_par_seconde)
        for coup in range(nombre):
            t = base + coup / frappes_par_seconde
            # Le mécanisme n'est pas un métronome : quelques millisecondes
            # d'irrégularité, sinon le rythme sonne informatique.
            t += float(rng.normal(0.0, 0.004))
            # Le marteau tape moins fort en fin de salve, comme un ressort qui
            # se détend.
            attenuation = 1.0 - 0.25 * (coup / max(nombre - 1, 1))
            frapper_cloche(buf, t, cloches[coup % 2], 0.55 * attenuation, rng)
    return fondu_bords(normaliser(buf, 0.85))


# --------------------------------------------------------------------------
# klaxon.wav — klaxon répété, le défaut : deux tons proches en scie
# (battement dissonant), salves courtes et rapprochées.
# --------------------------------------------------------------------------
def generer_klaxon() -> np.ndarray:
    buf = np.zeros(N)
    periode = 0.55
    n_honks = int(DUREE / periode)
    for i in range(n_honks):
        base = i * periode
        duree_honk = 0.40
        if base + duree_honk > DUREE:
            duree_honk = DUREE - base
        if duree_honk <= 0:
            continue
        ajouter_impulsion(buf, base, duree_honk, 415, 415, 0.55, "saw")
        ajouter_impulsion(buf, base, duree_honk, 495, 495, 0.55, "saw")
    return fondu_bords(normaliser(buf, 0.9))


# --------------------------------------------------------------------------
# sirene.wav — sirène montante, la plus insupportable : balayage continu
# en dents de scie, sans salve ni pause, volume maximal.
# --------------------------------------------------------------------------
def generer_sirene() -> np.ndarray:
    freq = 950.0 + 450.0 * np.sin(2 * np.pi * 0.5 * T)  # balaye 500-1400 Hz, 2 s/cycle
    phase = 2 * np.pi * np.cumsum(freq) / SR
    buf = onde(phase, "saw")
    return fondu_bords(normaliser(buf, 0.95))


def verifier_audible(nom: str, pcm: np.ndarray) -> None:
    """Garde-fou : sans lecture possible sur cette machine, on vérifie au
    moins que le fichier n'est pas quasi-silencieux."""
    rms = float(np.sqrt(np.mean(pcm.astype(np.float64) ** 2)))
    crete = float(np.max(np.abs(pcm)))
    assert rms > RMS_MIN, f"{nom} : RMS trop faible ({rms:.0f} <= {RMS_MIN})"
    assert crete > CRETE_MIN, f"{nom} : crête trop faible ({crete:.0f} <= {CRETE_MIN})"


def verifier_bouclable(nom: str, pcm: np.ndarray) -> None:
    """Garde-fou : le premier et le dernier échantillon doivent être (quasi)
    nuls pour que la boucle ne claque pas."""
    assert abs(int(pcm[0])) < 50, f"{nom} : premier échantillon non nul ({pcm[0]})"
    assert abs(int(pcm[-1])) < 50, f"{nom} : dernier échantillon non nul ({pcm[-1]})"


def ecrire_wav(chemin: str, flottants: np.ndarray) -> np.ndarray:
    pcm = np.clip(flottants * 32767.0, -32768, 32767).astype(np.int16)
    with wave.open(chemin, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    return pcm


def main() -> None:
    os.makedirs(DOSSIER_RAW, exist_ok=True)
    sonneries = {
        "coq.wav": generer_coq,
        "reveil_matin.wav": generer_reveil,
        "klaxon.wav": generer_klaxon,
        "sirene.wav": generer_sirene,
    }
    for nom, fabrique in sonneries.items():
        flottants = fabrique()
        chemin = os.path.join(DOSSIER_RAW, nom)
        pcm = ecrire_wav(chemin, flottants)
        verifier_audible(nom, pcm)
        verifier_bouclable(nom, pcm)
        taille = os.path.getsize(chemin)
        print(f"{nom}: {taille} octets, RMS={np.sqrt(np.mean(pcm.astype(np.float64) ** 2)):.0f}, "
              f"crête={np.max(np.abs(pcm))}")
    print("OK : quatre sonneries générées dans", DOSSIER_RAW)


if __name__ == "__main__":
    main()
