# Changelog

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
