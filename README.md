*[English version](README.en.md)*

# Veilleuse

Application Android minimaliste, thème noir pur OLED, qui transforme le
téléphone en veilleuse : flash ou écran (avec teinte chaude et
luminosité réglables), durée réglable, extinction instantanée ou
progressive. Écran de réglages secondaire avec format d'heure, couleur
d'accentuation (palette de teintes + blanc, avec un curseur pour
l'assombrir) et langue de l'application (26 langues, ou "Système").
Aucune permission Internet, aucun traceur, 100% logiciel libre
(GPL-3.0-or-later).

## Fonctionnement

1. Sur l'écran principal : choisir la source (**Flash** ou **Écran**),
   la durée avant extinction (1 à 120 min), le mode d'extinction
   (**Instantanée** ou **Progressive** avec durée du fondu réglable),
   et pour le mode Écran, la teinte chaude et la luminosité.
2. Icône ⚙️ en haut à droite : écran **Réglages** — format
   d'affichage de l'heure (Compact / Détaillé / HH:MM:SS), couleur
   d'accentuation de l'interface (palette de teintes + blanc, avec un
   curseur pour l'assombrir), et langue de l'application (parmi 26
   langues, ou "Système" pour suivre celle de l'appareil). Une note en
   bas de cet écran renvoie vers ce dépôt GitHub pour signaler un bug
   ou proposer une suggestion.
3. Appuyer sur **Lancer** : l'interface disparaît, l'écran devient noir
   (ou affiche la couleur chaude choisie en plein écran). La torche ou
   l'écran restent actifs, mode immersif (barres système masquées).
4. Un appui sur l'écran fait réapparaître temporairement le temps
   restant et le bouton **Arrêter** (masqués automatiquement après
   quelques secondes).
5. À la fin du minuteur, la lumière s'éteint (instantanément ou en
   fondu) puis l'application se ferme d'elle-même, laissant le
   téléphone repasser en veille normalement.

## Compiler le projet

Prérequis : JDK 17, Android SDK (compileSdk 34), connexion Internet pour
télécharger les dépendances Gradle/AndroidX (aucune dépendance
propriétaire, aucun service Google Play).

Si le JDK par défaut de la machine n'est pas la version 17 (le
compilateur Kotlin embarqué dans Gradle peut planter avec des JDK trop
récents), pointer Gradle vers un JDK 17 sans modifier le dépôt : ajouter
`org.gradle.java.home=/chemin/vers/jdk17` dans le `gradle.properties`
global de l'utilisateur (`~/.gradle/gradle.properties`), ou définir la
variable d'environnement `JAVA_HOME`.

Le binaire `gradle-wrapper.jar` n'est pas versionné dans ce dépôt.
Deux options :

- **Android Studio** : ouvrir le dossier du projet, Android Studio
  régénère automatiquement le wrapper au premier sync.
- **En ligne de commande**, si `gradle` est installé sur la machine :

  ```bash
  gradle wrapper --gradle-version 8.7
  ./gradlew assembleDebug
  ```

L'APK de debug est généré dans `app/build/outputs/apk/debug/`.

## Structure du dépôt

```
app/                      Code source de l'application (Kotlin)
                            Ressources traduites dans 26 langues
                            (app/src/main/res/values-*)
metadata/                  Métadonnées F-Droid/Play (fastlane) : titre,
                            descriptions, changelogs, en fr-FR et en-US
fdroid-metadata-template.yml   Modèle utilisé pour la soumission fdroiddata
CHANGELOG.md               Historique détaillé des versions
LICENSE                    GPL-3.0-or-later
```

## Publier sur F-Droid

Le projet est soumis à F-Droid : voir la merge request
<https://gitlab.com/fdroid/fdroiddata/-/merge_requests/47609> sur le
dépôt `fdroiddata` (en attente de revue par les mainteneurs).

Pour référence, la procédure suivie :

1. Code publié sur un dépôt Git public : <https://github.com/pic38/Veilleuse>.
2. `applicationId` / `namespace` (`app/build.gradle.kts`) réglé sur
   `io.github.pic38.veilleuse`, un identifiant qui appartient
   réellement à l'auteur, avec le package Kotlin correspondant en
   `app/src/main/java/io/github/pic38/veilleuse/`.
3. Un tag Git par version (`vX.Y.Z`), aligné sur `versionName` /
   `versionCode` (`app/build.gradle.kts`).
4. Guide officiel « Submitting to F-Droid » suivi :
   <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>
   — merge request sur le dépôt `fdroiddata` avec un fichier
   `metadata/<applicationId>.yml` basé sur
   `fdroid-metadata-template.yml` fourni ici.

## Permissions

Aucune permission « dangereuse » n'est demandée : le contrôle de la
torche (`CameraManager.setTorchMode`) ne nécessite pas la permission
`CAMERA` sur Android, et l'application n'a pas de permission Internet.

## Licence

GPL-3.0-or-later, voir [LICENSE](LICENSE).
