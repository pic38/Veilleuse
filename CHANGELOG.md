# Changelog

## 1.15.0

- Écran Réglages : pastille blanche ajoutée en tête de la palette de
  couleur d'accentuation, et palette étendue de 20 à 25 teintes.
- Nouveau curseur "Vers le noir" sous la palette : assombrit la
  couleur d'accentuation choisie (boutons, curseurs, titre) en la
  mélangeant progressivement vers le noir, indépendamment du curseur
  de luminosité de la veilleuse.

## 1.14.0

- Fix du crash signalé en 1.6.0 : le curseur de teinte chaude / de
  luminosité pouvait recevoir une valeur non alignée sur ses crans
  (ex. `IllegalStateException` au démarrage) si la préférence
  enregistrée n'était pas un multiple exact du pas du curseur. La
  valeur est maintenant arrondie et bornée avant d'être appliquée.
- Retrait du code de diagnostic temporaire (capture de crash +
  affichage au lancement suivant, introduit en 1.6.0), le bug étant
  identifié et corrigé.

## 1.13.0

- Nouvel écran de réglages (icône engrenage en haut à droite) :
  - Format de l'heure pour la durée totale et la durée du fondu :
    Compact (actuel), Détaillé ("XX h XX min XX s"), ou HH:MM:SS.
    Le compte à rebours pendant la veilleuse garde son propre format
    dédié (voir 1.12.0), non affecté par ce réglage.
  - Couleur d'accentuation : palette de 20 teintes arc-en-ciel
    défilante (façon Image Toolbox), sélection en direct qui retinte
    boutons, sliders et bouton Lancer.

## 1.12.0

- Compte à rebours (temps restant pendant la veilleuse) : format
  distinct qui ne masque que les unités nulles de poids fort ; une
  fois la première unité non nulle atteinte, tout le reste s'affiche
  même à zéro ("1 s", "1 min 0 s", "1 h 0 min 0 s").
- Les curseurs de durée totale et de fondu affichent le temps déjà
  formaté ("15 min", "1 h 30 min"…) dans leur bulle pendant le
  glissement, au lieu de la valeur brute.

## 1.11.0

- Format de temps : les unités à zéro sont masquées (ex. "15 min" au
  lieu de "00 h 15 min 00 s", "1 h 30 min" au lieu de "01 h 30 min 00 s").

## 1.10.0

- Tous les temps affichés (durée totale, durée du fondu, temps
  restant pendant la veilleuse) utilisent désormais un format unique
  "XX h XX min XX s" au lieu de formats différents selon l'écran
  (minutes, secondes brutes, minutes décimales, MM:SS).

## 1.9.0

- Durée du fondu affichée en minutes (1 décimale) au-delà de 60 s, au
  lieu de secondes brutes.
