/* NOX Player — interface en JavaScript — fait par math_0235
 * Toute l'appli visible est ici. Elle parle au téléphone via l'objet NOX (pont Java)
 * et reçoit les nouvelles via NOX_event(type, données). Aucun accès Internet. */
'use strict';
(function () {
  const N = window.NOX;
  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const J = (s, d) => { try { return JSON.parse(s); } catch (e) { return d; } };

  if (!N) { document.body.innerHTML = '<p style="padding:40px">NOX Player doit être ouvert depuis l\'appli.</p>'; return; }

  // ================================================================ Icônes (SVG)
  const P = {
    search: 'M10 3a7 7 0 015.6 11.2l4.6 4.6-1.4 1.4-4.6-4.6A7 7 0 1110 3zm0 2a5 5 0 100 10 5 5 0 000-10z',
    sort: 'M3 6h11v2H3zm0 5h8v2H3zm0 5h5v2H3zm15-11h2v11.2l2.6-2.6 1.4 1.4-5 5-5-5 1.4-1.4 2.6 2.6z',
    gear: 'M12 8a4 4 0 110 8 4 4 0 010-8zm0 2a2 2 0 100 4 2 2 0 000-4zM10.6 2h2.8l.5 2.6 1.6.7 2.2-1.5 2 2-1.5 2.2.7 1.6 2.6.5v2.8l-2.6.5-.7 1.6 1.5 2.2-2 2-2.2-1.5-1.6.7-.5 2.6h-2.8l-.5-2.6-1.6-.7-2.2 1.5-2-2 1.5-2.2-.7-1.6L2 13.4v-2.8l2.6-.5.7-1.6-1.5-2.2 2-2 2.2 1.5 1.6-.7z',
    play: 'M8 5.5v13a1 1 0 001.5.9l10.4-6.5a1 1 0 000-1.8L9.5 4.6A1 1 0 008 5.5z',
    pause: 'M7 5h3.2a1 1 0 011 1v12a1 1 0 01-1 1H7a1 1 0 01-1-1V6a1 1 0 011-1zm6.8 0H17a1 1 0 011 1v12a1 1 0 01-1 1h-3.2a1 1 0 01-1-1V6a1 1 0 011-1z',
    next: 'M5 6.2v11.6a1 1 0 001.6.8l8-5.8a1 1 0 000-1.6l-8-5.8A1 1 0 005 6.2zM16.5 6h2a.5.5 0 01.5.5v11a.5.5 0 01-.5.5h-2a.5.5 0 01-.5-.5v-11a.5.5 0 01.5-.5z',
    rew: 'M11.5 6.3v11.4a.9.9 0 01-1.4.7L2.4 12.7a.9.9 0 010-1.4l7.7-5.7a.9.9 0 011.4.7zm10 0v11.4a.9.9 0 01-1.4.7l-7.7-5.7a.9.9 0 010-1.4l7.7-5.7a.9.9 0 011.4.7z',
    ff: 'M12.5 6.3v11.4a.9.9 0 001.4.7l7.7-5.7a.9.9 0 000-1.4l-7.7-5.7a.9.9 0 00-1.4.7zm-10 0v11.4a.9.9 0 001.4.7l7.7-5.7a.9.9 0 000-1.4L3.9 5.6a.9.9 0 00-1.4.7z',
    heart: 'M12 20.5l-1.4-1.3C5.4 14.5 2 11.4 2 7.6 2 4.5 4.4 2 7.5 2c1.7 0 3.4.8 4.5 2.1C13.1 2.8 14.8 2 16.5 2 19.6 2 22 4.5 22 7.6c0 3.8-3.4 6.9-8.6 11.6z',
    heartO: 'M16.5 2c-1.7 0-3.4.8-4.5 2.1C10.9 2.8 9.2 2 7.5 2 4.4 2 2 4.5 2 7.6c0 3.8 3.4 6.9 8.6 11.6L12 20.5l1.4-1.3C18.6 14.5 22 11.4 22 7.6 22 4.5 19.6 2 16.5 2zM12.1 17.7l-.1.1-.1-.1C7.1 13.4 4 10.6 4 7.6 4 5.6 5.5 4 7.5 4c1.5 0 3 1 3.6 2.4h1.9C13.5 5 15 4 16.5 4c2 0 3.5 1.6 3.5 3.6 0 3-3.1 5.8-7.9 10.1z',
    more: 'M12 7a2 2 0 110-4 2 2 0 010 4zm0 7a2 2 0 110-4 2 2 0 010 4zm0 7a2 2 0 110-4 2 2 0 010 4z',
    down: 'M7.4 8.6L12 13.2l4.6-4.6L18 10l-6 6-6-6z',
    back: 'M15.4 7.4L14 6l-6 6 6 6 1.4-1.4L10.8 12z',
    eq: 'M6 3h2v6.2a3 3 0 010 5.6V21H6v-6.2a3 3 0 010-5.6zm5 0h2v10.2a3 3 0 010 5.6V21h-2v-2.2a3 3 0 010-5.6zm5 0h2v3.2a3 3 0 010 5.6V21h-2v-9.2a3 3 0 010-5.6z',
    queue: 'M3 6h12v2H3zm0 5h12v2H3zm0 5h8v2H3zm14-4v8l6-4z',
    shuffle: 'M14.8 4H20v5.2l-1.9-1.9-12.7 12.7L4 18.6 16.7 5.9zM4 5.4L5.4 4l5.1 5.1L9.1 10.5zm11 8.7l1.4-1.4 1.7 1.7L20 12.5V20h-5.2l1.9-1.9z',
    repeat: 'M7 7h10v3l4-4-4-4v3H5v6h2zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2z',
    repeat1: 'M7 7h10v3l4-4-4-4v3H5v6h2zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2zm-4-2V9h-1l-2 1v1h1.5v4z',
    locate: 'M12 8a4 4 0 110 8 4 4 0 010-8zm-9 3h2.1A7 7 0 0111 5.1V3h2v2.1a7 7 0 015.9 5.9H21v2h-2.1a7 7 0 01-5.9 5.9V21h-2v-2.1A7 7 0 015.1 13H3z',
    broom: 'M19.4 3.2l1.4 1.4-6.3 6.3c1.5 2.1 1.4 4.5-.1 6l-4 4-6.4-6.4 4-4c1.5-1.5 3.9-1.6 6-.1zm-9.1 9.3l-2.2 2.2 2.2 2.2 2.2-2.2c.6-.6.6-1.6 0-2.2s-1.6-.6-2.2 0z',
    plus: 'M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z',
    share: 'M14 5l7 7-7 7v-4.1c-5 0-8.5 1.6-11 5.1 1-5 4-10 11-11z',
    viz: 'M3 10h2v4H3zm4-4h2v12H7zm4-3h2v18h-2zm4 5h2v8h-2zm4 3h2v2h-2z',
    speed: 'M12 4a9 9 0 019 9h-2a7 7 0 10-7 7v2a9 9 0 010-18zm4.2 3.4l1.4 1.4-4.6 4.6a1.5 1.5 0 11-1.4-1.4z',
    bell: 'M12 22a2.5 2.5 0 002.4-2H9.6a2.5 2.5 0 002.4 2zm7-6V11a7 7 0 00-5.5-6.8V3h-3v1.2A7 7 0 005 11v5l-2 2v1h18v-1z',
    clock: 'M12 4a8 8 0 110 16 8 8 0 010-16zm0 2a6 6 0 100 12 6 6 0 000-12zm-1 2h2v4.6l3 1.8-1 1.7-4-2.4zM6.5 2.3l1.2 1.6-3.6 2.7-1.2-1.6zm11 0l3.6 2.7-1.2 1.6-3.6-2.7z',
    hide: 'M2.8 2.1l19.1 19.1-1.4 1.4-3.3-3.3A10.6 10.6 0 0112 20C6.5 20 2.6 16 1 12a12 12 0 013.9-5.1L1.4 3.5zM12 4c5.5 0 9.4 4 11 8a12.2 12.2 0 01-3 4.3l-3-3A5 5 0 0010.7 7L8.6 4.9A11 11 0 0112 4z',
    trash: 'M9 3h6l1 2h4v2H4V5h4zM6 9h12l-1 12H7z',
    edit: 'M4 17.2V20h2.8L17 9.8 14.2 7zM19.7 7a1 1 0 000-1.4l-1.3-1.3a1 1 0 00-1.4 0l-1.4 1.4L18.4 8.5z',
    next2: 'M4 6h10v2H4zm0 5h10v2H4zm0 5h6v2H4zm12-5v8l6-4z',
    addq: 'M3 6h12v2H3zm0 5h12v2H3zm0 5h8v2H3zm14-2v-3h2v3h3v2h-3v3h-2v-3h-3v-2z',
    minus: 'M5 11h14v2H5z',
    close: 'M6.4 5L12 10.6 17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19 5 17.6l5.6-5.6L5 6.4z',
  };
  const I = (n) => '<svg viewBox="0 0 24 24"><path d="' + P[n] + '"/></svg>';
  const setI = (el, n) => { el.innerHTML = I(n); };

  // ================================================================ Utilitaires
  const fmt = (ms) => {
    ms = Math.max(0, ms | 0);
    const s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
    return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0');
  };
  const hue = (s) => { let h = 0; for (const c of String(s)) h = (h * 31 + c.charCodeAt(0)) | 0; return 250 + (Math.abs(h) % 120); };
  const grad = (s) => { const h = hue(s); return 'linear-gradient(135deg,hsl(' + (h % 360) + ',70%,45%),hsl(' + ((h + 50) % 360) + ',70%,58%))'; };
  const initial = (s) => { const m = String(s || '').match(/[\p{L}\p{N}]/u); return m ? m[0].toUpperCase() : '♪'; };
  const cover = (t, cls, size) => '<div class="' + cls + '" style="background:' + grad((t.album || '') + (t.artist || '') + (t.id < 0 ? t.title : '')) + '">'
    + esc(initial(t.title)) + '<img loading="lazy" src="' + esc(t.art + '&s=' + (size || 160)) + '" alt=""></div>';
  // Images manquantes : on retire la balise, le dégradé reste (pas de gestionnaire inline à cause de la CSP)
  document.addEventListener('error', (e) => { if (e.target && e.target.tagName === 'IMG' && !e.target.classList.contains('logo')) e.target.remove(); }, true);

  let toastT = 0;
  function toast(msg) {
    const t = $('toast');
    t.textContent = msg;
    t.classList.remove('hidden');
    clearTimeout(toastT);
    toastT = setTimeout(() => t.classList.add('hidden'), 2600);
  }

  // ================================================================ Données
  const S = {
    lib: { songs: [], hidden: [], trash: [], favs: [], recent: [], playlists: {} },
    byId: new Map(),
    tab: 0, query: '', detail: null,
    sort: +(N.pref('sort', '0')), sortRev: N.pref('sortRev', '0') === '1',
    st: { cur: null }, perm: true, loaded: false,
  };
  const TABS = ['Chansons', 'Playlists', 'Albums', 'Artistes', 'Dossiers', 'Favoris', 'Récents'];
  const SORTS = ['Le dernier', 'Le plus joué', 'De A à Z', 'Artiste', 'Durée la plus longue'];
  const MODES = ['Répéter un titre', 'Répéter la liste', 'Lecture aléatoire'];
  const MODE_ICONS = ['repeat1', 'repeat', 'shuffle'];

  // ================================================================ Thème
  const THEMES = [['Néon violet', '#b14cff', '#00e5ff'], ['Bleu', '#4a90f2', '#7fc4ff'], ['Rose', '#ff4d8d', '#ffb3d1'],
    ['Vert', '#22c55e', '#a7f3d0'], ['Orange', '#ff8a3d', '#ffd6a5'], ['Rouge', '#e53935', '#ff9e80']];
  const BGS = [['Nuit violette', '#0b0714', '#181128', '#231939'], ['Noir pur', '#000000', '#161616', '#222222'], ['Gris foncé', '#1e2124', '#2a2e33', '#353a40']];
  function applyTheme() {
    const t = THEMES[+N.pref('theme', '0')] || THEMES[0], b = BGS[+N.pref('themeBg', '0')] || BGS[0];
    const r = document.documentElement.style;
    r.setProperty('--accent', t[1]); r.setProperty('--accent2', t[2]);
    r.setProperty('--bg', b[1]); r.setProperty('--card', b[2]); r.setProperty('--card2', b[3]);
  }

  // ================================================================ Couches (pour le bouton retour)
  const stack = [];
  function openLayer(id, onClose) { const el = $(id); el.classList.remove('hidden'); stack.push({ id, onClose }); }
  function closeTop() {
    const top = stack.pop();
    if (!top) return false;
    $(top.id).classList.add('hidden');
    if (top.onClose) top.onClose();
    return true;
  }
  function closeLayer(id) {
    const i = stack.map((x) => x.id).lastIndexOf(id);
    if (i < 0) return;
    while (stack.length > i) closeTop();
  }
  window.NOX_back = function () {
    if (stack.length) return closeTop();
    if (S.detail) { S.detail = null; render(); return true; }
    if (S.query) { $('search').value = ''; S.query = ''; render(); return true; }
    if (!$('searchBox').classList.contains('hidden')) { $('searchBox').classList.add('hidden'); return true; }
    return false;
  };

  // ================================================================ Feuille du bas / fenêtres
  function sheet(html, bind) {
    const sh = $('sheet');
    sh.innerHTML = '<button class="grip"></button>' + html;
    openLayer('sheetBack');
    sh.querySelector('.grip').onclick = () => closeLayer('sheetBack');
    if (bind) bind(sh);
  }
  $('sheetBack').addEventListener('click', (e) => { if (e.target.id === 'sheetBack') closeLayer('sheetBack'); });

  /** items: [{icon,label,run,sel}] ou 'div' */
  function sheetMenu(headHtml, items) {
    let h = headHtml || '';
    items.forEach((it, i) => {
      if (it === 'div') { h += '<div class="sdiv"></div>'; return; }
      h += '<button class="sitem' + (it.sel ? ' sel' : '') + '" data-i="' + i + '"><span class="si">' + (it.icon ? I(it.icon) : '') + '</span><span class="grow">' + esc(it.label) + '</span>' + (it.right || '') + '</button>';
    });
    sheet(h, (sh) => {
      sh.querySelectorAll('.sitem').forEach((b) => b.onclick = () => {
        const it = items[+b.dataset.i];
        if (!it.keep) closeLayer('sheetBack');
        it.run();
      });
    });
  }

  function modal(title, bodyHtml, buttons, bind) {
    const m = $('modal');
    m.innerHTML = (title ? '<h3>' + esc(title) + '</h3>' : '') + (bodyHtml || '') + '<div class="mbtns">'
      + (buttons || []).map((b, i) => '<button class="btn' + (b.ghost ? ' ghost' : '') + (b.danger ? ' danger' : '') + '" data-b="' + i + '">' + esc(b.label) + '</button>').join('') + '</div>';
    openLayer('modalBack');
    m.querySelectorAll('[data-b]').forEach((el) => el.onclick = () => {
      const b = buttons[+el.dataset.b];
      if (b.run && b.run(m) === false) return;
      closeLayer('modalBack');
    });
    if (bind) bind(m);
  }
  $('modalBack').addEventListener('click', (e) => { if (e.target.id === 'modalBack' && !$('modal').dataset.lock) closeLayer('modalBack'); });
  const alertBox = (title, text) => modal(title, '<p>' + esc(text) + '</p>', [{ label: 'OK' }]);
  const confirmBox = (title, text, label, run, danger) => modal(title, '<p>' + esc(text) + '</p>', [{ label: 'Annuler', ghost: true }, { label, run, danger }]);
  function prompt(title, value, placeholder, run) {
    modal(title, '<input type="text" id="mIn" placeholder="' + esc(placeholder || '') + '" value="' + esc(value || '') + '">',
      [{ label: 'Annuler', ghost: true }, { label: 'OK', run: (m) => run(m.querySelector('#mIn').value.trim()) }],
      (m) => { const i = m.querySelector('#mIn'); i.focus(); i.select(); });
  }
  function listBox(title, labels, run) {
    modal(title, '<div class="mlist">' + labels.map((l, i) => '<button data-l="' + i + '">' + esc(l) + '</button>').join('') + '</div>', [{ label: 'Fermer', ghost: true }],
      (m) => m.querySelectorAll('[data-l]').forEach((b) => b.onclick = () => { closeLayer('modalBack'); run(+b.dataset.l); }));
  }

  // ================================================================ Bibliothèque
  function setLibrary(lib) {
    S.lib = lib;
    S.byId = new Map();
    lib.songs.forEach((t) => S.byId.set(t.id, t));
    lib.hidden.forEach((t) => S.byId.set(t.id, t));
    lib.trash.forEach((t) => S.byId.set(t.id, t));
    S.loaded = true;
    S.perm = true;
    render();
    if ($('page').dataset.kind === 'settings' && !$('page').classList.contains('hidden')) openSettings(true);
  }

  const match = (t) => !S.query || (t.title + ' ' + t.artist + ' ' + t.album).toLowerCase().includes(S.query);
  const ids = (arr) => arr.map((id) => S.byId.get(id)).filter((t) => t && S.lib.songs.includes(t));
  function sorted(list) {
    const l = list.slice(), c = (a, b) => a.localeCompare(b, 'fr', { sensitivity: 'base' });
    switch (S.sort) {
      case 0: l.sort((a, b) => b.added - a.added); break;
      case 1: l.sort((a, b) => (b.plays || 0) - (a.plays || 0)); break;
      case 3: l.sort((a, b) => c(a.artist, b.artist) || c(a.title, b.title)); break;
      case 4: l.sort((a, b) => b.duration - a.duration); break;
      default: l.sort((a, b) => c(a.title, b.title));
    }
    if (S.sortRev) l.reverse();
    return l;
  }
  function groups(key) {
    const m = new Map();
    S.lib.songs.forEach((t) => { const k = t[key] || 'Inconnu'; if (!m.has(k)) m.set(k, []); m.get(k).push(t); });
    return [...m.entries()].sort((a, b) => a[0].localeCompare(b[0], 'fr', { sensitivity: 'base' }));
  }

  let shown = [];
  function render() {
    renderTabs();
    const list = $('list'), empty = $('empty');
    let html = '';
    shown = [];
    const curId = S.st.cur ? S.st.cur.id : null;
    const rowT = (t) => {
      shown.push(t);
      const fav = S.lib.favs.includes(t.id) ? '♥  ' : '';
      return '<div class="row' + (t.id === curId ? ' cur' : '') + '" data-t="' + t.id + '">' + cover(t, 'cov', 120)
        + '<div class="txt"><b>' + (t.id === curId ? '♪  ' : '') + esc(t.title) + '</b><small>' + fav + esc(t.artist) + '  ·  ' + fmt(t.duration) + '</small></div>'
        + '<button class="more" data-m="' + t.id + '">⋮</button></div>';
    };
    const rowG = (name, tracks, sub, kind) => '<div class="row" data-g="' + esc(name) + '" data-k="' + kind + '">'
      + (tracks[0] ? cover(tracks[0], 'cov', 120) : '<div class="cov" style="background:' + grad(name) + '">♫</div>')
      + '<div class="txt"><b>' + esc(name) + '</b><small>' + esc(sub) + '</small></div>'
      + (kind === 'pl' ? '<button class="more" data-pm="' + esc(name) + '">⋮</button>' : '') + '</div>';
    const nb = (n) => n + (n > 1 ? ' titres' : ' titre');

    $('backBar').classList.toggle('hidden', !S.detail);
    if (!S.perm) {
      empty.textContent = '🎵\n\nNOX Player a besoin d\'accéder à ta musique.\n\nAppuie ici pour autoriser.';
    } else if (!S.loaded) {
      empty.textContent = 'Recherche de ta musique…';
    } else if (S.detail) {
      $('backBar').textContent = '←   ' + S.detail.name;
      let tr = S.detail.kind === 'pl' ? ids(S.lib.playlists[S.detail.name] || []) : S.detail.tracks;
      tr.filter(match).forEach((t) => html += rowT(t));
      empty.textContent = S.detail.kind === 'pl' ? 'Playlist vide.\nMenu ⋮ d\'une chanson → « Ajouter à la Playlist »' : 'Rien ici';
    } else {
      switch (S.tab) {
        case 0:
          if (!S.query) html += '<div class="row add" id="addMore"><div class="cov" style="background:var(--card2);font-size:30px;color:#bbb">＋</div><div class="txt"><b>Ajouter plus de musique</b><small>Importer · vidéo → audio · lien</small></div></div>';
          sorted(S.lib.songs).filter(match).forEach((t) => html += rowT(t));
          break;
        case 1:
          html += '<div class="row" id="newPl"><div class="cov" style="background:linear-gradient(135deg,var(--accent),var(--accent2));font-size:28px">＋</div><div class="txt"><b>Nouvelle playlist</b><small>Crée ta propre liste</small></div></div>';
          Object.keys(S.lib.playlists).filter((n) => !S.query || n.toLowerCase().includes(S.query))
            .forEach((n) => { const tr = ids(S.lib.playlists[n]); html += rowG(n, tr, nb(tr.length), 'pl'); });
          break;
        case 2: case 3: case 4: {
          const key = ['album', 'artist', 'folder'][S.tab - 2];
          groups(key).filter(([n]) => !S.query || n.toLowerCase().includes(S.query))
            .forEach(([n, tr]) => html += rowG(n, tr, (key === 'album' ? tr[0].artist + '  ·  ' : '') + nb(tr.length), key));
          break;
        }
        case 5: ids(S.lib.favs).sort((a, b) => a.title.localeCompare(b.title, 'fr')).filter(match).forEach((t) => html += rowT(t)); break;
        case 6: ids(S.lib.recent).filter(match).forEach((t) => html += rowT(t)); break;
      }
      empty.textContent = S.query ? 'Aucun résultat pour « ' + S.query + ' »'
        : S.tab === 5 ? 'Pas encore de favoris.\nAppuie sur ♥ dans le lecteur.'
          : S.tab === 6 ? 'Rien écouté pour l\'instant'
            : 'Aucune musique trouvée.\nAjoute des fichiers audio sur ton téléphone,\nils apparaîtront ici tout seuls.';
    }
    list.innerHTML = html;
    const onlyFixed = shown.length === 0 && (S.tab === 0 || S.tab === 1) && !S.detail && S.perm && S.loaded;
    empty.classList.toggle('hidden', !!html && !onlyFixed && (shown.length > 0 || list.querySelectorAll('[data-g]').length > 0));
    if (onlyFixed && S.tab === 1) empty.classList.add('hidden');
    const sh = $('bShuffleAll');
    sh.classList.toggle('hidden', shown.length < 2);
    sh.textContent = '⇄   Lecture aléatoire  ·  ' + shown.length + ' titres';
    renderMini();
  }

  function renderTabs() {
    $('tabs').innerHTML = TABS.map((t, i) => '<button class="tab' + (i === S.tab ? ' on' : '') + '" data-tab="' + i + '">' + t + '</button>').join('');
  }

  $('tabs').addEventListener('click', (e) => {
    const b = e.target.closest('[data-tab]');
    if (!b) return;
    S.tab = +b.dataset.tab; S.detail = null; render(); $('list').scrollTop = 0;
  });
  $('backBar').onclick = () => { S.detail = null; render(); };
  $('empty').onclick = () => { if (!S.perm) N.askPermission(); };
  $('bShuffleAll').onclick = () => {
    if (shown.length < 1) return;
    if (S.st.cur && S.st.mode !== 2) N.cmd('mode', '2');
    N.play(JSON.stringify(shown.map((t) => t.id)), Math.floor(Math.random() * shown.length));
    if (!S.st.cur) setTimeout(() => N.cmd('mode', '2'), 400);
  };

  $('list').addEventListener('click', (e) => {
    const m = e.target.closest('[data-m]');
    if (m) { trackMenu(S.byId.get(+m.dataset.m)); return; }
    const pm = e.target.closest('[data-pm]');
    if (pm) { playlistMenu(pm.dataset.pm); return; }
    if (e.target.closest('#addMore')) { addMusicMenu(); return; }
    if (e.target.closest('#newPl')) { newPlaylist(null); return; }
    const r = e.target.closest('.row');
    if (!r) return;
    if (r.dataset.t) {
      const id = +r.dataset.t, i = shown.findIndex((t) => t.id === id);
      N.play(JSON.stringify(shown.map((t) => t.id)), Math.max(0, i));
    } else if (r.dataset.g) {
      const name = r.dataset.g, k = r.dataset.k;
      S.detail = k === 'pl' ? { kind: 'pl', name } : { kind: k, name, tracks: S.lib.songs.filter((t) => (t[k] || 'Inconnu') === name) };
      render(); $('list').scrollTop = 0;
    }
  });
  // appui long = menu
  let lpT = 0;
  $('list').addEventListener('touchstart', (e) => {
    const r = e.target.closest('.row[data-t]');
    if (!r) return;
    lpT = setTimeout(() => { lpT = -1; trackMenu(S.byId.get(+r.dataset.t)); }, 550);
  }, { passive: true });
  ['touchend', 'touchmove', 'touchcancel'].forEach((ev) => $('list').addEventListener(ev, (e) => {
    if (lpT === -1 && ev === 'touchend') e.preventDefault();
    clearTimeout(lpT); if (lpT !== -1) lpT = 0; else if (ev !== 'touchend') lpT = 0;
  }));
  $('list').addEventListener('click', (e) => { if (lpT === -1) { e.stopImmediatePropagation(); lpT = 0; } }, true);

  // ---------- En-tête
  setI($('bSearch'), 'search'); setI($('bSort'), 'sort'); setI($('bSettings'), 'gear');
  $('bSearch').onclick = () => {
    const b = $('searchBox');
    if (!b.classList.contains('hidden') && !S.query) { b.classList.add('hidden'); return; }
    b.classList.remove('hidden'); $('search').focus();
  };
  $('search').addEventListener('input', (e) => { S.query = e.target.value.trim().toLowerCase(); render(); });
  $('bSort').onclick = () => {
    sheetMenu('<h3>Tri des chansons</h3>', SORTS.map((s, i) => ({
      label: s, sel: i === S.sort, right: i === S.sort ? '<span style="font-size:22px">' + (S.sortRev ? '⇡' : '⇣') + '</span>' : '',
      run: () => {
        if (i === S.sort) S.sortRev = !S.sortRev; else { S.sort = i; S.sortRev = false; }
        N.setPref('sort', String(S.sort)); N.setPref('sortRev', S.sortRev ? '1' : '0');
        S.tab = 0; S.detail = null; render(); $('list').scrollTop = 0;
      },
    })));
  };
  $('bSettings').onclick = () => openSettings();

  // ================================================================ Menus
  function trackMenu(t) {
    if (!t) return;
    const ext = t.id < 0;
    const head = '<div class="shead">' + cover(t, 'cov', 160) + '<div class="t">' + esc(t.title) + '</div>' + (ext ? '' : '<button class="ib" id="shEdit">' + I('edit') + '</button>') + '</div>';
    const items = [];
    if (!ext) items.push({ icon: 'addq', label: 'Ajouter à la Playlist', run: () => pickPlaylist(t) });
    items.push({ icon: 'next2', label: 'Lire ensuite', run: () => { N.enqueue(t.id, true); toast('Sera joué ensuite'); } });
    items.push({ icon: 'queue', label: 'Ajouter à la file d\'attente', run: () => { N.enqueue(t.id, false); toast('Ajouté à la file'); } });
    items.push({ icon: 'share', label: 'Partager', run: () => N.share(t.id) });
    items.push('div');
    items.push({ icon: 'viz', label: 'Visualiseur musical', run: () => { if (!S.st.cur || S.st.cur.id !== t.id) N.play(JSON.stringify([t.id]), 0); openViz(); } });
    items.push({ icon: 'speed', label: 'Vitesse', run: speedBox });
    if (!ext) items.push({ icon: 'bell', label: 'Mettre comme sonnerie', run: () => ringtone(t) });
    items.push({ icon: 'clock', label: 'Minuterie de sommeil', run: sleepBox });
    if (!ext) {
      items.push('div');
      if (S.detail && S.detail.kind === 'pl') items.push({ icon: 'minus', label: 'Retirer de cette playlist', run: () => { N.playlist('remove', S.detail.name, String(t.id)); N.reload(); } });
      items.push({ icon: 'hide', label: 'Masquer', run: () => { N.setFlag('hidden', t.id, true); toast('Masqué — Paramètres › Fichiers masqués pour le réafficher'); } });
      items.push({ icon: 'trash', label: 'Supprimer', run: () => confirmBox('Supprimer « ' + t.title + ' » ?', 'La chanson va dans « Récemment supprimé » (Paramètres). Tu pourras la restaurer.', 'Supprimer', () => N.setFlag('trash', t.id, true), true) });
    }
    sheetMenu(head, items);
    const e = $('sheet').querySelector('#shEdit');
    if (e) e.onclick = () => { closeLayer('sheetBack'); openEdit(t); };
  }

  function ringtone(t) {
    const r = N.ringtone(t.id);
    if (r === 'ok') toast('🔔 « ' + t.title + ' » est ta nouvelle sonnerie');
    else if (r === 'perm') toast('Autorise « Modifier les paramètres système », puis réessaie');
    else toast('Impossible de changer la sonnerie sur ce téléphone');
  }

  function pickPlaylist(t) {
    const names = Object.keys(S.lib.playlists);
    listBox('Ajouter à…', ['＋  Nouvelle playlist'].concat(names), (i) => {
      if (i === 0) { newPlaylist(t); return; }
      const n = names[i - 1];
      if ((S.lib.playlists[n] || []).includes(t.id)) { toast('Déjà dans « ' + n + ' »'); return; }
      N.playlist('add', n, String(t.id)); toast('Ajouté à « ' + n + ' »'); N.reload();
    });
  }
  function newPlaylist(t) {
    prompt('Nouvelle playlist', '', 'Nom de la playlist', (n) => {
      if (!n) return false;
      if (S.lib.playlists[n]) { toast('Cette playlist existe déjà'); return false; }
      N.playlist('create', n, '');
      if (t) { N.playlist('add', n, String(t.id)); toast('Ajouté à « ' + n + ' »'); }
      N.reload();
    });
  }
  function playlistMenu(name) {
    sheetMenu('<h3>' + esc(name) + '</h3>', [
      { icon: 'play', label: 'Lire', run: () => { const tr = ids(S.lib.playlists[name] || []); if (tr.length) N.play(JSON.stringify(tr.map((x) => x.id)), 0); } },
      { icon: 'edit', label: 'Renommer', run: () => prompt('Renommer', name, '', (n) => { if (!n || n === name) return; N.playlist('rename', name, n); N.reload(); }) },
      { icon: 'trash', label: 'Supprimer', run: () => confirmBox('Supprimer « ' + name + ' » ?', 'Les chansons restent sur ton téléphone.', 'Supprimer', () => { N.playlist('delete', name, ''); N.reload(); }, true) },
    ]);
  }
  function addMusicMenu() {
    sheetMenu('<h3>Ajouter plus de musique</h3>', [
      { icon: 'plus', label: 'Importer des fichiers audio', run: () => N.importFiles() },
      { icon: 'viz', label: 'Convertir une vidéo en audio', run: () => N.convertVideo() },
      { icon: 'share', label: 'Importer depuis un lien direct', run: linkBox },
    ]);
  }
  function linkBox() {
    modal('Importer depuis un lien direct', '<p>Colle le lien d\'un fichier audio (.mp3, .m4a, .flac…). Il sera téléchargé dans ta bibliothèque.</p><input type="url" id="mIn" placeholder="https://…/musique.mp3">',
      [{ label: 'Annuler', ghost: true }, { label: 'Télécharger', run: (m) => {
        const err = N.download(m.querySelector('#mIn').value.trim());
        toast(err || '⬇ Téléchargement lancé — la musique apparaîtra toute seule');
      } }]);
  }
  function speedBox() {
    if (!S.st.cur) { toast('Lance d\'abord une chanson'); return; }
    const sp = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];
    listBox('Vitesse de lecture', sp.map((v) => (Math.abs(v - S.st.speed) < 0.01 ? '✓  ' : '     ') + v + 'x'), (i) => N.cmd('speed', String(sp[i])));
  }
  function sleepBox() {
    if (!S.st.cur) { toast('Lance d\'abord une chanson'); return; }
    const mins = [0, 5, 10, 15, 30, 45, 60, 90, 120];
    listBox('Arrêter la musique dans…', mins.map((m) => m ? m + ' minutes' : 'Désactiver'), (i) => {
      N.cmd('sleep', String(mins[i]));
      toast(mins[i] ? 'La musique s\'arrêtera dans ' + mins[i] + ' min' : 'Minuterie désactivée');
    });
  }

  // ================================================================ Mini-lecteur
  setI($('miniNext'), 'next');
  let miniKey = '';
  function renderMini() {
    const c = S.st.cur;
    $('mini').classList.toggle('hidden', !c || stack.some((x) => x.id === 'player' || x.id === 'queue' || x.id === 'viz'));
    if (!c) return;
    const key = c.id + c.art;
    if (key !== miniKey) { miniKey = key; $('miniCov').outerHTML = cover(c, 'mcov" id="miniCov', 120); }
    $('miniTitle').textContent = c.title;
    $('miniArtist').textContent = c.artist;
    setI($('miniIcon'), S.st.playing ? 'pause' : 'play');
    const f = S.st.dur ? S.st.pos / S.st.dur : 0;
    $('miniRing').style.strokeDashoffset = String(132 * (1 - f));
  }
  $('miniPlay').onclick = (e) => { e.stopPropagation(); N.cmd('toggle', ''); };
  $('miniNext').onclick = (e) => { e.stopPropagation(); N.cmd('next', ''); };
  $('mini').onclick = () => openPlayer();

  // ================================================================ Lecteur
  setI($('pDown'), 'down'); setI($('pPrev'), 'rew'); setI($('pNext'), 'ff'); setI($('pMore'), 'more');
  setI($('pEq'), 'eq'); setI($('pQueue'), 'queue');
  let pKey = '', wave = [], palette = ['#1c1726', '#dcc4ff'], seeking = false;

  function openPlayer() {
    if (!S.st.cur) return;
    if (!stack.some((x) => x.id === 'player')) openLayer('player', () => { renderMini(); });
    renderMini();
    renderPlayer(true);
  }
  function renderPlayer(force) {
    if ($('player').classList.contains('hidden') && !force) return;
    const c = S.st.cur;
    if (!c) { closeLayer('player'); return; }
    const key = c.id + c.art;
    if (key !== pKey) {
      pKey = key;
      $('pCov').outerHTML = cover(c, 'pcov" id="pCov', 700);
      wave = J(N.wave(Math.abs(hash(c.id + c.title))), []);
      const pal = J(N.palette(c.id), null);
      palette = pal || ['#1c1726', '#dcc4ff'];
      const r = document.documentElement.style;
      r.setProperty('--pbg', palette[0]); r.setProperty('--pacc', palette[1]);
    }
    $('pTitle').textContent = c.title;
    $('pArtist').textContent = c.artist;
    setI($('pPlay'), S.st.playing ? 'pause' : 'play');
    setI($('pFav'), S.st.fav ? 'heart' : 'heartO');
    $('pFav').style.color = S.st.fav ? '#ff4d8d' : '';
    setI($('pShuffle'), 'shuffle'); $('pShuffle').classList.toggle('off', S.st.mode !== 2);
    setI($('pRepeat'), S.st.mode === 0 ? 'repeat1' : 'repeat'); $('pRepeat').classList.toggle('off', S.st.mode === 2);
    const chips = [];
    if (S.st.sleep > 0) chips.push('<button class="chip" data-c="s">⏾ ' + fmt(S.st.sleep) + '</button>');
    if (Math.abs(S.st.speed - 1) > 0.01) chips.push('<button class="chip" data-c="v">⏩ ' + S.st.speed + 'x</button>');
    $('pChips').innerHTML = chips.join('');
    renderProgress();
  }
  function hash(s) { let h = 7; for (const ch of String(s)) h = (h * 31 + ch.charCodeAt(0)) | 0; return h; }
  function renderProgress() {
    const f = S.st.dur ? S.st.pos / S.st.dur : 0;
    if (!seeking) {
      $('pSeek').value = String(Math.round(f * 1000));
      $('pSeek').style.setProperty('--p', (f * 100) + '%');
      $('pCur').textContent = fmt(S.st.pos);
    }
    $('pDur').textContent = fmt(S.st.dur);
    drawWave(seeking ? +$('pSeek').value / 1000 : f);
  }
  function drawWave(f) {
    const cv = $('pWave'), w = cv.clientWidth, h = 46, dpr = window.devicePixelRatio || 1;
    if (!w || !wave.length) return;
    if (cv.width !== Math.round(w * dpr)) { cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); }
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, w, h);
    const path = () => {
      g.beginPath(); g.moveTo(0, h);
      const step = w / (wave.length - 1);
      let px = 0, py = h - wave[0] * h * 0.95;
      g.lineTo(px, py);
      for (let i = 1; i < wave.length; i++) {
        const x = i * step, y = h - wave[i] * h * 0.95;
        g.quadraticCurveTo(px, py, (px + x) / 2, (py + y) / 2);
        px = x; py = y;
      }
      g.lineTo(w, py); g.lineTo(w, h); g.closePath();
    };
    g.fillStyle = hexA(palette[1], 0.15); path(); g.fill();
    g.save(); g.beginPath(); g.rect(0, 0, w * f, h); g.clip();
    g.fillStyle = hexA(palette[1], 0.45); path(); g.fill(); g.restore();
  }
  function hexA(hex, a) {
    const n = parseInt(String(hex).slice(1), 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')';
  }
  $('pSeek').addEventListener('input', () => {
    seeking = true;
    const v = +$('pSeek').value / 1000;
    $('pSeek').style.setProperty('--p', (v * 100) + '%');
    $('pCur').textContent = fmt(v * S.st.dur);
    drawWave(v);
  });
  $('pSeek').addEventListener('change', () => { seeking = false; N.cmd('seek', String(Math.round(+$('pSeek').value / 1000 * S.st.dur))); });
  $('pWave').addEventListener('click', (e) => {
    const r = $('pWave').getBoundingClientRect();
    N.cmd('seek', String(Math.round((e.clientX - r.left) / r.width * S.st.dur)));
  });
  $('pDown').onclick = () => closeLayer('player');
  $('pPlay').onclick = () => N.cmd('toggle', '');
  $('pPrev').onclick = () => N.cmd('prev', '');
  $('pNext').onclick = () => N.cmd('next', '');
  $('pShuffle').onclick = () => N.cmd('mode', S.st.mode === 2 ? '1' : '2');
  $('pRepeat').onclick = () => N.cmd('mode', S.st.mode === 0 ? '1' : '0');
  $('pFav').onclick = () => {
    const c = S.st.cur;
    if (!c || c.id < 0) { toast('Impossible pour un fichier externe'); return; }
    const on = N.fav(c.id);
    toast(on ? '♥ Ajouté aux favoris' : 'Retiré des favoris');
    S.lib.favs = on ? S.lib.favs.concat([c.id]) : S.lib.favs.filter((x) => x !== c.id);
  };
  $('pMore').onclick = () => { if (S.st.cur) trackMenu(S.byId.get(S.st.cur.id) || S.st.cur); };
  $('pEq').onclick = eqBox;
  $('pLyrics').onclick = () => openLyrics();
  $('pQueue').onclick = openQueue;
  $('pChips').onclick = (e) => { const c = e.target.closest('[data-c]'); if (c) (c.dataset.c === 's' ? sleepBox : speedBox)(); };

  // ---------- Égaliseur
  function eqBox() {
    const e = J(N.eq(), { ok: false });
    if (!e.ok) { toast('Lance une chanson pour ouvrir l\'égaliseur'); return; }
    const db = (l) => (l > 0 ? '+' : '') + Math.round(l / 100) + ' dB';
    const hz = (h) => h >= 1000 ? (h / 1000).toFixed(1) + ' kHz' : h + ' Hz';
    let h = '<label class="switch">Activer l\'égaliseur<input type="checkbox" id="eqOn"' + (e.on ? ' checked' : '') + '></label>';
    if (e.presets.length) h += '<button class="btn ghost" id="eqPre">Préréglages ▾</button>';
    e.bands.forEach((b, i) => h += '<div class="eqrow"><label id="eqL' + i + '">' + hz(b.hz) + '   ' + db(b.level) + '</label><input type="range" data-b="' + i + '" min="' + e.min + '" max="' + e.max + '" value="' + b.level + '"></div>');
    if (e.bass >= 0) h += '<div class="eqrow"><label>Basses renforcées</label><input type="range" id="eqBass" min="0" max="1000" value="' + e.bass + '"></div>';
    modal('Égaliseur', h, [{ label: 'OK' }], (m) => {
      const on = m.querySelector('#eqOn');
      const turnOn = () => { if (!on.checked) { on.checked = true; N.eqSet('on', 1, 0); } };
      on.onchange = () => N.eqSet('on', on.checked ? 1 : 0, 0);
      m.querySelectorAll('[data-b]').forEach((r) => r.oninput = () => {
        const i = +r.dataset.b;
        m.querySelector('#eqL' + i).textContent = hz(e.bands[i].hz) + '   ' + db(+r.value);
        N.eqSet('band', i, +r.value); turnOn();
      });
      const bass = m.querySelector('#eqBass');
      if (bass) bass.oninput = () => { N.eqSet('bass', +bass.value, 0); turnOn(); };
      const pre = m.querySelector('#eqPre');
      if (pre) pre.onclick = () => {
        closeLayer('modalBack');
        listBox('Préréglages', e.presets, (i) => { N.eqSet('preset', i, 0); N.eqSet('on', 1, 0); setTimeout(eqBox, 150); });
      };
    });
  }

  // ================================================================ File d'attente
  setI($('qPlay'), 'pause'); setI($('qLocate'), 'locate'); setI($('qClear'), 'broom');
  $('qModes').innerHTML = MODE_ICONS.map((ic, i) => '<button data-mode="' + i + '">' + I(ic) + '</button>').join('');
  function openQueue() {
    if (!S.st.cur) return;
    openLayer('queue', renderMini);
    renderQueue(true);
    setTimeout(locate, 50);
  }
  let qKey = '', drag = null;
  function renderQueue(force) {
    if ($('queue').classList.contains('hidden') && !force) return;
    const c = S.st.cur;
    if (!c) { closeLayer('queue'); return; }
    $('qCov').outerHTML = cover(c, 'mcov" id="qCov', 120);
    $('qTitle').textContent = c.title;
    setI($('qPlay'), S.st.playing ? 'pause' : 'play');
    $('qProg').style.width = (S.st.dur ? S.st.pos / S.st.dur * 100 : 0) + '%';
    $('qMode').textContent = MODES[S.st.mode];
    $('qModes').querySelectorAll('[data-mode]').forEach((b) => b.classList.toggle('on', +b.dataset.mode === S.st.mode));
    if (drag) return;
    const key = JSON.stringify(S.st.queue.map((t) => t.id)) + S.st.qpos;
    if (key === qKey && !force) return;
    qKey = key;
    $('qList').innerHTML = S.st.queue.map((t, i) => '<div class="row ' + (i === S.st.qpos ? 'cur' : i < S.st.qpos ? 'past' : 'next') + '" data-q="' + i + '">'
      + cover(t, 'cov', 120) + '<div class="txt"><b>' + (i === S.st.qpos ? '♪  ' : '') + esc(t.title) + '</b><small>' + esc(t.artist) + '</small></div>'
      + (i === S.st.qpos ? '' : '<button class="handle" data-h="' + i + '">≡</button>') + '</div>').join('');
  }
  function locate() { const el = $('qList').querySelector('.row.cur'); if (el) el.scrollIntoView({ block: 'start' }); }
  $('qLocate').onclick = locate;
  $('qClear').onclick = () => confirmBox('Vider la file d\'attente ?', 'Seul le titre en cours sera gardé.', 'Vider', () => N.cmd('clear', ''));
  $('qPlay').onclick = () => N.cmd('toggle', '');
  $('qGrip').onclick = () => closeLayer('queue');
  $('qHead').onclick = (e) => { if (!e.target.closest('#qPlay')) closeLayer('queue'); };
  $('qModes').onclick = (e) => { const b = e.target.closest('[data-mode]'); if (b) { N.cmd('mode', b.dataset.mode); toast(MODES[+b.dataset.mode]); } };
  $('qList').addEventListener('click', (e) => {
    if (e.target.closest('.handle')) return;
    const r = e.target.closest('[data-q]');
    if (r) N.cmd('jump', r.dataset.q);
  });
  // Glisser la poignée ≡ pour réorganiser
  $('qList').addEventListener('pointerdown', (e) => {
    const hd = e.target.closest('[data-h]');
    if (!hd) return;
    e.preventDefault();
    const row = hd.closest('.row');
    drag = { from: +hd.dataset.h, row, startY: e.clientY, h: row.offsetHeight };
    row.classList.add('drag');
    hd.setPointerCapture(e.pointerId);
  });
  $('qList').addEventListener('pointermove', (e) => {
    if (!drag) return;
    const list = $('qList'), rows = [...list.querySelectorAll('.row')];
    const lr = list.getBoundingClientRect();
    if (e.clientY < lr.top + 40) list.scrollTop -= 12; else if (e.clientY > lr.bottom - 40) list.scrollTop += 12;
    const over = rows.find((r) => { const b = r.getBoundingClientRect(); return e.clientY >= b.top && e.clientY < b.bottom; });
    if (!over || over === drag.row) return;
    const oi = rows.indexOf(over), di = rows.indexOf(drag.row);
    if (oi > di) over.after(drag.row); else over.before(drag.row);
  });
  const endDrag = () => {
    if (!drag) return;
    const rows = [...$('qList').querySelectorAll('.row')];
    const to = rows.indexOf(drag.row), from = drag.from;
    drag.row.classList.remove('drag');
    drag = null; qKey = '';
    if (to >= 0 && to !== from) N.cmd('move', from + ',' + to); else renderQueue(true);
  };
  $('qList').addEventListener('pointerup', endDrag);
  $('qList').addEventListener('pointercancel', endDrag);

  // ================================================================ Paroles
  let lyr = null, lyrId = null;
  function openLyrics() {
    const c = S.st.cur;
    if (!c) return;
    lyrId = c.id;
    lyr = J(N.lyrics(c.id), []);
    if (!lyr.length) { editLyrics(c, ''); return; }
    const synced = lyr.some((l) => l.t >= 0);
    page('lyrics', '<div class="ebar"><button class="pgback" id="pgB">' + I('back') + '</button><h2>' + esc(c.title) + '</h2><button class="btn ghost" id="lyE">Modifier</button></div>'
      + '<div class="lyrics' + (synced ? '' : ' plain') + '" id="lyBox">' + lyr.map((l, i) => '<p data-l="' + i + '">' + (esc(l.l) || '&nbsp;') + '</p>').join('') + '</div>', (pg) => {
      pg.querySelector('#lyE').onclick = () => editLyrics(c, N.lyricsRaw(c.id));
      if (synced) pg.querySelector('#lyBox').onclick = (e) => {
        const p = e.target.closest('[data-l]');
        if (p && lyr[+p.dataset.l].t >= 0) N.cmd('seek', String(lyr[+p.dataset.l].t));
      };
    });
    syncLyrics();
  }
  let lyrOn = -1;
  function syncLyrics() {
    if (!lyr || $('page').dataset.kind !== 'lyrics' || $('page').classList.contains('hidden') || !S.st.cur || S.st.cur.id !== lyrId) return;
    let on = -1;
    lyr.forEach((l, i) => { if (l.t >= 0 && l.t <= S.st.pos + 250) on = i; });
    if (on === lyrOn) return;
    lyrOn = on;
    $('page').querySelectorAll('.lyrics p').forEach((p, i) => p.classList.toggle('on', i === on));
    const el = $('page').querySelector('.lyrics p.on');
    if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
  function editLyrics(c, text) {
    page('lyricsEdit', '<div class="ebar"><button class="pgback" id="pgB">' + I('back') + '</button><h2>Paroles</h2><button class="btn" id="lyS">Sauvegarder</button></div>'
      + '<p style="color:var(--sub);margin:6px 4px 12px">' + esc(c.title) + ' — colle les paroles ici. Les lignes minutées façon .lrc ([01:23.45]) défilent en rythme.</p>'
      + '<textarea class="lyr" id="lyT" placeholder="Colle les paroles ici…">' + esc(text) + '</textarea>', (pg) => {
      pg.querySelector('#lyS').onclick = () => {
        N.saveLyrics(c.id, pg.querySelector('#lyT').value);
        toast('✓ Paroles enregistrées');
        closeLayer('page');
        if (pg.querySelector('#lyT').value.trim()) openLyrics();
      };
    });
  }

  // ================================================================ Pages (paramètres, édition…)
  function page(kind, html, bind) {
    const pg = $('page');
    if (stack.some((x) => x.id === 'page')) closeLayer('page');
    pg.dataset.kind = kind;
    pg.innerHTML = html;
    pg.scrollTop = 0;
    openLayer('page', () => { pg.dataset.kind = ''; });
    const b = pg.querySelector('#pgB');
    if (b) b.onclick = () => closeLayer('page');
    if (bind) bind(pg);
  }

  function openEdit(t) {
    page('edit', '<div class="ebar"><button class="pgback" id="pgB">' + I('back') + '</button><h2>Éditer les infos</h2><button class="btn" id="edS">Sauvegarder</button></div>'
      + '<div style="padding:0 18px"><div class="flabel">Nom</div><input class="field" id="edT" placeholder="Saisir le titre" value="' + esc(t.title) + '">'
      + '<div class="flabel">Artiste</div><input class="field" id="edA" placeholder="Saisir le nom d\'artiste" value="' + esc(t.artist === 'Artiste inconnu' ? '' : t.artist) + '">'
      + '<div class="flabel">Album</div><input class="field" id="edL" placeholder="Saisir le nom de l\'album" value="' + esc(t.album === 'Album inconnu' ? '' : t.album) + '">'
      + '<div class="flabel">Couverture</div>' + cover(t, 'ecov" id="edC', 300)
      + '<div style="display:flex;gap:10px;margin-top:14px"><button class="btn ghost" id="edP">✎  Modifier</button>' + (t.custom ? '<button class="btn ghost" id="edR" style="color:var(--sub)">Photo d\'origine</button>' : '') + '</div>'
      + '<div class="flabel">Chemin du fichier</div><div class="path">' + esc(t.path) + '</div></div>', (pg) => {
      pg.querySelector('#edS').onclick = () => {
        N.edit(t.id, pg.querySelector('#edT').value, pg.querySelector('#edA').value, pg.querySelector('#edL').value);
        toast('✓ Sauvegardé');
        closeLayer('page');
      };
      pg.querySelector('#edP').onclick = () => N.pickCover(t.id);
      const r = pg.querySelector('#edR');
      if (r) r.onclick = () => { N.resetCover(t.id); toast('Photo d\'origine remise'); };
    });
  }

  function openSettings(refresh) {
    const lm = Math.floor(N.listenMs() / 60000);
    const th = THEMES[+N.pref('theme', '0')] || THEMES[0], bg = BGS[+N.pref('themeBg', '0')] || BGS[0];
    const row = (ic, label, val, act) => '<div class="srow" data-a="' + act + '"><span class="si">' + ic + '</span><span class="sl">' + esc(label) + '</span><span class="sv">' + esc(val) + '</span><span class="sc">›</span></div>';
    const html = '<button class="pgback" id="pgB">' + I('back') + '</button><div class="pgtitle">Paramètres</div>'
      + '<div class="card">' + row('◷', 'Temps de lecture', Math.floor(lm / 60) + 'h ' + (lm % 60) + 'm', 'stats') + row('☁', 'Sauvegarder & restaurer', 'Fichier', 'backup') + '</div>'
      + '<div class="card">' + row('👕', 'Thème', th[0] + ' / ' + bg[0], 'theme') + row('⏰', 'Minuterie de sommeil', S.st.sleep > 0 ? fmt(S.st.sleep) : 'Désactivée', 'sleep')
      + row('🚫', 'Publicités', 'Aucune ✓', 'ads') + '</div>'
      + '<div class="card">' + row('◌', 'Fichiers masqués', S.lib.hidden.length + ' fichiers', 'hidden') + row('🗑', 'Récemment supprimé', S.lib.trash.length + ' fichiers', 'trash')
      + row('🎧', 'Paramètres de lecture', '', 'play') + row('🔔', 'Paramètres de notifications', '', 'notif') + row('🌐', 'Langue', 'Français', 'lang') + '</div>'
      + '<div class="card">' + row('🔗', 'Importer depuis un lien direct', '', 'link') + row('⟳', 'Rescanner la bibliothèque', '', 'rescan')
      + row('🛡', 'Sécurité', 'Version officielle ✓', 'sec') + row('ⓘ', 'À propos', N.version(), 'about') + '</div>';
    if (refresh && $('page').dataset.kind === 'settings') { $('page').innerHTML = html; $('page').querySelector('#pgB').onclick = () => closeLayer('page'); return; }
    page('settings', html);
  }
  $('page').addEventListener('click', (e) => {
    const r = e.target.closest('[data-a]');
    if (!r || $('page').dataset.kind !== 'settings') return;
    settingsAction(r.dataset.a);
  });
  function settingsAction(a) {
    switch (a) {
      case 'stats': {
        const top = S.lib.songs.filter((t) => t.plays > 0).sort((x, y) => y.plays - x.plays).slice(0, 10);
        const lm = Math.floor(N.listenMs() / 60000);
        alertBox('Temps de lecture', 'Temps d\'écoute total : ' + Math.floor(lm / 60) + 'h ' + (lm % 60) + 'm\n\nTes plus écoutées :\n'
          + (top.length ? top.map((t, i) => (i + 1) + '. ' + t.title + '  —  ' + t.plays + (t.plays > 1 ? ' écoutes' : ' écoute')).join('\n') : 'Rien encore — lance une chanson !'));
        break;
      }
      case 'backup':
        modal('Sauvegarder & restaurer', '<p>Favoris, playlists, modifications, paroles et réglages — dans un fichier sur ton téléphone. Pas besoin de compte ni d\'Internet.</p>',
          [{ label: 'Restaurer', ghost: true, run: () => N.backup(false) }, { label: 'Sauvegarder', run: () => N.backup(true) }]);
        break;
      case 'theme': {
        const cur = +N.pref('theme', '0'), cb = +N.pref('themeBg', '0');
        sheet('<h3>Thème</h3><div style="padding:0 26px"><div style="color:var(--sub);font-weight:700">Couleur</div><div class="dots">'
          + THEMES.map((t, i) => '<button data-th="' + i + '" style="background:linear-gradient(135deg,' + t[1] + ',' + t[2] + ')">' + (i === cur ? '✓' : '') + '</button>').join('')
          + '</div><div style="color:var(--sub);font-weight:700">Fond</div></div>'
          + BGS.map((b, i) => '<button class="sitem' + (i === cb ? ' sel' : '') + '" data-bg="' + i + '"><span class="si">' + (i === cb ? '●' : '○') + '</span>' + b[0] + '</button>').join(''), (sh) => {
          sh.querySelectorAll('[data-th]').forEach((b) => b.onclick = () => { N.setPref('theme', b.dataset.th); applyTheme(); closeLayer('sheetBack'); openSettings(true); });
          sh.querySelectorAll('[data-bg]').forEach((b) => b.onclick = () => { N.setPref('themeBg', b.dataset.bg); applyTheme(); closeLayer('sheetBack'); openSettings(true); });
        });
        break;
      }
      case 'sleep': sleepBox(); break;
      case 'ads': toast('NOX Player n\'a pas de pubs, et n\'en aura jamais.'); break;
      case 'hidden':
        if (!S.lib.hidden.length) { toast('Aucun fichier masqué'); break; }
        listBox('Fichiers masqués — touche pour réafficher', S.lib.hidden.map((t) => t.title + '  —  ' + t.artist), (i) => {
          N.setFlag('hidden', S.lib.hidden[i].id, false); toast('« ' + S.lib.hidden[i].title + ' » est de retour');
        });
        break;
      case 'trash':
        if (!S.lib.trash.length) { toast('La corbeille est vide'); break; }
        modal('Récemment supprimé', '<div class="mlist">' + S.lib.trash.map((t, i) => '<button data-l="' + i + '">' + esc(t.title + '  —  ' + t.artist) + '</button>').join('') + '</div>',
          [{ label: 'Tout supprimer', danger: true, run: () => N.deleteForever(JSON.stringify(S.lib.trash.map((t) => t.id))) }, { label: 'Fermer', ghost: true }],
          (m) => m.querySelectorAll('[data-l]').forEach((b) => b.onclick = () => {
            const t = S.lib.trash[+b.dataset.l];
            closeLayer('modalBack');
            modal(t.title, '', [
              { label: '🗑 Supprimer du téléphone', danger: true, run: () => N.deleteForever(JSON.stringify([t.id])) },
              { label: '↩ Restaurer', run: () => { N.setFlag('trash', t.id, false); toast('Restauré'); } }]);
          }));
        break;
      case 'play': {
        const opts = [['pause_unplug', 'Pause quand les écouteurs sont débranchés', true], ['skip_short', 'Masquer les sons de moins de 30 secondes', false],
          ['auto_audio', 'Nouvelles vidéos du téléphone → audio automatiquement', false]];
        modal('Paramètres de lecture', opts.map((o) => '<label class="switch">' + esc(o[1]) + '<input type="checkbox" data-k="' + o[0] + '"' + (N.flag(o[0], o[2]) ? ' checked' : '') + '></label>').join(''),
          [{ label: 'OK' }], (m) => m.querySelectorAll('[data-k]').forEach((c) => c.onchange = () => N.setFlagPref(c.dataset.k, c.checked)));
        break;
      }
      case 'notif': N.notificationSettings(); break;
      case 'lang': toast('NOX Player est en français.'); break;
      case 'link': linkBox(); break;
      case 'rescan': N.reload(); toast('Scan en cours…'); break;
      case 'sec':
        alertBox('Sécurité', 'Cette copie est la version officielle de NOX Player, signée avec la clé privée de math_0235.\n\n'
          + '• Une copie modifiée refuse de démarrer.\n• Android refuse toute mise à jour qui n\'est pas signée avec la même clé.\n'
          + '• L\'appli ne se connecte à aucun serveur : personne ne peut la contrôler à distance.\n\nTélécharge NOX Player uniquement depuis la page officielle.');
        break;
      case 'about': alertBox('NOX Player', 'Lecteur de musique hors ligne — sans pub, sans compte, sans Internet.\nInterface en JavaScript, cœur en C++.\n\nVersion ' + N.version() + '\nfait par math_0235\n© math_0235 — tous droits réservés'); break;
    }
  }

  // ================================================================ Visualiseur
  let vizData = new Array(48).fill(0), vizIdle = true, vizRaf = 0;
  setI($('vizClose'), 'close');
  function openViz() {
    openLayer('viz', () => { N.viz(false); cancelAnimationFrame(vizRaf); renderMini(); });
    const c = S.st.cur;
    if (c) { $('vizCov').outerHTML = cover(c, 'vcov" id="vizCov', 400); $('vizTitle').textContent = c.title; $('vizArtist').textContent = c.artist; }
    vizData = new Array(48).fill(0);
    vizIdle = true;
    N.viz(true);
    renderMini();
    const loop = () => { drawViz(); vizRaf = requestAnimationFrame(loop); };
    loop();
  }
  $('vizClose').onclick = () => closeLayer('viz');
  $('viz').addEventListener('click', (e) => { if (!e.target.closest('#vizClose')) N.cmd('toggle', ''); });
  function drawViz() {
    const cv = $('vizCanvas'), w = cv.clientWidth, h = cv.clientHeight, dpr = window.devicePixelRatio || 1;
    if (cv.width !== Math.round(w * dpr)) { cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); }
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, w, h);
    const cs = getComputedStyle(document.documentElement);
    const gr = g.createLinearGradient(0, h, 0, h * 0.35);
    gr.addColorStop(0, cs.getPropertyValue('--accent').trim()); gr.addColorStop(1, cs.getPropertyValue('--accent2').trim());
    g.fillStyle = gr;
    const n = 48, slot = w / n, bw = slot * 0.62, base = h - 30, maxH = h * 0.45, t = Date.now() / 1000;
    for (let i = 0; i < n; i++) {
      let v = vizIdle ? 0.15 + 0.12 * Math.sin(t * 2.2 + i * 0.45) : vizData[i];
      if (!vizIdle) vizData[i] *= 0.94;
      const bh = Math.max(bw, v * maxH), x = i * slot + (slot - bw) / 2;
      g.beginPath();
      if (g.roundRect) g.roundRect(x, base - bh, bw, bh, bw / 2); else g.rect(x, base - bh, bw, bh);
      g.fill();
    }
  }

  // ================================================================ Progression
  function progress(d) {
    if (d.done) { $('modal').dataset.lock = ''; if (stack.length && stack[stack.length - 1].id === 'modalBack' && $('modal').dataset.prog) closeLayer('modalBack'); $('modal').dataset.prog = ''; return; }
    if (!$('modal').dataset.prog) {
      modal(d.title, '<div class="bar"><i id="prgBar"></i></div><p id="prgTxt" style="margin-top:10px">0 %</p>', []);
      $('modal').dataset.prog = '1'; $('modal').dataset.lock = '1';
    }
    const b = $('prgBar');
    if (b) { b.style.width = d.pct + '%'; $('prgTxt').textContent = d.pct + ' %'; }
  }

  // ================================================================ Événements venant du téléphone
  window.NOX_event = function (type, d) {
    switch (type) {
      case 'library': setLibrary(d); break;
      case 'perm': S.perm = false; render(); break;
      case 'state': case 'tick': {
        const prevCur = S.st.cur ? S.st.cur.id : null;
        S.st = d && d.cur ? d : { cur: null };
        if (type === 'state') {
          if ((S.st.cur ? S.st.cur.id : null) !== prevCur) render(); else renderMini();
          renderPlayer(); renderQueue();
          if (!S.st.cur) { closeLayer('player'); closeLayer('queue'); }
        } else { renderMini(); renderProgress(); renderQueue(); }
        syncLyrics();
        break;
      }
      case 'viz': if (d) { vizIdle = false; for (let i = 0; i < 48; i++) vizData[i] = Math.max(vizData[i] || 0, d[i] || 0); } else vizIdle = true; break;
      case 'cover': {
        const t = S.byId.get(d.id);
        if (t) Object.assign(t, d);
        if ($('page').dataset.kind === 'edit') openEdit(t || d);
        pKey = ''; miniKey = ''; renderPlayer();
        break;
      }
      case 'progress': progress(d); break;
      case 'open': if (d === 'player') setTimeout(openPlayer, 300); break;
      case 'resume': render(); break;
    }
  };

  // ================================================================ Démarrage
  applyTheme();
  render();
  const st = J(N.state(), { cur: null });
  if (st && st.cur) window.NOX_event('state', st);
})();
