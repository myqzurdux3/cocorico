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

Ce sont des sonneries de remplacement, sans prétention artistique : à
substituer par de vrais enregistrements avant toute publication (voir
`docs/sonneries-placeholder.md`).

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
# coq.wav — chant de coq stylisé, la moins violente : trois appels
# « cocorico » (glissandos sinusoïdaux doux) espacés sur 8 s.
# --------------------------------------------------------------------------
def generer_coq() -> np.ndarray:
    buf = np.zeros(N)
    periode = DUREE / 3
    for i in range(3):
        base = i * periode + 0.15
        # co-co-ri : deux syllabes courtes montantes, puis -co : longue descente.
        ajouter_impulsion(buf, base + 0.00, 0.20, 500, 750, 0.45, "sine")
        ajouter_impulsion(buf, base + 0.25, 0.20, 550, 800, 0.45, "sine")
        ajouter_impulsion(buf, base + 0.55, 0.85, 950, 430, 0.55, "sine")
    return fondu_bords(normaliser(buf, 0.55))


# --------------------------------------------------------------------------
# reveil_matin.wav — réveil mécanique classique : timbre carré (cloche),
# trille d'amplitude, salves répétées type « brrring ».
# --------------------------------------------------------------------------
def generer_reveil() -> np.ndarray:
    buf = np.zeros(N)
    periode = DUREE / 4
    for i in range(4):
        base = i * periode + 0.1
        duree_salve = 1.4
        idx = (T >= base) & (T < base + duree_salve)
        local = T[idx] - base
        frac = local / duree_salve
        porteuse = onde(2 * np.pi * 2800 * local, "square")
        trille = 0.5 + 0.5 * np.sin(2 * np.pi * 22 * local)
        enveloppe = np.sin(np.pi * frac)
        buf[idx] += 0.6 * enveloppe * trille * porteuse
    return fondu_bords(normaliser(buf, 0.55))


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
