/* Akış web uygulaması: sayfalar, müzik çubuğu, fotoğraf görüntüleyici, yükleme paneli, TV yön tuşları. */
(function () {
  'use strict';

  var C = window.AKIS || {};
  var T = C.t || {};
  var P = window.AkisPlayer;
  function t(k) {
    var s = T[k] || k;
    for (var i = 1; i < arguments.length; i++) s = s.split('{' + (i - 1) + '}').join(arguments[i]);
    return s;
  }
  function enc(s) { return encodeURIComponent(s); }
  function esc(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function $(sel, el) { return (el || document).querySelector(sel); }
  function get(k, d) { try { var v = localStorage.getItem('akis.' + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } }
  function put(k, v) { try { localStorage.setItem('akis.' + k, JSON.stringify(v)); } catch (e) { } }
  var fmt = P.fmt;
  function size(b) {
    if (!b) return '';
    if (b >= 1073741824) return (b / 1073741824).toFixed(2) + ' GB';
    if (b >= 1048576) return (b / 1048576).toFixed(1) + ' MB';
    return Math.max(1, Math.round(b / 1024)) + ' KB';
  }
  function plural(base, n) { return t(n === 1 ? base + '_one' : base + '_other', n); }
  function api(url) {
    return fetch(url, { cache: 'no-store' }).then(function (r) {
      if (r.status === 403 || r.redirected) { location.reload(); throw new Error('auth'); }
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.text();
    }).then(function (body) {
      if (typeof lastBody !== 'undefined') lastBody[url] = body;
      return JSON.parse(body);
    });
  }

  var ICO = {
    folder: '<svg viewBox="0 0 24 24"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>',
    v: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M10 9.5v5l4-2.5z"/></svg>',
    a: '<svg viewBox="0 0 24 24"><path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/></svg>',
    i: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M21 16l-5-5-9 9"/></svg>',
    f: '<svg viewBox="0 0 24 24"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>',
    file: '<svg viewBox="0 0 24 24"><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M14 3v6h6"/></svg>',
    drive: '<svg viewBox="0 0 24 24"><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M5 13l2-8h10l2 8M7 17h.01"/></svg>',
    recent: '<svg viewBox="0 0 24 24"><path d="M12 3l1.9 5.8H20l-5 3.6 1.9 5.8-4.9-3.6-4.9 3.6 1.9-5.8-5-3.6h6.1z"/></svg>',
    hist: '<svg viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l3 2"/></svg>',
    unw: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M10 9v6l5-3z"/></svg>',
    grid: '<svg viewBox="0 0 24 24"><rect x="4" y="4" width="7" height="7" rx="1"/><rect x="13" y="4" width="7" height="7" rx="1"/><rect x="4" y="13" width="7" height="7" rx="1"/><rect x="13" y="13" width="7" height="7" rx="1"/></svg>',
    list: '<svg viewBox="0 0 24 24"><path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/></svg>',
    x: '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>',
    left: '<svg viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></svg>',
    right: '<svg viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></svg>',
    play: '<svg viewBox="0 0 24 24"><path d="M8 5.5v13l10.5-6.5z"/></svg>',
    pause: '<svg viewBox="0 0 24 24"><path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"/></svg>',
    prev: '<svg viewBox="0 0 24 24"><path d="M19 5.5v13L9 12zM5 5h2.5v14H5z" fill="currentColor" stroke="none"/></svg>',
    next: '<svg viewBox="0 0 24 24"><path d="M5 5.5v13L15 12zM16.5 5H19v14h-2.5z" fill="currentColor" stroke="none"/></svg>',
    shuffle: '<svg viewBox="0 0 24 24"><path d="M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/></svg>',
    queue: '<svg viewBox="0 0 24 24"><path d="M3 6h13M3 12h13M3 18h9M17 15v6l4-3z"/></svg>',
    slides: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="12" rx="2"/><path d="M10 9v4l3.5-2z"/><path d="M8 21h8"/></svg>',
    dl: '<svg viewBox="0 0 24 24"><path d="M12 4v12M6 10l6 6 6-6M4 20h16"/></svg>',
    info: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5h.01"/></svg>',
    check: '<svg viewBox="0 0 24 24"><path d="M5 12l5 5L20 7"/></svg>',
    cam: '<svg viewBox="0 0 24 24"><rect x="3" y="7" width="13" height="10" rx="2"/><path d="M16 11l5-3v8l-5-3z"/></svg>',
    apps: '<svg viewBox="0 0 24 24"><rect x="4" y="4" width="6" height="6" rx="1.5"/><rect x="14" y="4" width="6" height="6" rx="1.5"/><rect x="4" y="14" width="6" height="6" rx="1.5"/><rect x="14" y="14" width="6" height="6" rx="1.5"/></svg>',
    battery: '<svg viewBox="0 0 24 24"><rect x="3" y="7" width="16" height="10" rx="2"/><path d="M21 11v2M6 10v4M9 10v4"/></svg>',
    screen: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/></svg>',
    mic: '<svg viewBox="0 0 24 24"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg>',
    bell: '<svg viewBox="0 0 24 24"><path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 20a2 2 0 0 0 4 0"/></svg>',
    link: '<svg viewBox="0 0 24 24"><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/></svg>'
  };
  var CAT_ICON = { v: ICO.v, a: ICO.a, i: ICO.i, f: ICO.folder, 'h:u': ICO.unw, 'h:c': ICO.hist, r: ICO.recent, 'pd:': ICO.drive };

  var view = $('#view');
  var toastEl = $('#toast'), toastTimer = 0;
  function toast(s) {
    toastEl.textContent = s; toastEl.classList.add('on');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { toastEl.classList.remove('on'); }, 2600);
  }
  window.AkisToast = toast;

  // ======================= Üst çubuk =======================
  $('#brandName').textContent = C.name || 'Akış';
  var q = $('#q');
  q.placeholder = t('search_ph');
  if (!C.search) $('#searchForm').hidden = true;
  if (C.admin) { var ab = $('#adminBtn'); ab.hidden = false; ab.setAttribute('aria-label', t('ad_title')); }
  var upBtn = $('#upBtn');
  if (C.upload) { upBtn.hidden = false; $('#upLabel').textContent = t('upload'); upBtn.addEventListener('click', function () { Upload.open(); }); }

  var themeMeta = document.querySelector('meta[name="theme-color"]');
  if (themeMeta) themeMeta.content = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim() || '#000';

  // ======================= Görünüm (yalnızca bu tarayıcı) =======================
  // akis_ui çerezi "dil.tema.mod"; sunucu sayfayı buna göre üretir. Boş parça = telefonu takip et.
  var lookBtn = $('#lookBtn'), lookPanel = $('#lookPanel');
  if (C.look) {
    lookBtn.hidden = false; lookBtn.setAttribute('aria-label', t('look')); lookBtn.title = t('look');
    var lookCur = decodeURIComponent((document.cookie.match(/(?:^|; )akis_ui=([^;]*)/) || [])[1] || '').split('.');
    var lookSel = function (name, label, opts, cur) {
      return '<label>' + esc(t(label)) + '<select class="pill nav" data-k="' + name + '">' +
        [['', t('look_phone')]].concat(opts).map(function (o) {
          return '<option value="' + esc(o[0]) + '"' + (o[0] === (cur || '') ? ' selected' : '') + '>' + esc(o[1]) + '</option>';
        }).join('') + '</select></label>';
    };
    lookPanel.innerHTML = '<h3>' + esc(t('look')) + '</h3>' +
      lookSel('l', 'look_lang', C.langs || [], lookCur[0]) +
      lookSel('t', 'look_theme', C.themes || [], lookCur[1]) +
      lookSel('m', 'look_mode', [['system', t('mode_system')], ['light', t('mode_light')], ['dark', t('mode_dark')]], lookCur[2]) +
      '<p class="note">' + esc(t('look_note')) + '</p>' +
      '<button class="pill nav" type="button" data-reset>' + esc(t('look_reset')) + '</button>';
    var lookSave = function (v) {
      document.cookie = 'akis_ui=' + encodeURIComponent(v) + '; Path=/; SameSite=Lax; ' + (v.replace(/\./g, '') ? 'Max-Age=31536000' : 'Max-Age=0');
      location.reload();
    };
    lookPanel.addEventListener('change', function () {
      var v = ['l', 't', 'm'].map(function (k) { return lookPanel.querySelector('[data-k="' + k + '"]').value; }).join('.');
      lookSave(v);
    });
    lookPanel.querySelector('[data-reset]').addEventListener('click', function () { lookSave(''); });
    var lookClose = function () { lookPanel.hidden = true; };
    lookBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      lookPanel.hidden = !lookPanel.hidden;
      if (!lookPanel.hidden) lookPanel.querySelector('select').focus();
    });
    document.addEventListener('click', function (e) { if (!lookPanel.hidden && !lookPanel.contains(e.target)) lookClose(); });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !lookPanel.hidden) { lookClose(); lookBtn.focus(); } });
  }

  var qTimer = 0;
  q.addEventListener('input', function () {
    clearTimeout(qTimer);
    qTimer = setTimeout(function () {
      var v = q.value.trim();
      if (v) go('#/s/' + enc(v), true); else if (route().name === 'search') go('#/', true);
    }, 320);
  });
  $('#searchForm').addEventListener('submit', function (e) { e.preventDefault(); var v = q.value.trim(); if (v) go('#/s/' + enc(v)); q.blur(); });
  document.addEventListener('keydown', function (e) {
    if (e.key === '/' && document.activeElement !== q && !player && C.search) { e.preventDefault(); q.focus(); q.select(); }
  });

  // ======================= Yönlendirme =======================
  function go(hash, replace) { if (replace) history.replaceState(null, '', hash); else location.hash = hash; if (replace) render(); }
  function route() {
    var h = location.hash.replace(/^#\/?/, '');
    var qs = '';
    var qi = h.indexOf('?'); if (qi >= 0) { qs = h.slice(qi + 1); h = h.slice(0, qi); }
    var params = {};
    qs.split('&').forEach(function (p) { if (p) { var kv = p.split('='); params[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1] || ''); } });
    var parts = h.split('/');
    var name = parts[0] || 'home';
    var arg = decodeURIComponent(parts.slice(1).join('/'));
    var map = { '': 'home', home: 'home', f: 'folder', s: 'search', v: 'watch', d: 'files', up: 'upload', admin: 'admin', status: 'status', apps: 'apps', cam: 'cam', screen: 'screen', mic: 'mic', inbox: 'inbox', notifs: 'notifs' };
    return { name: map[name] || 'home', arg: arg, p: params, key: location.hash };
  }
  var player = null, list = [], listFolder = '0', scrolls = {}, lastKey = '', softNext = false;
  var sel = null; // seçim modunda: { set: {indeks: true}, last: son tıklanan }

  /** Açık sayfanın verisini alır; öncekiyle aynıysa ekrana dokunmaz, farklıysa odak ve kaydırmayı koruyarak çizer. */
  var lastBody = {};
  function routeApi(r) {
    if (r.name === 'home') return '/api/home';
    if (r.name === 'folder') return '/api/list?id=' + enc(r.arg || '0');
    if (r.name === 'search') return '/api/search?q=' + enc(r.arg);
    if (r.name === 'files') return '/api/files?p=' + enc(r.arg || '');
    return null;
  }
  function softRefresh() {
    var r = route(), url = routeApi(r);
    if (!url) return;
    fetch(url, { cache: 'no-store' }).then(function (x) { return x.text(); }).then(function (body) {
      if (lastBody[url] === body || route().key !== r.key || player) return;
      var a = document.activeElement, idx = a && a.getAttribute && a.getAttribute('data-i'), y = window.scrollY;
      softNext = true;
      render();
      setTimeout(function () {
        window.scrollTo(0, y);
        if (idx != null) { var el = view.querySelector('[data-i="' + idx + '"]'); if (el) el.focus({ preventScroll: true }); }
      }, 120);
    }).catch(function () { });
  }

  /** Telefonda kitaplık değişince (yeni dosya, yükleme) açık sayfayı sessizce yenile. */
  (function watchChanges() {
    var ver = -1;
    function loop() {
      fetch('/api/changes?since=' + ver, { cache: 'no-store' }).then(function (r) { return r.json(); }).then(function (d) {
        var changed = ver >= 0 && d.v !== ver;
        ver = d.v;
        if (changed && !player && $('#lightbox').hidden && !document.querySelector('.props:not([hidden])')) softRefresh();
        setTimeout(loop, 300);
      }).catch(function () { setTimeout(loop, 5000); });
    }
    setTimeout(loop, 1000);
  })();
  window.addEventListener('hashchange', render);
  window.addEventListener('scroll', function () { scrolls[lastKey] = window.scrollY; }, { passive: true });

  function render() {
    var r = route();
    if (r.name === 'upload') { Upload.open(); history.replaceState(null, '', '#/'); r = route(); }
    if (player) { player.destroy(); player = null; }
    if (r.key !== lastKey && sel) Select.stop(true);
    lastKey = r.key;
    Upload.dir = '';
    if (r.name !== 'search' && document.activeElement !== q) q.value = '';
    var soft = softNext; softNext = false;
    if (!soft) view.innerHTML = '<div class="spin"></div>';
    var done = function () {
      if (sel) Select.paint();
      if (soft) return;
      var y = scrolls[r.key];
      window.scrollTo(0, y || 0);
      if (!player) { var f = view.querySelector('.nav'); if (f && usingKeys) f.focus({ preventScroll: true }); }
    };
    var p;
    if (r.name === 'folder') p = renderFolder(r.arg || '0');
    else if (r.name === 'search') { if (document.activeElement !== q) q.value = r.arg; p = renderSearch(r.arg); }
    else if (r.name === 'watch') p = renderWatch(r.arg, r.p['in'] || '');
    else if (r.name === 'files') p = renderFiles(r.arg || '');
    else if (r.name === 'status') p = renderStatus();
    else if (r.name === 'cam') p = renderCam();
    else if (r.name === 'screen') p = renderScreen();
    else if (r.name === 'inbox') p = renderInbox();
    else if (r.name === 'notifs') p = renderNotifs();
    else if (r.name === 'mic') p = renderMic();
    else if (r.name === 'apps') p = renderApps();
    else if (r.name === 'admin') p = window.AkisAdmin.render(view, { t: t, esc: esc, toast: toast, size: size, ICO: ICO });
    else p = renderHome();
    Promise.resolve(p).then(done).catch(function (e) {
      if (e && e.message === 'auth') return;
      view.innerHTML = '<div class="empty">' + esc(t('error_load')) + '</div>';
    });
  }

  // ======================= Kartlar =======================
  var RICH = /\.(apk|pdf)$/i;
  function thumb(it) {
    if (it.k === 'f') {
      var cover = C.thumbs && it.path ? '<img loading="lazy" alt="" class="cover" src="/tf?p=' + enc(it.path) + '" onload="this.classList.add(\'ok\')" onerror="this.remove()">' : '';
      return '<div class="thumb"><div class="icon-badge">' + (CAT_ICON[it.id] || ICO.folder) + '</div>' + cover + '</div>';
    }
    if (!it.k && C.thumbs && it.raw && RICH.test(it.t)) {
      var p = decodeURIComponent(it.raw.split('p=')[1] || '');
      return '<div class="thumb">' + ICO.file + '<img loading="lazy" alt="" class="fit" src="/tf?p=' + enc(p) + '" onload="this.classList.add(\'ok\')" onerror="this.remove()"></div>';
    }
    var img = (it.k === 'v' || it.k === 'i' || it.k === 'a') && it.id
      ? '<img loading="lazy" alt="" src="/t/' + enc(it.id) + '.jpg" onload="this.classList.add(\'ok\')" onerror="this.remove()">' : '';
    var badge = '';
    if (it.k === 'v' && it.d) badge = '<span class="badge">' + fmt(it.d / 1000) + '</span>';
    if (it.ok) badge += '<span class="badge l">✓</span>';
    return '<div class="thumb">' + (ICO[it.k] || ICO.file) + img + badge + '</div>' +
      (it.p ? '<div class="prog"><i style="width:' + Math.round(it.p * 100) + '%"></i></div>' : '');
  }
  function subline(it) {
    if (it.k === 'f') return it.n >= 0 ? plural('items', it.n) : '';
    if (it.p && it.d) return t('left', fmt((it.d - (it.pos || 0)) / 1000));
    if (it.k === 'a') return [it.ar, it.d ? fmt(it.d / 1000) : ''].filter(Boolean).join(' • ');
    if (it.k === 'v') return [it.w ? (it.h >= 2000 ? '4K' : it.h >= 1000 ? '1080p' : it.h >= 700 ? '720p' : it.h + 'p') : '', size(it.sz)].filter(Boolean).join(' • ');
    return size(it.sz);
  }
  function card(it, idx, extra) {
    var cls = 'card nav' + (it.k === 'f' ? ' folder' : '') + (it.k === 'a' ? ' sq' : '');
    return '<a class="' + cls + '" href="' + href(it) + '" data-i="' + idx + '"' + (extra || '') + '>' + thumb(it) +
      '<div class="meta"><div class="title">' + esc(it.t) + '</div><div class="sub">' + esc(subline(it)) + '</div></div></a>';
  }
  function href(it) {
    if (it.k === 'f') return it.path != null ? '#/d/' + enc(it.path) : '#/f/' + enc(it.id);
    if (it.k === 'v') return '#/v/' + enc(it.id) + '?in=' + enc(it.inFolder || listFolder);
    if (it.raw) return it.raw;
    return 'javascript:void 0';
  }
  /** Kartlara tıklama: müzik ve fotoğraflar sayfa değiştirmeden açılır. */
  view.addEventListener('click', function (e) {
    var a = e.target.closest('[data-i]');
    if (!a || !view.contains(a)) return;
    if (sel && a.getAttribute('data-src') !== 'shelf') { e.preventDefault(); Select.toggle(+a.getAttribute('data-i'), e.shiftKey); return; }
    var src = a.getAttribute('data-src') === 'shelf' ? shelves[a.getAttribute('data-shelf')] : list;
    var it = src && src[+a.getAttribute('data-i')];
    if (!it) return;
    if (it.k === 'a') { e.preventDefault(); Music.play(src.filter(function (x) { return x.k === 'a'; }), it); }
    else if (it.k === 'i') { e.preventDefault(); Lightbox.open(src.filter(function (x) { return x.k === 'i'; }), it); }
    else if (!it.k && it.raw && Docs.can(it.t)) { e.preventDefault(); Docs.open(it); }
  });

  // ======================= Ana sayfa =======================
  var shelves = {};
  function shelf(key, items, title, more) {
    shelves[key] = items;
    if (!items || !items.length) return '';
    var h = '<div class="sec"><h2>' + esc(title) + '</h2><span class="sp"></span>' + (more || '') + '</div><div class="shelf">';
    items.forEach(function (it, i) { h += card(it, i, ' data-src="shelf" data-shelf="' + key + '"'); });
    return h + '</div>';
  }
  function renderHome() {
    return api('/api/home').then(function (d) {
      list = []; listFolder = '0'; shelves = {};
      var h = '';
      (d.cont || []).forEach(function (x) { x.inFolder = 'h:c'; });
      h += shelf('cont', d.cont, t('continue'), '<a class="chip nav" href="#/f/h:c">' + esc(t('see_all')) + '</a>');
      h += '<div class="sec"><h2>' + esc(t('library')) + '</h2></div><div class="tiles">';
      (d.cats || []).forEach(function (c) {
        var n = c.n >= 0 ? (c.id === 'v' ? plural('videos', c.n) : c.id === 'a' ? plural('songs', c.n) : c.id === 'i' ? plural('photos', c.n) : c.id === 'f' ? plural('folders', c.n) : '') : '';
        h += '<a class="tile nav" href="#/f/' + enc(c.id) + '"><div class="icon-badge">' + (CAT_ICON[c.id] || ICO.folder) + '</div><b>' + esc(c.t) + '</b><span class="sub">' + esc(n) + '</span></a>';
      });
      if (C.files) h += '<a class="tile nav" href="#/d/"><div class="icon-badge">' + ICO.drive + '</div><b>' + esc(t('all_files')) + '</b><span class="sub">' + esc(t('all_files_sub')) + '</span></a>';
      if (C.apps) h += '<a class="tile nav" href="#/apps"><div class="icon-badge">' + ICO.apps + '</div><b>' + esc(t('apps')) + '</b><span class="sub">APK</span></a>';
      if (C.camera) h += '<a class="tile nav" href="#/cam"><div class="icon-badge">' + ICO.cam + '</div><b>' + esc(t('camera')) + '</b><span class="sub">MJPEG</span></a>';
      if (C.notifs) h += '<a class="tile nav" href="#/notifs"><div class="icon-badge">' + ICO.bell + '</div><b>' + esc(t('notifs')) + '</b><span class="sub">' + esc(t('notifs_sub')) + '</span></a>';
      if (C.inbox) h += '<a class="tile nav" href="#/inbox"><div class="icon-badge">' + ICO.dl + '</div><b>' + esc(t('inbox')) + '</b><span class="sub">' + esc(t('inbox_sub')) + '</span></a>';
      if (C.screen) h += '<a class="tile nav" href="#/screen"><div class="icon-badge">' + ICO.screen + '</div><b>' + esc(t('screen')) + '</b><span class="sub">MJPEG</span></a>';
      if (C.mic) h += '<a class="tile nav" href="#/mic"><div class="icon-badge">' + ICO.mic + '</div><b>' + esc(t('mic')) + '</b><span class="sub">WAV</span></a>';
      if (C.status) h += '<a class="tile nav" href="#/status"><div class="icon-badge">' + ICO.battery + '</div><b>' + esc(t('status')) + '</b><span class="sub" id="batTile"></span></a>';
      h += '</div>';
      if (d.recent && d.recent.length) {
        var flt = get('recentFilter', 'all');
        var items = d.recent.filter(function (x) { return flt === 'all' || x.k === flt; });
        h += '<div class="sec"><h2>' + esc(t('recent')) + '</h2><span class="sp"></span><div class="chips">' +
          ['all', 'v', 'i'].map(function (k) { return '<button class="chip nav' + (flt === k ? ' on' : '') + '" data-rf="' + k + '">' + esc(t(k === 'all' ? 'all' : k === 'v' ? 'videos_short' : 'photos_short')) + '</button>'; }).join('') +
          '</div></div><div class="grid">';
        list = items; listFolder = 'r';
        items.forEach(function (it, i) { h += card(it, i); });
        h += '</div>';
      }
      view.innerHTML = h || '<div class="empty">' + esc(t('empty')) + '</div>';
      view.querySelectorAll('[data-rf]').forEach(function (b) {
        b.addEventListener('click', function () { put('recentFilter', b.getAttribute('data-rf')); renderHome(); });
      });
    });
  }

  // ======================= Klasör =======================
  function sortItems(items, how) {
    var a = items.slice();
    var coll = new Intl.Collator(C.lang || undefined, { numeric: true, sensitivity: 'base' });
    var key = { name: function (x, y) { return coll.compare(x.t, y.t); }, date: function (x, y) { return (y.dt || 0) - (x.dt || 0); }, size: function (x, y) { return (y.sz || 0) - (x.sz || 0); } }[how];
    if (!key) return a;
    return a.sort(function (x, y) { if ((x.k === 'f') !== (y.k === 'f')) return x.k === 'f' ? -1 : 1; return key(x, y); });
  }
  function toolbar(crumbsHtml, opts) {
    var mode = get('view', 'grid'), sort = get('sort', 'default');
    var h = '<div class="bar"><nav class="crumbs">' + crumbsHtml + '</nav>';
    if (opts.sort !== false) {
      h += '<select class="pill nav" id="sortSel" aria-label="' + esc(t('sort')) + '">' +
        ['default', 'name', 'date', 'size'].map(function (s) { return '<option value="' + s + '"' + (s === sort ? ' selected' : '') + '>' + esc(t('sort_' + s)) + '</option>'; }).join('') + '</select>';
    }
    h += '<button class="pill nav' + (sel ? ' acc' : '') + '" data-sel="1">' + ICO.check + esc(t('select')) + '</button>';
    h += '<div class="seg"><button class="nav' + (mode === 'grid' ? ' on' : '') + '" data-vm="grid" aria-label="' + esc(t('view_grid')) + '">' + ICO.grid + '</button>' +
      '<button class="nav' + (mode === 'list' ? ' on' : '') + '" data-vm="list" aria-label="' + esc(t('view_list')) + '">' + ICO.list + '</button></div></div>';
    return h;
  }
  function bindToolbar(rerender) {
    var s = $('#sortSel');
    if (s) s.addEventListener('change', function () { put('sort', s.value); rerender(); });
    view.querySelectorAll('[data-vm]').forEach(function (b) { b.addEventListener('click', function () { put('view', b.getAttribute('data-vm')); rerender(); }); });
    var sb = view.querySelector('[data-sel]');
    if (sb) sb.addEventListener('click', function () { if (sel) Select.stop(); else Select.start(); });
  }
  function itemsHtml(items) {
    if (!items.length) return '<div class="empty">' + esc(t('empty')) + '</div>';
    var imgs = items.filter(function (x) { return x.k === 'i'; }).length;
    if (imgs > 6 && imgs / items.length > 0.6) {
      var h = '<div class="masonry">';
      items.forEach(function (it, i) {
        if (it.k === 'i') h += '<a class="ph nav" href="javascript:void 0" data-i="' + i + '"><img loading="lazy" alt="' + esc(it.t) + '" src="/t/' + enc(it.id) + '.jpg"></a>';
      });
      h += '</div>';
      var rest = items.map(function (x, i) { return x.k !== 'i' ? card(x, i) : ''; }).join('');
      return (rest ? '<div class="grid" style="margin-bottom:12px">' + rest + '</div>' : '') + h;
    }
    var mode = get('view', 'grid');
    return '<div class="' + mode + '">' + items.map(function (it, i) { return card(it, i); }).join('') + '</div>';
  }
  function crumbsLib(crumbs) {
    return '<a class="nav" href="#/">' + esc(t('home')) + '</a>' + (crumbs || []).map(function (c) {
      return '<span>›</span><a class="nav" href="#/f/' + enc(c.id) + '">' + esc(c.t) + '</a>';
    }).join('');
  }
  function renderFolder(id) {
    return api('/api/list?id=' + enc(id)).then(function (d) {
      var draw = function () {
        list = sortItems(d.items, get('sort', 'default')); listFolder = id;
        view.innerHTML = toolbar(crumbsLib(d.crumbs), {}) + itemsHtml(list);
        bindToolbar(draw);
      };
      draw();
    });
  }
  function renderSearch(qs) {
    return api('/api/search?q=' + enc(qs)).then(function (d) {
      list = d.items; listFolder = '';
      list.forEach(function (x) { x.inFolder = ''; });
      view.innerHTML = '<div class="sec"><h2>' + esc(t('results', qs)) + '</h2><span class="sp"></span><span>' + esc(plural('items', list.length)) + '</span></div>' +
        (list.length ? itemsHtml(list) : '<div class="empty">' + esc(t('nothing_found')) + '</div>');
    });
  }

  // ======================= Tüm dosyalar =======================
  function renderFiles(path) {
    return api('/api/files?p=' + enc(path)).then(function (d) {
      var draw = function () {
        var items = d.items.map(function (x) {
          return { id: x.id || '', t: x.n, k: x.dir ? 'f' : (x.k || ''), sz: x.sz, dt: x.m, path: x.dir ? x.p : null, d: x.d, ext: x.ext,
            raw: x.dir ? null : '/raw?p=' + enc(x.p), n: x.dir ? x.c : -1, inFolder: '' };
        });
        list = sortItems(items, get('sort', 'name') === 'default' ? 'name' : get('sort', 'name'));
        listFolder = '';
        Upload.dir = d.path || '';
        var cr = '<a class="nav" href="#/d/">' + esc(t('all_files')) + '</a>' + (d.crumbs || []).map(function (c) {
          return '<span>›</span><a class="nav" href="#/d/' + enc(c.p) + '">' + esc(c.n) + '</a>';
        }).join('');
        var zipBtn = d.path ? '<a class="pill nav" href="/zip?p=' + enc(d.path) + '" download>' + ICO.dl + esc(t('zip')) + '</a>' : '';
        view.innerHTML = toolbar(cr, {}).replace('<div class="seg">', zipBtn + '<div class="seg">') + itemsHtml(list);
        // Medya olmayan dosyalar indirme bağlantısıdır
        view.querySelectorAll('a[data-i]').forEach(function (a) { var it = list[+a.getAttribute('data-i')]; if (it && it.raw && !it.k) a.setAttribute('download', it.t); });
        bindToolbar(draw);
      };
      draw();
    });
  }

  // ======================= Telefon durumu =======================
  var statusTimer = 0;
  function renderStatus() {
    clearTimeout(statusTimer);
    return api('/api/status').then(function (s) {
      if (route().name !== 'status') return;
      function card(title, rows) { return '<div class="card2"><h2>' + esc(title) + '</h2>' + rows.filter(Boolean).join('') + '</div>'; }
      function row(k, v, bar) {
        return '<div class="row"><span class="grow">' + esc(k) + '</span><b>' + esc(v) + '</b></div>' +
          (bar != null ? '<div class="meter"><i style="width:' + Math.round(bar * 100) + '%"></i></div>' : '');
      }
      var h = '<div class="adm"><div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + esc(t('home')) + '</a><span>›</span><a>' + esc(t('status')) + '</a></nav></div>';
      h += card(t('battery'), [
        s.battery != null ? row(t('battery'), s.battery + '%', s.battery / 100) : '',
        row(s.charging ? t('charging') : t('not_charging'), ''),
        s.temp != null ? row(t('temperature'), (Math.round(s.temp * 10) / 10) + ' °C') : '',
      ]);
      h += card(t('storage'), (s.storage || []).map(function (d) {
        return row(d.name, t('free_of', size(d.free), size(d.total)), 1 - d.free / d.total);
      }).concat([row(t('memory'), t('free_of', size(s.ramFree), size(s.ramTotal)), 1 - s.ramFree / s.ramTotal)]));
      h += card(t('wifi'), [
        s.rssi != null ? row(t('signal'), s.rssi + ' dBm', (s.signal + 1) / 5) : row(t('signal'), '—'),
        s.linkSpeed ? row(t('link_speed'), s.linkSpeed + ' Mbit/s') : '',
      ]);
      h += card(t('device'), [row(t('device'), s.device + ' • Android ' + s.android), s.uptime ? row(t('uptime'), fmt(s.uptime / 1000)) : '']);
      view.innerHTML = h + '</div>';
      statusTimer = setTimeout(function () { if (route().name === 'status' && !document.hidden) renderStatus(); }, 5000);
    });
  }

  // ======================= Canlı kamera =======================
  function renderCam() {
    var front = get('camFront', C.camFront), q = get('camQ', C.camHeight || 720), sound = false;
    function draw() {
      view.innerHTML = '<div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + esc(t('home')) + '</a><span>›</span><a>' + esc(t('camera')) + '</a></nav>' +
        '<button class="pill nav" data-cm="sw">' + esc(t('cam_switch')) + '</button>' +
        '<button class="pill nav" data-cm="q">' + (q >= 720 ? '720p' : '480p') + '</button>' +
        '<a class="pill nav" href="/cam/snap" download>' + esc(t('cam_snap')) + '</a>' +
        (C.mic ? '<button class="pill nav' + (sound ? ' acc' : '') + '" data-cm="snd" aria-pressed="' + sound + '">' + ICO.mic + esc(t('mic_sound')) + '</button>' : '') +
        '<button class="pill nav" data-cm="fs">' + esc(t('fullscreen')) + '</button></div>' +
        '<div class="camwrap"><img id="camImg" alt="" src="/cam/stream?f=' + (front ? 'front' : 'back') + '&q=' + q + '&_=' + Date.now() + '"></div>' +
        (sound ? '<audio id="camSnd" autoplay src="/live/mic.wav?_=' + Date.now() + '"></audio>' : '');
      var img = document.getElementById('camImg');
      img.onerror = function () { view.querySelector('.camwrap').innerHTML = '<div class="empty">' + esc(t('cam_off')) + '</div>'; };
      view.querySelectorAll('[data-cm]').forEach(function (b) {
        b.addEventListener('click', function () {
          var a = b.getAttribute('data-cm');
          if (a === 'sw') { front = !front; put('camFront', front); draw(); }
          else if (a === 'q') { q = q >= 720 ? 480 : 720; put('camQ', q); draw(); }
          else if (a === 'snd') { sound = !sound; draw(); }
          else if (a === 'fs') { var w = view.querySelector('.camwrap'); (w.requestFullscreen || w.webkitRequestFullscreen || function () { }).call(w); }
        });
      });
    }
    draw();
    return Promise.resolve();
  }

  // ======================= Ekran paylaşımı =======================
  function renderScreen() {
    function draw() {
      view.innerHTML = '<div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + esc(t('home')) + '</a><span>›</span><a>' + esc(t('screen')) + '</a></nav>' +
        '<button class="pill nav" data-sc="fs">' + esc(t('fullscreen')) + '</button></div>' +
        '<div class="camwrap"><div class="empty" id="scWait">' + esc(t('screen_wait')) + '</div>' +
        '<img id="scImg" alt="" hidden src="/live/screen?_=' + Date.now() + '"></div>';
      var img = document.getElementById('scImg');
      img.onload = function () { img.hidden = false; var w = document.getElementById('scWait'); if (w) w.remove(); };
      img.onerror = function () {
        view.querySelector('.camwrap').innerHTML = '<div class="empty">' + esc(t('screen_off')) +
          '<div style="margin-top:12px"><button class="pill nav" data-sc="again">' + esc(t('screen_again')) + '</button></div></div>';
      };
    }
    view.onclick = function (e) {
      var b = e.target.closest && e.target.closest('[data-sc]');
      if (!b || route().name !== 'screen') return;
      var a = b.getAttribute('data-sc');
      if (a === 'again') draw();
      else if (a === 'fs') { var w = view.querySelector('.camwrap'); (w.requestFullscreen || w.webkitRequestFullscreen || function () { }).call(w); }
    };
    draw();
    return Promise.resolve();
  }

  // ======================= Mikrofon (bebek telsizi) =======================
  function renderMic() {
    view.innerHTML = '<div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + esc(t('home')) + '</a><span>›</span><a>' + esc(t('mic')) + '</a></nav></div>' +
      '<div class="camwrap" style="flex-direction:column;gap:16px;padding:28px"><div class="icon-badge" style="width:72px;height:72px">' + ICO.mic + '</div>' +
      '<audio id="micAud" controls autoplay src="/live/mic.wav?_=' + Date.now() + '" style="width:min(420px,100%)"></audio>' +
      '<div class="note" style="color:#bbb;text-align:center">' + esc(t('mic_note')) + '</div></div>';
    var a = document.getElementById('micAud');
    a.onerror = function () { a.insertAdjacentHTML('afterend', '<div class="empty">' + esc(t('mic_off')) + '</div>'); a.remove(); };
    return Promise.resolve();
  }

  // ======================= Bildirimler =======================
  var notifTimer = 0;
  function renderNotifs() {
    clearTimeout(notifTimer);
    var last = -1;
    function draw(d) {
      var h = d.items.map(function (n) {
        var when = new Date(n.t).toLocaleTimeString(C.lang || undefined, { hour: '2-digit', minute: '2-digit' });
        return '<div class="card app"' + (n.gone ? ' style="opacity:.55"' : '') + '><img loading="lazy" alt="" src="/nicon?pkg=' + enc(n.pkg) + '">' +
          '<div class="meta"><div class="sub">' + esc(n.app + ' • ' + when + (n.gone ? ' • ' + t('notifs_gone') : '')) + '</div>' +
          '<div class="title">' + esc(n.title) + '</div>' + (n.text ? '<div class="sub" style="white-space:pre-wrap">' + esc(n.text) + '</div>' : '') + '</div></div>';
      }).join('') || '<div class="empty">' + esc(t('notifs_empty')) + '</div>';
      var el = document.getElementById('notifList');
      if (el) el.innerHTML = h;
    }
    function poll() {
      if (route().name !== 'notifs') return;
      api('/api/notifs').then(function (d) {
        if (d.v !== last) { last = d.v; draw(d); }
        notifTimer = setTimeout(poll, 3000);
      }).catch(function () { notifTimer = setTimeout(poll, 6000); });
    }
    view.innerHTML = '<div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + esc(t('home')) + '</a><span>›</span><a>' + esc(t('notifs')) + '</a></nav></div>' +
      '<div class="list" id="notifList"><div class="spin" style="margin:30px auto"></div></div>';
    return api('/api/notifs').then(function (d) { last = d.v; draw(d); notifTimer = setTimeout(poll, 3000); });
  }

  // ======================= Gelenler =======================
  function renderInbox() {
    return api('/api/inbox').then(function (d) {
      var h = d.items.map(function (x) {
        var img = /^image\//.test(x.m) && x.m !== 'image/svg+xml';
        var when = new Date(x.t).toLocaleString(C.lang || undefined, { dateStyle: 'short', timeStyle: 'short' });
        return '<div class="card app">' + (img ? '<img loading="lazy" alt="" src="/inbox/' + enc(x.id) + '" style="object-fit:cover">' : '<div class="icon-badge">' + ICO.file + '</div>') +
          '<div class="meta"><div class="title">' + esc(x.n) + '</div><div class="sub">' + esc([size(x.sz), when, t('inbox_from', x.from)].join(' • ')) + '</div></div>' +
          '<a class="pill nav" href="/inbox/' + enc(x.id) + '" target="_blank" rel="noopener">' + esc(t('preview')) + '</a>' +
          '<a class="pill nav" href="/inbox/' + enc(x.id) + '?dl=1" download>' + ICO.dl + '</a></div>';
      }).join('') || '<div class="empty">' + esc(t('inbox_empty')) + '</div>';
      view.innerHTML = '<div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + esc(t('home')) + '</a><span>›</span><a>' + esc(t('inbox')) + '</a></nav>' +
        '<span class="sub">' + esc(plural('items', d.items.length)) + '</span></div><div class="list">' + h + '</div>';
    });
  }

  // ======================= Yüklü uygulamalar =======================
  function renderApps() {
    return api('/api/apps').then(function (d) {
      var all = d.items, q2 = '';
      function draw() {
        var items = all.filter(function (a) { return !q2 || (a.name + ' ' + a.pkg).toLowerCase().indexOf(q2) >= 0; });
        var listEl = document.getElementById('appList');
        var h = items.map(function (a) {
          return '<div class="card app"><img loading="lazy" alt="" src="/appicon?pkg=' + enc(a.pkg) + '">' +
            '<div class="meta"><div class="title">' + esc(a.name) + '</div><div class="sub">' + esc(t('app_version', a.ver || '?') + ' • ' + size(a.sz)) +
            (a.split ? ' • ' + esc(t('split_apk')) : '') + '</div><div class="sub">' + esc(a.pkg) + '</div></div>' +
            '<a class="pill nav" href="/apk?pkg=' + enc(a.pkg) + '" download>' + ICO.dl + '</a></div>';
        }).join('') || '<div class="empty">' + esc(t('nothing_found')) + '</div>';
        if (listEl) listEl.innerHTML = h;
      }
      view.innerHTML = '<div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + esc(t('home')) + '</a><span>›</span><a>' + esc(t('apps')) + '</a></nav>' +
        '<input class="pill nav" id="appQ" placeholder="' + esc(t('filter_apps')) + '" style="border:0;outline:0;color:var(--fg)"><span class="sub">' + esc(plural('items', all.length)) + '</span></div>' +
        '<div class="list" id="appList"></div>';
      var inp = document.getElementById('appQ');
      inp.addEventListener('input', function () { q2 = inp.value.trim().toLowerCase(); draw(); });
      draw();
    });
  }

  // ======================= İzleme sayfası =======================
  function renderWatch(id, inFolder) {
    return api('/api/item?id=' + enc(id) + '&in=' + enc(inFolder)).then(function (info) {
      var it = info.item;
      if (Music.active()) Music.pause();
      var file = '/m/' + enc(it.id) + '/file.' + (it.ext || 'bin');
      var meta = [it.d ? fmt(it.d / 1000) : '', it.h ? it.w + '×' + it.h : '', size(it.sz), (info.subs && info.subs.length) ? plural('subs_count', info.subs.length) : ''].filter(Boolean).join(' • ');
      var h = '<div class="bar"><nav class="crumbs">' + crumbsLib(info.crumbs) + '</nav></div>' +
        '<div class="watch"><div><div id="plHost"></div><h1>' + esc(it.t) + '</h1><div class="sub">' + esc(meta) + '</div>' +
        '<div class="actions">' +
        '<a class="pill nav" href="vlc://' + esc(location.origin + file) + '">' + ICO.v + esc(t('open_vlc')) + '</a>' +
        '<a class="pill nav" href="' + esc(file) + '" download>' + ICO.dl + esc(t('download')) + '</a>' +
        '<button class="pill nav" id="copyLink">' + ICO.link + esc(t('copy_link')) + '</button>' +
        '<button class="pill nav" id="propsBtn">' + ICO.info + esc(t('info')) + '</button></div></div>' +
        '<div class="side">';
      var side = [];
      if (info.next) { info.next.inFolder = inFolder; side.push(info.next); }
      if (info.prev) { info.prev.inFolder = inFolder; side.push(info.prev); }
      if (side.length) {
        list = side; listFolder = inFolder;
        h += '<h3>' + esc(t('next_up')) + '</h3><div class="list">' + side.map(function (x, i) { return card(x, i); }).join('') + '</div>';
      }
      h += '</div></div>';
      view.innerHTML = h;
      $('#propsBtn').addEventListener('click', function () { Props.open({ id: it.id }); });
      $('#copyLink').addEventListener('click', function () {
        var u = location.origin + file;
        if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(u).then(function () { toast(t('copied')); });
        else { var ta = document.createElement('textarea'); ta.value = u; document.body.appendChild(ta); ta.select(); try { document.execCommand('copy'); toast(t('copied')); } catch (e) { } ta.remove(); }
      });
      player = P.mount($('#plHost'), info, {
        inFolder: inFolder,
        onBack: function () { if (history.length > 1) history.back(); else go('#/'); },
        onNext: function (n) { go('#/v/' + enc(n.id) + '?in=' + enc(inFolder), true); }
      });
    });
  }

  // ======================= Müzik çubuğu =======================
  var Music = (function () {
    var bar = $('#mini'), audio = new Audio(), queue = [], order = [], pos = 0, shuffle = get('shuffle', false), qOpen = false;
    audio.preload = 'auto';
    audio.volume = get('mvol', 1);
    function cur() { return queue[order[pos]]; }
    function build() {
      bar.innerHTML = '<div class="art"></div><div class="info"><div class="title"></div><div class="sub"></div><div class="mbar"><i></i></div></div>' +
        '<div class="ctl"><button class="opt nav" data-m="shuffle" aria-label="' + esc(t('shuffle')) + '">' + ICO.shuffle + '</button>' +
        '<button class="nav" data-m="prev" aria-label="' + esc(t('prev')) + '">' + ICO.prev + '</button>' +
        '<button class="pp nav" data-m="pp" aria-label="' + esc(t('play')) + '">' + ICO.play + '</button>' +
        '<button class="nav" data-m="next" aria-label="' + esc(t('next')) + '">' + ICO.next + '</button>' +
        '<button class="opt nav" data-m="queue" aria-label="' + esc(t('queue')) + '">' + ICO.queue + '</button>' +
        '<button class="nav" data-m="close" aria-label="' + esc(t('close')) + '">' + ICO.x + '</button></div>';
      bar.addEventListener('click', function (e) {
        var b = e.target.closest('[data-m]'); if (!b) return;
        var m = b.getAttribute('data-m');
        if (m === 'pp') toggle(); else if (m === 'prev') prev(); else if (m === 'next') next(true);
        else if (m === 'shuffle') { shuffle = !shuffle; put('shuffle', shuffle); reorder(); paint(); }
        else if (m === 'queue') { qOpen = !qOpen; paintQueue(); }
        else if (m === 'close') close();
        else if (m === 'q') { pos = order.indexOf(+b.getAttribute('data-q')); load(); qOpen = false; paintQueue(); }
      });
      $('.mbar', bar).addEventListener('click', function (e) {
        var r = this.getBoundingClientRect();
        if (audio.duration) seekTo((e.clientX - r.left) / r.width * audio.duration);
      });
    }
    function reorder() {
      var curIdx = order[pos] || 0;
      order = queue.map(function (_, i) { return i; });
      if (shuffle) {
        for (var i = order.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var x = order[i]; order[i] = order[j]; order[j] = x; }
        order.splice(order.indexOf(curIdx), 1); order.unshift(curIdx);
      }
      pos = order.indexOf(curIdx);
    }
    function paint() {
      var it = cur(); if (!it) return;
      $('.title', bar).textContent = it.t;
      $('.sub', bar).textContent = [it.ar, it.al].filter(Boolean).join(' • ') || t('queue_n', pos + 1, queue.length);
      var art = $('.art', bar);
      art.style.backgroundImage = 'url("/t/' + enc(it.id) + '.jpg")';
      art.innerHTML = '';
      $('[data-m=pp]', bar).innerHTML = audio.paused ? ICO.play : ICO.pause;
      $('[data-m=shuffle]', bar).classList.toggle('on', shuffle);
    }
    function paintQueue() {
      var ql = $('.qlist', bar);
      if (!qOpen) { if (ql) ql.remove(); return; }
      if (!ql) { ql = document.createElement('div'); ql.className = 'qlist'; bar.appendChild(ql); }
      ql.innerHTML = order.map(function (qi, p) { return '<button class="nav' + (p === pos ? ' on' : '') + '" data-m="q" data-q="' + qi + '">' + esc(queue[qi].t) + '</button>'; }).join('');
    }
    var engine = null, gen = 0;
    function stopEngine() { if (engine) { engine.destroy(); engine = null; } }
    /** Tarayıcının çalamadığı dosya (WMA, AC3, APE, ALAC…): telefon AAC'ye çevirip akıtır, bizim motorla çalınır. */
    function converted(it) {
      var g = ++gen;
      stopEngine();
      if (!P.MS) { toast(t('cant_play_audio')); next(false); return; }
      api('/api/probe?id=' + enc(it.id)).then(function (pr) {
        if (g !== gen) return;
        var tr = pr.ok ? pr.tracks.filter(function (x) { return !x.v && (x.tx || x.codec); })[0] : null;
        if (!tr) throw new Error('codec');
        var tx = !(tr.codec && P.MS.isTypeSupported(P.Engine.mime(null, tr.codec)));
        engine = new P.Engine(audio, it.id, (pr.d || it.d || 0) / 1000,
          { vcodec: null, a: tr.i, tx: tx, acodec: tx ? 'mp4a.40.2' : tr.codec },
          function () { if (g === gen) { toast(t('cant_play_audio')); next(false); } });
        engine.open(0);
        audio.play().catch(function () { });
      }).catch(function () { if (g === gen) { toast(t('cant_play_audio')); next(false); } });
    }
    function seekTo(x) { if (engine) engine.seek(x); else audio.currentTime = x; }
    function load() {
      var it = cur(); if (!it) return;
      gen++;
      stopEngine();
      if (it.mime && !audio.canPlayType(it.mime) && !/mpeg|mp3|aac|mp4|ogg|opus|flac|wav|webm/.test(it.mime)) {
        converted(it);
      } else {
        audio.src = '/m/' + enc(it.id) + '/file.' + (it.ext || 'mp3');
        audio.play().catch(function () { });
      }
      paint();
      if ('mediaSession' in navigator) {
        try {
          navigator.mediaSession.metadata = new MediaMetadata({ title: it.t, artist: it.ar || '', album: it.al || '', artwork: [{ src: '/t/' + enc(it.id) + '.jpg' }] });
          navigator.mediaSession.setActionHandler('play', function () { audio.play(); });
          navigator.mediaSession.setActionHandler('pause', function () { audio.pause(); });
          navigator.mediaSession.setActionHandler('previoustrack', prev);
          navigator.mediaSession.setActionHandler('nexttrack', function () { next(true); });
        } catch (e) { }
      }
    }
    function toggle() { if (audio.paused) audio.play().catch(function () { }); else audio.pause(); }
    function prev() { if (audio.currentTime > 4) { seekTo(0); return; } if (pos > 0) { pos--; load(); } }
    function next(user) { if (pos + 1 < order.length) { pos++; load(); } else if (user) { pos = 0; load(); } }
    function close() { gen++; stopEngine(); audio.pause(); audio.removeAttribute('src'); bar.hidden = true; document.body.classList.remove('has-mini'); queue = []; }
    audio.addEventListener('ended', function () { next(false); });
    audio.addEventListener('play', paint);
    audio.addEventListener('pause', paint);
    audio.addEventListener('timeupdate', function () { var i = $('.mbar i', bar); if (i && audio.duration) i.style.width = (audio.currentTime / audio.duration * 100) + '%'; });
    // Doğrudan çalınamadıysa (ör. M4A içinde ALAC) bir de telefonda çevirerek dene
    audio.addEventListener('error', function () { var it = cur(); if (!engine && it) converted(it); });
    return {
      play: function (items, it) {
        if (!bar.innerHTML) build();
        queue = items; pos = 0; order = queue.map(function (_, i) { return i; });
        pos = Math.max(0, queue.indexOf(it));
        if (shuffle) reorder();
        bar.hidden = false; document.body.classList.add('has-mini');
        load();
      },
      enqueue: function (items) {
        if (!items.length) return;
        if (!queue.length || bar.hidden) { this.play(items, items[0]); return; }
        var start = queue.length;
        queue = queue.concat(items);
        items.forEach(function (_, k) { order.push(start + k); });
        paintQueue();
      },
      active: function () { return !bar.hidden && !audio.paused; },
      pause: function () { audio.pause(); }
    };
  })();

  // ======================= Belge önizleme ve ZIP içi =======================
  var Docs = (function () {
    var TEXT = /\.(txt|log|md|markdown|json|xml|csv|tsv|ini|conf|cfg|yml|yaml|toml|srt|vtt|ass|ssa|sub|html?|css|js|ts|kt|kts|java|py|c|h|cpp|hpp|cs|go|rs|rb|php|sh|bat|ps1|sql|gradle|properties)$/i;
    var wrap = document.createElement('div');
    wrap.className = 'props docv'; wrap.hidden = true;
    document.body.appendChild(wrap);
    function close() { wrap.hidden = true; wrap.innerHTML = ''; }
    wrap.addEventListener('click', function (e) { if (e.target === wrap || e.target.closest('[data-dc]')) close(); });
    document.addEventListener('keydown', function (e) { if (!wrap.hidden && e.key === 'Escape') { e.preventDefault(); e.stopImmediatePropagation(); close(); } }, true);
    function pathOf(it) { return decodeURIComponent((it.raw || '').split('p=')[1] || ''); }
    function shell(title, dl, body) {
      wrap.innerHTML = '<div class="box wide"><div class="hd"><h2>' + esc(title) + '</h2>' +
        (dl ? '<a class="pill nav" href="' + esc(dl) + '" download>' + ICO.dl + esc(t('download')) + '</a>' : '') +
        '<button class="pill nav" data-dc="1" aria-label="' + esc(t('close')) + '">' + ICO.x + '</button></div><div class="docbody">' + body + '</div></div>';
      wrap.hidden = false;
    }
    function md(src) {
      var h = esc(src);
      h = h.replace(/^```[\s\S]*?^```/gm, function (m) { return '<pre>' + m.replace(/^```.*$/gm, '') + '</pre>'; });
      h = h.replace(/^###### (.*)$/gm, '<h6>$1</h6>').replace(/^##### (.*)$/gm, '<h5>$1</h5>').replace(/^#### (.*)$/gm, '<h4>$1</h4>')
        .replace(/^### (.*)$/gm, '<h3>$1</h3>').replace(/^## (.*)$/gm, '<h2>$1</h2>').replace(/^# (.*)$/gm, '<h1>$1</h1>');
      h = h.replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>').replace(/\*([^*]+)\*/g, '<i>$1</i>').replace(/`([^`]+)`/g, '<code>$1</code>');
      h = h.replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
      h = h.replace(/^\s*[-*] (.*)$/gm, '<li>$1</li>');
      return '<div class="md">' + h.split(/\n{2,}/).map(function (p) { return /^<(h\d|pre|li)/.test(p) ? p : '<p>' + p.replace(/\n/g, '<br>') + '</p>'; }).join('') + '</div>';
    }
    function code(src) {
      var lines = src.split('\n');
      return '<pre class="code">' + lines.map(function (l, i) { return '<span class="ln">' + (i + 1) + '</span>' + esc(l); }).join('\n') + '</pre>';
    }
    function openText(it) {
      shell(it.t, it.raw, '<div class="spin"></div>');
      fetch(it.raw, { headers: { Range: 'bytes=0-1048575' } }).then(function (r) { return r.arrayBuffer(); }).then(function (buf) {
        var txt = new TextDecoder('utf-8').decode(buf);
        var big = buf.byteLength >= 1048576 ? '<div class="note">' + esc(t('too_big')) + '</div>' : '';
        var body;
        if (/\.(md|markdown)$/i.test(it.t)) body = md(txt);
        else if (/\.json$/i.test(it.t)) { try { body = code(JSON.stringify(JSON.parse(txt), null, 2)); } catch (e) { body = code(txt); } }
        else body = code(txt);
        var b = wrap.querySelector('.docbody'); if (b) b.innerHTML = big + body;
      }).catch(function () { var b = wrap.querySelector('.docbody'); if (b) b.textContent = t('error_load'); });
    }
    function openZip(it) {
      var p = pathOf(it);
      shell(it.t, it.raw, '<div class="spin"></div>');
      api('/api/zipls?p=' + enc(p)).then(function (d) {
        var b = wrap.querySelector('.docbody'); if (!b) return;
        if (d.err) { b.textContent = t('zip_bad'); return; }
        b.innerHTML = '<div class="note">' + esc(t('zip_items')) + ' • ' + esc(plural('items', d.items.length)) + '</div><div class="ziplist">' +
          d.items.map(function (x) {
            return x.dir ? '<div class="zr dir">' + ICO.folder + '<span>' + esc(x.n) + '</span></div>'
              : '<a class="zr nav" download href="/zipget?p=' + enc(p) + '&e=' + enc(x.n) + '">' + ICO.file + '<span>' + esc(x.n) + '</span><b>' + esc(size(x.sz)) + '</b></a>';
          }).join('') + '</div>';
      }).catch(function () { var b = wrap.querySelector('.docbody'); if (b) b.textContent = t('zip_bad'); });
    }
    return {
      can: function (name) { return (C.docs && (/\.pdf$/i.test(name) || TEXT.test(name))) || (C.zipView && /\.zip$/i.test(name)); },
      open: function (it) {
        if (/\.zip$/i.test(it.t)) openZip(it);
        else if (/\.pdf$/i.test(it.t)) shell(it.t, it.raw, '<iframe class="pdf" src="' + esc(it.raw) + '"></iframe>');
        else openText(it);
      }
    };
  })();

  // ======================= Kısayol yardımı (?) =======================
  (function () {
    if (!C.keys) return;
    var wrap = document.createElement('div');
    wrap.className = 'props'; wrap.hidden = true;
    document.body.appendChild(wrap);
    function row(k, d) { return '<div><span><kbd>' + k.split(' ').join('</kbd> <kbd>') + '</kbd></span><b>' + esc(t(d)) + '</b></div>'; }
    function open() {
      wrap.innerHTML = '<div class="box"><div class="hd"><h2>' + esc(t('keys_title')) + '</h2><button class="pill nav" data-kc="1">' + ICO.x + '</button></div><div class="body keys">' +
        '<h3>' + esc(t('keys_general')) + '</h3>' + row('/', 'k_search') + row('← ↑ → ↓', 'k_arrows') + row('Backspace', 'k_back') + row('I', 'k_info') + row('?', 'k_help') +
        '<h3>' + esc(t('keys_player')) + '</h3>' + row('Space K', 'k_play') + row('← →', 'k_seek') + row('↑ ↓', 'k_vol') + row('M', 'k_mute') + row('F', 'k_fs') +
        row('C', 'k_subs') + row('G H', 'k_sync') + row('N', 'k_next') + row('0…9', 'k_jump') + row('< >', 'k_speed') +
        '<h3>' + esc(t('keys_photos')) + '</h3>' + row('← →', 'k_photo_nav') + row('Space', 'k_slides') + row('I', 'k_info') + row('Esc', 'k_close') +
        '</div></div>';
      wrap.hidden = false;
    }
    wrap.addEventListener('click', function (e) { if (e.target === wrap || e.target.closest('[data-kc]')) { wrap.hidden = true; } });
    document.addEventListener('keydown', function (e) {
      var tag = (e.target && e.target.tagName) || '';
      if (e.key === '?' && tag !== 'INPUT' && tag !== 'TEXTAREA') { e.preventDefault(); if (wrap.hidden) open(); else wrap.hidden = true; }
      else if (!wrap.hidden && e.key === 'Escape') { e.preventDefault(); e.stopImmediatePropagation(); wrap.hidden = true; }
    }, true);
  })();

  // ======================= Çoklu seçim =======================
  var Select = (function () {
    var bar = document.createElement('div');
    bar.className = 'selbar'; bar.hidden = true;
    document.body.appendChild(bar);
    function count() { return Object.keys(sel ? sel.set : {}).length; }
    function picked() { return Object.keys(sel.set).map(function (i) { return list[+i]; }).filter(Boolean); }
    function paint() {
      if (!sel) return;
      view.querySelectorAll('[data-i]').forEach(function (a) {
        if (a.getAttribute('data-src') === 'shelf') return;
        a.classList.toggle('picked', !!sel.set[a.getAttribute('data-i')]);
        a.classList.add('selecting');
      });
      var n = count(), audio = picked().some(function (x) { return x.k === 'a'; });
      bar.innerHTML = '<span>' + esc(t('selected_n', n)) + '</span><span class="sp"></span>' +
        '<button class="pill nav" data-sa="all">' + esc(t('select_all')) + '</button>' +
        (audio ? '<button class="pill nav" data-sa="queue">' + esc(t('add_queue')) + '</button>' : '') +
        '<button class="pill acc nav" data-sa="zip"' + (n ? '' : ' disabled') + '>' + ICO.dl + esc(t('zip')) + '</button>' +
        '<button class="pill nav" data-sa="x" aria-label="' + esc(t('cancel')) + '">' + ICO.x + '</button>';
      bar.hidden = false;
    }
    function zip() {
      var f = document.createElement('form');
      f.method = 'post'; f.action = '/zipsel'; f.style.display = 'none';
      picked().forEach(function (x) {
        var inp = document.createElement('input');
        inp.type = 'hidden';
        if (x.id) { inp.name = 'i'; inp.value = x.id; } else { inp.name = 'p'; inp.value = x.path || decodeURIComponent((x.raw || '').split('p=')[1] || ''); }
        f.appendChild(inp);
      });
      document.body.appendChild(f); f.submit(); f.remove();
    }
    bar.addEventListener('click', function (e) {
      var b = e.target.closest('[data-sa]'); if (!b) return;
      var a = b.getAttribute('data-sa');
      if (a === 'x') api2.stop();
      else if (a === 'all') { list.forEach(function (_, i) { sel.set[i] = true; }); paint(); }
      else if (a === 'zip') { zip(); api2.stop(); }
      else if (a === 'queue') { var au = picked().filter(function (x) { return x.k === 'a'; }); Music.enqueue(au); toast(t('queued_n', au.length)); api2.stop(); }
    });
    document.addEventListener('keydown', function (e) { if (sel && e.key === 'Escape') { e.preventDefault(); api2.stop(); } });
    var api2 = {
      start: function () { sel = { set: {}, last: -1 }; document.body.classList.add('has-sel'); paint(); var b = view.querySelector('[data-sel]'); if (b) b.classList.add('acc'); },
      stop: function (quiet) {
        sel = null; bar.hidden = true; document.body.classList.remove('has-sel');
        if (quiet) return;
        view.querySelectorAll('.selecting').forEach(function (a) { a.classList.remove('selecting', 'picked'); });
        var b = view.querySelector('[data-sel]'); if (b) b.classList.remove('acc');
      },
      toggle: function (i, range) {
        if (range && sel.last >= 0) {
          var a = Math.min(sel.last, i), z = Math.max(sel.last, i);
          for (var k = a; k <= z; k++) sel.set[k] = true;
        } else if (sel.set[i]) delete sel.set[i]; else sel.set[i] = true;
        sel.last = i;
        paint();
      },
      paint: paint
    };
    return api2;
  })();

  // ======================= Özellikler =======================
  var CODECS = { 'video/avc': 'H.264', 'video/hevc': 'H.265 (HEVC)', 'video/x-vnd.on2.vp9': 'VP9', 'video/x-vnd.on2.vp8': 'VP8', 'video/av01': 'AV1',
    'video/mp4v-es': 'MPEG-4', 'video/mpeg2': 'MPEG-2', 'audio/mp4a-latm': 'AAC', 'audio/ac3': 'AC3', 'audio/eac3': 'E-AC3', 'audio/eac3-joc': 'E-AC3 Atmos',
    'audio/vnd.dts': 'DTS', 'audio/vnd.dts.hd': 'DTS-HD', 'audio/true-hd': 'TrueHD', 'audio/opus': 'Opus', 'audio/vorbis': 'Vorbis', 'audio/mpeg': 'MP3',
    'audio/flac': 'FLAC', 'audio/raw': 'PCM', 'audio/alac': 'ALAC' };
  var lnames = null;
  try { lnames = new Intl.DisplayNames([C.lang || 'en'], { type: 'language' }); } catch (e) { }
  function lname(c) { try { var n = c && lnames && lnames.of(c); return n && n !== c ? n.charAt(0).toUpperCase() + n.slice(1) : (c || '').toUpperCase(); } catch (e) { return (c || '').toUpperCase(); } }
  function when(sec) { return sec ? new Date(sec * 1000).toLocaleString(C.lang || undefined, { dateStyle: 'medium', timeStyle: 'short' }) : ''; }
  function propsHtml(x) {
    var rows = [];
    function row(k, v) { if (v) rows.push('<div><span>' + esc(t(k)) + '</span><b>' + esc(v) + '</b></div>'); }
    row('type', x.dir ? t('folder_t') : (x.mime || ''));
    if (x.dir) row('contains', plural('items', x.count || 0));
    if (x.size) row('file_size', size(x.size) + ' (' + Number(x.size).toLocaleString(C.lang || undefined) + ' B)');
    if (x.d) row('duration', fmt(x.d / 1000));
    if (x.w) row('resolution', x.w + ' × ' + x.h + (x.k === 'i' ? ' (' + (Math.round(x.w * x.h / 1e5) / 10) + ' MP)' : ''));
    if (x.br) row('bitrate', x.br >= 1e6 ? (Math.round(x.br / 1e5) / 10) + ' Mbit/s' : Math.round(x.br / 1000) + ' kbit/s');
    row('title_t', x.title); row('artist_t', x.artist); row('album_t', x.album); row('year_t', x.year); row('genre_t', x.genre);
    if (x.date) { var m = /^(\d{4}):(\d\d):(\d\d) (\d\d):(\d\d)/.exec(x.date); row('taken', m ? new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5]).toLocaleString(C.lang || undefined, { dateStyle: 'long', timeStyle: 'short' }) : x.date); }
    row('camera', x.camera);
    if (x.f) row('aperture', 'ƒ/' + (Math.round(x.f * 10) / 10));
    if (x.exp) row('shutter', x.exp >= 1 ? (Math.round(x.exp * 10) / 10) + ' s' : '1/' + Math.round(1 / x.exp) + ' s');
    if (x.iso) row('iso', String(x.iso));
    if (x.focal) row('focal', (Math.round(x.focal * 10) / 10) + ' mm');
    (x.tracks || []).forEach(function (tr) {
      var c = CODECS[tr.mime] || tr.mime.replace(/^\w+\//, '').toUpperCase();
      if (tr.v) row('video_t', c + (tr.w ? ' · ' + tr.w + '×' + tr.h : ''));
      else {
        var ch = tr.ch ? (tr.ch > 2 ? (tr.ch - 1) + '.1' : tr.ch === 2 ? t('stereo') : t('mono')) : '';
        row('audio_t', [tr.name || lname(tr.lang), c, ch, tr.rate ? Math.round(tr.rate / 100) / 10 + ' kHz' : '', tr.def ? t('default_t') : ''].filter(Boolean).join(' · '));
      }
    });
    if (x.subs && x.subs.length) row('subs_t', x.subs.join(', '));
    row('modified', when(x.m));
    row('path', x.path);
    var map = x.lat != null ? '<a class="pill" target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=' + x.lat + '&mlon=' + x.lon + '#map=16/' + x.lat + '/' + x.lon + '">' + esc(t('open_map')) + '</a>' : '';
    return '<b class="nm">' + esc(x.name || '') + '</b>' + (rows.length ? rows.join('') : '<div>' + esc(t('no_info')) + '</div>') + map;
  }
  function propsUrl(it) { return it.id ? '/api/info?id=' + enc(it.id) : '/api/info?p=' + enc(it.p || it.path || ''); }
  /** Herhangi bir öğenin özellik penceresi (sağ tık, uzun basma, I tuşu). */
  var Props = (function () {
    var wrap = document.createElement('div');
    wrap.className = 'props'; wrap.hidden = true;
    document.body.appendChild(wrap);
    function close() { wrap.hidden = true; wrap.innerHTML = ''; }
    wrap.addEventListener('click', function (e) { if (e.target === wrap || e.target.closest('[data-pc]')) close(); });
    document.addEventListener('keydown', function (e) { if (!wrap.hidden && (e.key === 'Escape' || e.key === 'Backspace')) { e.preventDefault(); e.stopImmediatePropagation(); close(); } }, true);
    return {
      open: function (it) {
        wrap.hidden = false;
        wrap.innerHTML = '<div class="box"><div class="hd"><h2>' + esc(t('info')) + '</h2><button class="pill nav" data-pc="1" aria-label="' + esc(t('close')) + '">' + ICO.x + '</button></div><div class="body"><div class="spin" style="margin:24px auto"></div></div></div>';
        var body = $('.body', wrap);
        api(propsUrl(it)).then(function (x) { body.innerHTML = propsHtml(x); }).catch(function () { body.textContent = t('no_info'); });
        var c = $('[data-pc]', wrap); if (c) c.focus();
      }
    };
  })();
  function itemOf(a) {
    var src = a.getAttribute('data-src') === 'shelf' ? shelves[a.getAttribute('data-shelf')] : list;
    var it = src && src[+a.getAttribute('data-i')];
    if (!it) return null;
    return it.id ? { id: it.id } : { p: it.path || (it.raw ? decodeURIComponent(it.raw.split('p=')[1] || '') : '') };
  }
  // Sağ tık / uzun basma / I tuşu → Özellikler
  view.addEventListener('contextmenu', function (e) {
    var a = e.target.closest('[data-i]'); if (!a) return;
    var it = itemOf(a); if (!it) return;
    e.preventDefault(); Props.open(it);
  });
  var lpTimer = 0, lpFired = false;
  view.addEventListener('pointerdown', function (e) {
    if (e.pointerType === 'mouse') return;
    var a = e.target.closest('[data-i]'); if (!a) return;
    lpFired = false;
    clearTimeout(lpTimer);
    lpTimer = setTimeout(function () { var it = itemOf(a); if (it) { lpFired = true; Props.open(it); } }, 600);
  });
  ['pointerup', 'pointercancel', 'pointermove'].forEach(function (n) {
    view.addEventListener(n, function (e) { if (n !== 'pointermove' || Math.abs(e.movementX) + Math.abs(e.movementY) > 6) clearTimeout(lpTimer); });
  });
  view.addEventListener('click', function (e) { if (lpFired) { e.preventDefault(); e.stopImmediatePropagation(); lpFired = false; } }, true);
  document.addEventListener('keydown', function (e) {
    if ((e.key === 'i' || e.key === 'I') && !player && document.activeElement && document.activeElement.hasAttribute && document.activeElement.hasAttribute('data-i')) {
      var it = itemOf(document.activeElement); if (it) { e.preventDefault(); Props.open(it); }
    }
  });

  // ======================= Fotoğraf görüntüleyici =======================
  var Lightbox = (function () {
    var box = $('#lightbox'), items = [], i = 0, timer = 0, startX = 0, startY = 0, active = false, infoOn = false;
    function loadInfo() {
      var panel = $('.lb-info', box); if (!panel) return;
      var it = items[i];
      panel.innerHTML = '<div class="spin" style="margin:16px auto"></div>';
      api('/api/info?id=' + enc(it.id)).then(function (x) { if (items[i] === it) panel.innerHTML = propsHtml(x); }).catch(function () { panel.textContent = t('no_info'); });
    }
    function src(it) { return '/m/' + enc(it.id) + '/file.' + (it.ext || 'jpg'); }
    function paint() {
      var it = items[i];
      box.innerHTML = '<div class="lb-top"><button data-l="close" aria-label="' + esc(t('close')) + '">' + ICO.x + '</button><span>' + esc(it.t) + '</span>' +
        '<span style="flex:none">' + (i + 1) + ' / ' + items.length + '</span>' +
        '<button data-l="show" class="' + (timer ? 'on' : '') + '" aria-label="' + esc(t('slideshow')) + '">' + ICO.slides + '</button>' +
        '<button data-l="info" class="' + (infoOn ? 'on' : '') + '" aria-label="' + esc(t('info')) + '">' + ICO.info + '</button>' +
        '<a href="' + esc(src(it)) + '" download aria-label="' + esc(t('download')) + '"><button tabindex="-1">' + ICO.dl + '</button></a></div>' +
        '<button class="nv l" data-l="prev" aria-label="' + esc(t('prev')) + '">' + ICO.left + '</button><img alt="" src="' + esc(src(it)) + '">' +
        '<button class="nv r" data-l="next" aria-label="' + esc(t('next')) + '">' + ICO.right + '</button>' +
        (infoOn ? '<div class="lb-info"></div>' : '');
      if (infoOn) loadInfo();
      if (items[i + 1]) { var pre = new Image(); pre.src = src(items[i + 1]); }
    }
    function step(d) { i = (i + d + items.length) % items.length; paint(); }
    function close() { active = false; box.hidden = true; box.innerHTML = ''; clearInterval(timer); timer = 0; }
    function slideshow() { if (timer) { clearInterval(timer); timer = 0; } else timer = setInterval(function () { step(1); }, 4000); paint(); }
    box.addEventListener('click', function (e) {
      var b = e.target.closest('[data-l]');
      if (!b) { if (e.target === box) close(); return; }
      var a = b.getAttribute('data-l');
      if (a === 'close') close(); else if (a === 'prev') step(-1); else if (a === 'next') step(1); else if (a === 'show') slideshow();
      else if (a === 'info') { infoOn = !infoOn; paint(); }
    });
    box.addEventListener('pointerdown', function (e) { startX = e.clientX; startY = e.clientY; });
    box.addEventListener('pointerup', function (e) {
      var dx = e.clientX - startX, dy = e.clientY - startY;
      if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) step(dx < 0 ? 1 : -1);
      else if (dy > 90) close();
    });
    document.addEventListener('keydown', function (e) {
      if (!active) return;
      if (e.key === 'ArrowRight') step(1); else if (e.key === 'ArrowLeft') step(-1);
      else if (e.key === 'Escape' || e.key === 'Backspace') close(); else if (e.key === ' ') slideshow();
      else if (e.key === 'i' || e.key === 'I') { infoOn = !infoOn; paint(); } else return;
      e.preventDefault(); e.stopImmediatePropagation();
    }, true);
    return { open: function (list, it) { items = list; i = Math.max(0, list.indexOf(it)); active = true; box.hidden = false; paint(); } };
  })();

  // ======================= Yükleme paneli =======================
  var Upload = (function () {
    var panel = $('#upload'), q2 = [], busy = false, built = false;
    function build() {
      built = true;
      panel.innerHTML = '<div class="hd"><h2>' + esc(t('upload_title')) + '</h2><button class="pill nav" data-u="close" aria-label="' + esc(t('close')) + '">' + ICO.x + '</button></div>' +
        '<p class="sub" id="upDest" style="white-space:normal"></p>' +
        '<div class="drop" id="drop">' + esc(t('drop')) + ' <label class="nav" tabindex="0">' + esc(t('pick')) + '<input type="file" multiple hidden></label></div><div id="ufs"></div>';
      panel.addEventListener('click', function (e) { if (e.target.closest('[data-u=close]')) close(); });
      var inp = $('input[type=file]', panel);
      inp.addEventListener('change', function () { add(inp.files); inp.value = ''; });
      var drop = $('#drop', panel);
      ['dragenter', 'dragover'].forEach(function (n) { drop.addEventListener(n, function (e) { e.preventDefault(); drop.classList.add('on'); }); });
      ['dragleave', 'drop'].forEach(function (n) { drop.addEventListener(n, function (e) { e.preventDefault(); drop.classList.remove('on'); }); });
      drop.addEventListener('drop', function (e) { add(e.dataTransfer.files); });
    }
    function dest() { return api.dir && C.files ? api.dir : ''; }
    var api = {
      dir: '',
      open: function () {
        if (!C.upload) return;
        if (!built) build();
        $('#upDest', panel).textContent = t('upload_dest', dest() || C.uploadTarget || '');
        panel.hidden = false;
      }
    };
    function close() { panel.hidden = true; }
    function add(files) {
      var box = $('#ufs', panel);
      for (var i = 0; i < files.length; i++) {
        var f = files[i], e = document.createElement('div');
        e.className = 'uf';
        e.innerHTML = '<div class="title"></div><div class="sub"></div><div class="bar2"><i></i></div>';
        $('.title', e).textContent = f.name;
        $('.sub', e).textContent = size(f.size) + ' • ' + t('queued');
        box.appendChild(e);
        q2.push({ f: f, e: e, dir: dest() });
      }
      next();
    }
    function next() {
      if (busy || !q2.length) return;
      busy = true;
      var it = q2.shift(), x = new XMLHttpRequest(), s = $('.sub', it.e), b = $('i', it.e);
      x.open('PUT', '/yukle/' + enc(it.f.name) + (it.dir ? '?dir=' + enc(it.dir) : ''));
      x.upload.onprogress = function (ev) {
        if (!ev.lengthComputable) return;
        var p = ev.loaded / ev.total * 100;
        b.style.width = p + '%';
        s.textContent = size(ev.loaded) + ' / ' + size(ev.total) + ' • ' + Math.floor(p) + '%';
      };
      x.onload = function () {
        s.textContent = x.status === 200 ? size(it.f.size) + ' • ' + t('sent') : t('error') + ' (' + x.status + ')';
        b.style.width = '100%'; busy = false;
        if (!q2.length && x.status === 200) render();
        next();
      };
      x.onerror = function () { s.textContent = t('lost'); busy = false; next(); };
      x.send(it.f);
    }
    // Sayfanın herhangi bir yerine sürükleyince panel açılır
    var depth = 0;
    document.addEventListener('dragenter', function (e) {
      if (!C.upload || !e.dataTransfer || Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') < 0) return;
      depth++; document.body.classList.add('dragging');
    });
    document.addEventListener('dragleave', function () { if (--depth <= 0) { depth = 0; document.body.classList.remove('dragging'); } });
    document.addEventListener('dragover', function (e) { if (C.upload) e.preventDefault(); });
    document.addEventListener('drop', function (e) {
      depth = 0; document.body.classList.remove('dragging');
      if (!C.upload || !e.dataTransfer || !e.dataTransfer.files.length) return;
      e.preventDefault();
      api.open(); add(e.dataTransfer.files);
    });
    return api;
  })();

  // ======================= TV kumandası / klavyeyle gezinme =======================
  var usingKeys = false;
  document.addEventListener('pointerdown', function () { usingKeys = false; }, true);
  document.addEventListener('keydown', function (e) {
    var k = e.key;
    if (k !== 'ArrowUp' && k !== 'ArrowDown' && k !== 'ArrowLeft' && k !== 'ArrowRight') {
      if ((k === 'Backspace' || k === 'BrowserBack') && !player && document.activeElement !== q && location.hash && location.hash !== '#/') { e.preventDefault(); history.back(); }
      return;
    }
    if (player || e.defaultPrevented) return;
    var a = document.activeElement;
    if (a === q && (k === 'ArrowLeft' || k === 'ArrowRight')) return;
    if (a && a.tagName === 'SELECT') return;
    usingKeys = true;
    var all = Array.prototype.slice.call(document.querySelectorAll('.nav, #mini button, #upload .nav')).filter(function (el) {
      if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') return false;
      var r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0;
    });
    if (!all.length) return;
    e.preventDefault();
    if (!a || all.indexOf(a) < 0) { all[0].focus(); return; }
    var r0 = a.getBoundingClientRect(), cx = r0.left + r0.width / 2, cy = r0.top + r0.height / 2;
    var best = null, bestScore = Infinity;
    all.forEach(function (el) {
      if (el === a) return;
      var r = el.getBoundingClientRect(), x = r.left + r.width / 2, y = r.top + r.height / 2;
      var dx = x - cx, dy = y - cy, main, cross;
      if (k === 'ArrowRight') { if (r.left < r0.right - 4) return; main = dx; cross = Math.abs(dy); }
      else if (k === 'ArrowLeft') { if (r.right > r0.left + 4) return; main = -dx; cross = Math.abs(dy); }
      else if (k === 'ArrowDown') { if (r.top < r0.bottom - 4) return; main = dy; cross = Math.abs(dx); }
      else { if (r.bottom > r0.top + 4) return; main = -dy; cross = Math.abs(dx); }
      var score = main + cross * 2.2;
      if (score < bestScore) { bestScore = score; best = el; }
    });
    if (best) {
      best.focus({ preventScroll: true });
      best.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    }
  });

  render();
})();
