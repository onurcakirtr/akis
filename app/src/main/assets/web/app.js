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
      return r.json();
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
  var upBtn = $('#upBtn');
  if (C.upload) { upBtn.hidden = false; $('#upLabel').textContent = t('upload'); upBtn.addEventListener('click', function () { Upload.open(); }); }
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
    var map = { '': 'home', home: 'home', f: 'folder', s: 'search', v: 'watch', d: 'files', up: 'upload' };
    return { name: map[name] || 'home', arg: arg, p: params, key: location.hash };
  }
  var player = null, list = [], listFolder = '0', scrolls = {}, lastKey = '';
  window.addEventListener('hashchange', render);
  window.addEventListener('scroll', function () { scrolls[lastKey] = window.scrollY; }, { passive: true });

  function render() {
    var r = route();
    if (r.name === 'upload') { Upload.open(); history.replaceState(null, '', '#/'); r = route(); }
    if (player) { player.destroy(); player = null; }
    lastKey = r.key;
    Upload.dir = '';
    if (r.name !== 'search' && document.activeElement !== q) q.value = '';
    view.innerHTML = '<div class="spin"></div>';
    var done = function () {
      var y = scrolls[r.key];
      window.scrollTo(0, y || 0);
      if (!player) { var f = view.querySelector('.nav'); if (f && usingKeys) f.focus({ preventScroll: true }); }
    };
    var p;
    if (r.name === 'folder') p = renderFolder(r.arg || '0');
    else if (r.name === 'search') { if (document.activeElement !== q) q.value = r.arg; p = renderSearch(r.arg); }
    else if (r.name === 'watch') p = renderWatch(r.arg, r.p['in'] || '');
    else if (r.name === 'files') p = renderFiles(r.arg || '');
    else p = renderHome();
    Promise.resolve(p).then(done).catch(function (e) {
      if (e && e.message === 'auth') return;
      view.innerHTML = '<div class="empty">' + esc(t('error_load')) + '</div>';
    });
  }

  // ======================= Kartlar =======================
  function thumb(it) {
    if (it.k === 'f') return '<div class="thumb"><div class="icon-badge">' + (CAT_ICON[it.id] || ICO.folder) + '</div></div>';
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
    var src = a.getAttribute('data-src') === 'shelf' ? shelves[a.getAttribute('data-shelf')] : list;
    var it = src && src[+a.getAttribute('data-i')];
    if (!it) return;
    if (it.k === 'a') { e.preventDefault(); Music.play(src.filter(function (x) { return x.k === 'a'; }), it); }
    else if (it.k === 'i') { e.preventDefault(); Lightbox.open(src.filter(function (x) { return x.k === 'i'; }), it); }
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
    h += '<div class="seg"><button class="nav' + (mode === 'grid' ? ' on' : '') + '" data-vm="grid" aria-label="' + esc(t('view_grid')) + '">' + ICO.grid + '</button>' +
      '<button class="nav' + (mode === 'list' ? ' on' : '') + '" data-vm="list" aria-label="' + esc(t('view_list')) + '">' + ICO.list + '</button></div></div>';
    return h;
  }
  function bindToolbar(rerender) {
    var s = $('#sortSel');
    if (s) s.addEventListener('change', function () { put('sort', s.value); rerender(); });
    view.querySelectorAll('[data-vm]').forEach(function (b) { b.addEventListener('click', function () { put('view', b.getAttribute('data-vm')); rerender(); }); });
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
        '<button class="pill nav" id="copyLink">' + ICO.link + esc(t('copy_link')) + '</button></div></div>' +
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
      active: function () { return !bar.hidden && !audio.paused; },
      pause: function () { audio.pause(); }
    };
  })();

  // ======================= Fotoğraf görüntüleyici =======================
  var Lightbox = (function () {
    var box = $('#lightbox'), items = [], i = 0, timer = 0, startX = 0, startY = 0, active = false;
    function src(it) { return '/m/' + enc(it.id) + '/file.' + (it.ext || 'jpg'); }
    function paint() {
      var it = items[i];
      box.innerHTML = '<div class="lb-top"><button data-l="close" aria-label="' + esc(t('close')) + '">' + ICO.x + '</button><span>' + esc(it.t) + '</span>' +
        '<span style="flex:none">' + (i + 1) + ' / ' + items.length + '</span>' +
        '<button data-l="show" class="' + (timer ? 'on' : '') + '" aria-label="' + esc(t('slideshow')) + '">' + ICO.slides + '</button>' +
        '<a href="' + esc(src(it)) + '" download aria-label="' + esc(t('download')) + '"><button tabindex="-1">' + ICO.dl + '</button></a></div>' +
        '<button class="nv l" data-l="prev" aria-label="' + esc(t('prev')) + '">' + ICO.left + '</button><img alt="" src="' + esc(src(it)) + '">' +
        '<button class="nv r" data-l="next" aria-label="' + esc(t('next')) + '">' + ICO.right + '</button>';
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
      else if (e.key === 'Escape' || e.key === 'Backspace') close(); else if (e.key === ' ') slideshow(); else return;
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