- Curseur de fondu : toujours 100 crans répartis sur toute sa plage,
  quelle que soit la durée totale choisie (le pas s'ajuste
  automatiquement au lieu d'être fixé à 5 s).
- "Instantanée" renommé en "Immédiate" (extinction).
- Le texte affiché une fois les commandes révélées dit maintenant
  "Touchez l'écran pour masquer" (au lieu de "pour afficher", incohérent
  puisqu'il n'apparaît qu'après le premier tap).

## 1.8.0

- Le texte affiché pendant l'attente de mise en veille est maintenant
  bien centré (y compris sur plusieurs lignes).
- Curseur de durée totale : ajout d'un cran "10 s" sous la minute
  minimum, pratique pour tester rapidement.
- Les boutons de choix (source, extinction) affichent désormais
  clairement l'option sélectionnée (fond ambre plein + texte noir) au
  lieu de rester quasi identiques entre coché/non coché.

## 1.7.0

- À la fin du minuteur, le bouton Arrêter et l'affichage/masquage des
  informations au tap restent disponibles (au lieu d'être désactivés) :
  le message "en attente de mise en veille" fait maintenant partie du
  même système d'affichage au tap que le reste, caché par défaut.
- (Le build de diagnostic temporaire introduit en 1.6.0 est toujours
  actif en attendant le retour sur le crash signalé.)

## 1.6.0

- Build de diagnostic temporaire : capture le prochain crash non
  rattrapé et l'affiche (texte sélectionnable, copié automatiquement
  dans le presse-papier) au lancement suivant, pour permettre de
  signaler un crash précis sans accès à `adb logcat`. Sera retiré une
  fois le bug identifié.

## 1.5.0

- Fix : le numéro de version (et le bas du bouton Lancer) pouvait être
  masqué par la barre de navigation système sur l'écran de réglages,
  qui passait sous les barres système comme l'écran veilleuse. Seul
  l'écran veilleuse actif reste désormais edge-to-edge ; l'écran de
  réglages respecte les insets système (padding haut/bas).
- Bloc de contrôles remonté d'une hauteur de bouton par rapport au bas
  de l'écran.

## 1.4.0

- Titre de l'écran de réglages descendu (marge augmentée d'environ sa
  propre hauteur).
- Les contrôles (source, durée, extinction, bouton Lancer) sont
  désormais regroupés vers le bas de l'écran plutôt qu'étalés depuis
  le haut, pour une meilleure accessibilité au pouce sur grand écran.
- Numéro de version affiché discrètement en bas de l'écran de réglages.

## 1.3.0

- À la fin du minuteur, un texte discret indique que l'app attend la
  mise en veille automatique du téléphone (réglages Android) avant de
  se fermer ; le tap pour rappeler les commandes est aussi désactivé
  à ce moment-là.
- Fondu écran : fréquence de mise à jour x10 (5 ms au lieu de 50 ms)
  pour une transition encore plus lisse.
- Fondu flash : dithering réduit d'un facteur 5 (2 paliers de
  simulation au lieu de 10) suite aux retours terrain.
- Le curseur de durée du fondu peut désormais aller jusqu'à la durée
  totale complète de la veilleuse (le plafond de 120 s est supprimé).

## 1.2.0

- Curseur de luminosité étendu de 0 à 100 (au lieu de 10-100) : à 0,
  la surface écran devient transparente en plus du rétroéclairage au
  minimum, pour une extinction réellement nulle.
- Fondu du flash : dithering temporel entre les deux niveaux matériels
  voisins pour simuler ~10× plus de paliers de luminosité perçus (le
  nombre de niveaux physiques reste limité par le matériel, mais la
  transition est nettement plus lisse).
- Fin de veilleuse par minuteur : l'app attend désormais l'extinction
  réelle de l'écran (diffusion système `ACTION_SCREEN_OFF`) avant de se
  fermer, pour éviter que l'écran d'accueil s'affiche brièvement en
  pleine luminosité pendant la mise en veille automatique du téléphone.

## 1.1.0

- Fix : plein écran immersif tronqué par une bande noire sur les appareils
  à encoche/perforation (gestion du `layoutInDisplayCutoutMode`).
- Fix : le fondu progressif (flash ou écran) était parfois instantané
  au lieu d'être progressif sur certains appareils/réglages système
  (dépendance à l'échelle d'animation système supprimée, fondu piloté
  manuellement).
- Le fondu ne peut plus être réglé à une durée supérieure à la veilleuse
  elle-même.
- Les curseurs de teinte et de luminosité sont désormais gradués en
  entiers (plus de décimales).
- Fin de veilleuse par minuteur : l'application se ferme bien
  automatiquement comme documenté (au lieu de revenir aux réglages).
- Interface plus arrondie (boutons de choix, pastille d'aperçu couleur).

## 1.0.0

- Première version : choix de la source lumineuse (flash ou écran avec
  teinte chaude réglable), durée réglable, extinction instantanée ou
  progressive, thème noir pur OLED, mode immersif avec réapparition des
  commandes au toucher.
