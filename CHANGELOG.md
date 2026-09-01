# Changelog

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
