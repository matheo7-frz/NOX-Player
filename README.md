# 🎧 NOX Player

**Lecteur de musique Android hors ligne — sans pub, sans compte, sans Internet.**
Créé par **math_0235**.

> ⚠️ **Télécharge NOX Player UNIQUEMENT depuis cette page** (onglet *Releases*).
> Toute autre copie peut être modifiée et contenir un virus.

---

## 📥 Installer NOX Player (Android 8 ou plus récent)

1. **Télécharge l'APK officiel**
   👉 [**Releases → dernière version**](https://github.com/matheo7-frz/NOX-Player/releases/latest), puis appuie sur **`NOX-Player.apk`**.
2. **Ouvre le fichier téléchargé** (notification de téléchargement ou appli *Fichiers* → *Téléchargements*).
3. Si Android affiche **« Pour votre sécurité, l'installation d'applications inconnues est bloquée »** :
   appuie sur **Paramètres** → active **« Autoriser cette source »** → reviens en arrière.
4. Appuie sur **Installer**.
5. Si **Google Play Protect** affiche un message : c'est normal pour une appli qui n'est pas sur le Play Store.
   Appuie sur **Plus de détails** → **Installer quand même**.
6. Ouvre **NOX Player** et autorise l'accès à ta musique. C'est tout ✅

### Mettre à jour
Télécharge la nouvelle version au même endroit et installe-la **par-dessus**. Tes favoris et playlists sont gardés.

---

## 🛡️ Vérifier que c'est la VRAIE version

Chaque APK officiel est **signé avec la clé privée de math_0235**. Personne d'autre ne possède cette clé.

**Empreinte officielle du certificat (SHA-256) :**
```
FA:62:98:91:01:FA:1D:CC:7A:BF:17:00:A7:E6:10:A3:76:4B:1F:6E:3A:2F:5F:32:B3:56:AC:74:F6:CA:DD:D8
```

- **Dans l'appli :** *Paramètres → Sécurité* doit afficher **« Version officielle ✓ »**.
- **Copie modifiée ?** Elle **refuse de démarrer** et affiche un écran rouge d'avertissement.
- **Mise à jour pirate ?** Android **refuse de l'installer** par-dessus la vraie appli (la clé ne correspond pas).
- Chaque version publiée est accompagnée de son fichier `NOX-Player.apk.sha256` pour vérifier que le fichier n'a pas été altéré.

---

## 🔒 Protection

| Protection | Ce que ça empêche |
|---|---|
| Signature par clé privée (gardée hors du code, en secret GitHub) | Que quelqu'un publie une fausse mise à jour |
| Vérification anti-modification en **C++** au démarrage | Qu'une copie modifiée ou infectée fonctionne |
| Code obfusqué (R8) + code natif sans symboles | Qu'on lise ou modifie facilement l'appli |
| Interface verrouillée (aucune page extérieure, CSP stricte) | Qu'on injecte du code dans l'interface |
| Aucun serveur, aucune connexion | Que quelqu'un contrôle l'appli à distance ou récupère tes données |

---

## ✨ Fonctions

- Bibliothèque automatique : toute musique ajoutée au téléphone apparaît toute seule
- Chansons, Playlists, Albums, Artistes, Dossiers, Favoris, Récents + recherche et tri (le dernier, le plus joué, A→Z, artiste, durée)
- Lecteur façon Lark : couleurs de la pochette, onde, paroles synchronisées (.lrc), égaliseur + basses, vitesse, minuterie
- File d'attente à réorganiser, 3 modes (répéter un titre / la liste / aléatoire)
- Visualiseur musical, mettre comme sonnerie, modifier titre / artiste / pochette
- Vidéo → audio, import de fichiers, sauvegarde / restauration dans un fichier
- Lecture en arrière-plan, notification, écran verrouillé, boutons des écouteurs

**Technique :** interface en JavaScript, cœur en C++ (sécurité, son, couleurs, paroles), petit pont Java pour Android.

---

## 📜 Licence

**© math_0235 — Tous droits réservés.** Voir [LICENSE](LICENSE).
Tu peux installer et utiliser NOX Player gratuitement. Il est **interdit** de copier, modifier, redistribuer ou republier l'application ou son code.

Un problème de sécurité ? Voir [SECURITY.md](SECURITY.md).
