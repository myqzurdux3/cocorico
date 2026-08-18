#!/usr/bin/env python3
"""Génère les sonneries de remplacement de Cocorico.

La machine de développement n'a ni accès réseau ni encodeur audio (pas de
ffmpeg, pas de sox) : impossible de suivre la consigne initiale « sourcer
quatre .ogg sur freesound.org ». Ce script synthétise donc quatre sonneries
avec numpy et le module `wave` de la bibliothèque standard, et les écrit
directement dans `app/src/main/res/raw/` au format WAV PCM 16 bits, mono,
22 050 Hz, 8 secondes. **Il n'écrit que trois fichiers : `coq.wav` est un
enregistrement, pas une synthèse, et ce script ne doit jamais l'écraser.**

Le nom de ressource Android ignore l'extension : `klaxon.wav` reste bien
`R.raw.klaxon`, donc `Sonneries.kt` n'a besoin d'aucun changement.

Le réveil-matin a été resynthétisé le 18 août 2026 : il sonnait comme un
buzzer électronique et non comme un réveil à cloches. Le coq, lui, ne se
génère plus du tout — un vrai enregistrement fourni par l'utilisateur a
remplacé la synthèse. Le klaxon et la sirène restent des sonneries de
remplacement sans prétention, à
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
# coq.wav n'est plus synthétisé. Une version de synthèse a été tentée — pile
# d'harmoniques, tremblement de hauteur, souffle — et restait reconnaissable
# comme une imitation : l'utilisateur a fourni un vrai enregistrement, qui la
# remplace. Ce script ne le régénère donc pas, et ne doit pas l'écraser.
#
# Conversion appliquée à l'enregistrement, pour le mettre au format des autres
# (mono, PCM 16 bits, crête à 0,85, bords à zéro sur 8 ms) :
#
#     python3 -c "
#     import miniaudio, numpy as np, wave
#     d = miniaudio.decode_file('coq.mp3')
#     x = np.array(d.samples, dtype=np.float64).reshape(-1, d.nchannels)
#     m = x.mean(axis=1); m = m / np.max(np.abs(m)) * 0.85 * 32767
#     n = int(d.sample_rate * 0.008); r = np.linspace(0, 1, n)
#     m[:n] *= r; m[-n:] *= r[::-1]
#     w = wave.open('app/src/main/res/raw/coq.wav', 'wb')
#     w.setnchannels(1); w.setsampwidth(2); w.setframerate(d.sample_rate)
#     w.writeframes(np.clip(m, -32768, 32767).astype(np.int16).tobytes()); w.close()"
#
# La fréquence d'échantillonnage de la source est conservée (44 100 Hz) plutôt
# que ramenée aux 22 050 Hz des sonneries de synthèse : rééchantillonner un
# vrai enregistrement pour ressembler à des fichiers de remplacement serait
# dégrader le seul son authentique du lot.
# --------------------------------------------------------------------------


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
        # "coq.wav" absent volontairement : vrai enregistrement, voir plus haut.
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
    print("OK : trois sonneries générées dans", DOSSIER_RAW, "(coq.wav non touché)")


if __name__ == "__main__":
    main()
