# Veilleuse

Application Android minimaliste, thème noir pur OLED, qui transforme le
téléphone en veilleuse : flash ou écran (avec teinte chaude réglable),
durée réglable, extinction instantanée ou progressive. Aucune permission
Internet, aucun traceur, 100% logiciel libre (GPL-3.0-or-later).

## Fonctionnement

1. Sur l'écran de réglages : choisir la source (**Flash** ou **Écran**),
   la durée avant extinction (1 à 120 min), le mode d'extinction
   (**Instantanée** ou **Progressive** avec durée du fondu réglable),
   et pour le mode Écran, la teinte chaude et la luminosité.
2. Appuyer sur **Lancer** : l'interface disparaît, l'écran devient noir
   (ou affiche la couleur chaude choisie en plein écran). La torche ou
   l'écran restent actifs, mode immersif (barres système masquées).
3. Un appui sur l'écran fait réapparaître temporairement le temps
   restant et le bouton **Arrêter** (masqués automatiquement après
   quelques secondes).
4. À la fin du minuteur, la lumière s'éteint (instantanément ou en
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
app/                    Code source de l'application (Kotlin)
metadata/                Métadonnées F-Droid/Play (fastlane) : titre,
                          descriptions, changelogs, en fr-FR et en-US
fdroid-metadata-template.yml   Modèle de fichier à soumettre dans fdroiddata
LICENSE                  GPL-3.0-or-later
```

## Publier sur GitHub

```bash
git init
git add .
git commit -m "Version initiale de Veilleuse"
git branch -M main
git remote add origin https://github.com/pic38/Veilleuse.git
git push -u origin main
git tag v1.0.0
git push origin v1.0.0
```

Pensez à créer un dépôt GitHub public nommé `Veilleuse` (ou autre nom de
votre choix — mettez-le à jour dans `fdroid-metadata-template.yml`) avant
le `git push`.

## Publier sur F-Droid

1. Publier le code sur un dépôt Git public (GitHub, GitLab, Codeberg…).
   Fait : <https://github.com/pic38/Veilleuse>.
2. `applicationId` / `namespace` (`app/build.gradle.kts`) réglé sur
   `io.github.pic38.veilleuse`, un identifiant qui appartient
   réellement à l'auteur, avec le package Kotlin correspondant en
   `app/src/main/java/io/github/pic38/veilleuse/`.
3. Créer un tag Git correspondant à la version (`v1.0.0`). Fait.
4. Suivre le guide officiel « Submitting to F-Droid » :
   <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>
   — il s'agit d'ouvrir une merge request sur le dépôt
   `fdroiddata` avec un fichier `metadata/<applicationId>.yml`
   basé sur `fdroid-metadata-template.yml` fourni ici.
5. Alternative plus rapide : héberger son propre dépôt F-Droid avec
   l'outil `fdroidserver` (`fdroid init`, `fdroid build`, `fdroid update`),
   utile pour tester ou distribuer sans passer par le dépôt officiel.

## Permissions

Aucune permission « dangereuse » n'est demandée : le contrôle de la
torche (`CameraManager.setTorchMode`) ne nécessite pas la permission
`CAMERA` sur Android, et l'application n'a pas de permission Internet.

## Licence

GPL-3.0-or-later, voir [LICENSE](LICENSE).
