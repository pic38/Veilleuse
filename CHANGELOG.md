# Changelog

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
