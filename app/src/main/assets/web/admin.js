/* Akış web yönetimi: şifre + telefonda onay ile giriş, ayarlar, kitaplık, cihazlar, paylaşımlar. */
(function () {
  'use strict';
  var U, view, waitTimer = 0;

  function call(path, body) {
    var o = { cache: 'no-store', headers: { 'X-Akis': '1' } };
    if (body) { o.method = 'POST'; o.headers['Content-Type'] = 'application/json'; o.body = JSON.stringify(body); }
    return fetch(path, o).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) { j._status = r.status; return j; });
    });
  }
  function t(k) { return U.t.apply(null, arguments); }
  function e(s) { return U.esc(s); }
  function sw(on, attrs) { return '<button class="sw nav' + (on ? ' on' : '') + '" ' + attrs + ' role="switch" aria-checked="' + on + '"></button>'; }
  function when(ms) { return new Date(ms).toLocaleString((window.AKIS && window.AKIS.lang) || undefined, { dateStyle: 'short', timeStyle: 'short' }); }

  // ---------------- Giriş ----------------
  function login(msg) {
    clearTimeout(waitTimer);
    view.innerHTML = '<div class="adm"><form class="login" id="admLogin"><h2>' + e(t('ad_title')) + '</h2>' +
      '<input class="nav" type="password" id="admPw" autocomplete="current-password" placeholder="' + e(t('ad_password')) + '">' +
      (window.AKIS && window.AKIS.totp ? '<input class="nav" type="text" id="admCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="' + e(t('ad_code')) + '">' : '') +
      '<button class="pill acc nav" type="submit" style="justify-content:center">' + e(t('ad_signin')) + '</button>' +
      '<div class="note" id="admMsg">' + e(msg || '') + '</div><div class="note">' + e(t('ad_security')) + '</div></form></div>';
    var f = document.getElementById('admLogin');
    document.getElementById('admPw').focus();
    f.addEventListener('submit', function (ev) {
      ev.preventDefault();
      var pw = document.getElementById('admPw').value;
      if (!pw) return;
      var codeEl = document.getElementById('admCode');
      call('/admin/login', { pw: pw, code: codeEl ? codeEl.value : '' }).then(function (j) {
        if (j.r) wait(j.r);
        else login(t(j.err === 'locked' ? 'ad_locked' : j.err === 'off' ? 'ad_off' : j.err === 'code' ? 'ad_wrong_code' : 'ad_wrong'));
      });
    });
  }
  function wait(id) {
    view.innerHTML = '<div class="adm"><div class="login"><div class="spin" style="margin:10px auto"></div><h2>' + e(t('ad_wait')) + '</h2></div></div>';
    (function poll() {
      call('/admin/status?r=' + encodeURIComponent(id)).then(function (j) {
        if (location.hash.indexOf('#/admin') !== 0) return;
        if (j.s === 'ok') load();
        else if (j.s === 'pending') waitTimer = setTimeout(poll, 1500);
        else login(t(j.s === 'denied' ? 'ad_denied' : 'ad_expired'));
      }).catch(function () { waitTimer = setTimeout(poll, 3000); });
    })();
  }

  // ---------------- Panel ----------------
  var st = null, dirPath = null;
  function load() {
    return call('/admin/state').then(function (j) {
      if (j._status === 401 || j.err === 'login') return login('');
      if (j.err === 'off') return login(t('ad_off'));
      st = j; draw();
    });
  }
  function act(body, okMsg) {
    return call('/admin/do', body).then(function (j) {
      if (j._status === 401) return login('');
      if (j.ok && okMsg !== false) U.toast(t('ad_saved'));
      return load();
    });
  }
  function draw() {
    var h = '<div class="adm"><div class="bar"><nav class="crumbs"><a class="nav" href="#/">' + e(t('home')) + '</a><span>›</span><a>' + e(t('ad_title')) + '</a></nav>' +
      '<button class="pill nav" data-x="out">' + e(t('ad_signout')) + '</button></div>';
    // Sunucu
    h += '<div class="card2"><h2>' + e(t('ad_server')) + '</h2><div class="row"><span class="grow">' + e(t('ad_name')) + '</span>' +
      '<input type="text" class="nav" id="admName" maxlength="60" value="' + e(st.name) + '"><button class="pill nav" data-x="name">' + e(t('ad_save')) + '</button></div>' +
      '<div class="row"><span class="grow">' + e(t('ad_sublang')) + '</span><select class="nav" id="admLang">' +
      st.langs.map(function (l) { return '<option value="' + l + '"' + (l === st.subLang ? ' selected' : '') + '>' + l.toUpperCase() + '</option>'; }).join('') + '</select></div></div>';
    // Özellikler
    h += '<div class="card2"><h2>' + e(t('ad_features')) + '</h2>';
    Object.keys(st.switches).forEach(function (k) {
      h += '<div class="row"><span class="grow">' + e(t('ad_sw_' + k)) + '</span>' + sw(st.switches[k], 'data-sw="' + k + '"') + '</div>';
    });
    h += '<div class="note" style="padding-top:8px">' + e(t('ad_security')) + '</div></div>';
    // Telefon
    if (st.ring) {
      h += '<div class="card2"><h2>' + e(t('ad_phone')) + '</h2><div class="row"><span class="grow">' + e(t('ad_ring_note')) + '</span>' +
        (st.ringing ? '<button class="pill nav" data-x="ringStop">' + e(t('ad_ring_stop')) + '</button>' : '<button class="pill acc nav" data-x="ring">' + e(t('ad_ring')) + '</button>') +
        '</div></div>';
    }
    // Klasörlerim
    h += '<div class="card2"><h2>' + e(t('ad_myfolders')) + '</h2>';
    if (!st.folders.length) h += '<div class="note">' + e(t('ad_none')) + '</div>';
    st.folders.forEach(function (f) {
      h += '<div class="row"><span class="grow">' + e(f.name) + '<div class="sub">' + e(f.path ? f.uri : decodeURIComponent(f.uri).split('tree/').pop()) + '</div></span>' +
        '<button class="pill nav" data-x="rmf" data-v="' + e(f.uri) + '">' + e(t('ad_remove')) + '</button></div>';
    });
    if (st.filesGranted) {
      if (dirPath === null) h += '<div class="row"><button class="pill nav" data-x="pick">' + U.ICO.folder + e(t('ad_add_folder')) + '</button></div>';
      else h += '<div id="admDirs"><div class="spin" style="margin:10px auto"></div></div>';
    } else h += '<div class="note" style="padding-top:8px">' + e(t('ad_need_perm')) + '</div>';
    h += '</div>';
    // Gizli klasörler
    if (st.switches.hideFolders) {
      h += '<div class="card2"><h2>' + e(t('ad_hidden')) + '</h2>';
      st.buckets.forEach(function (b) {
        h += '<div class="row"><span class="grow">' + e(b.name) + '<div class="sub">' + e(t('items_' + (b.n === 1 ? 'one' : 'other'), b.n)) + '</div></span>' +
          sw(b.hidden, 'data-hide="' + e(b.id) + '"') + '</div>';
      });
      h += '</div>';
    }
    // Cihazlar
    h += '<div class="card2"><h2>' + e(t('ad_devices')) + '</h2><div class="note">' + e(t('ad_connected')) + '</div>';
    if (!st.clients.length) h += '<div class="note">' + e(t('ad_none')) + '</div>';
    st.clients.forEach(function (c) {
      var me = c.ip === st.me;
      h += '<div class="row"><span class="grow">' + e(c.name) + '<div class="sub">' + e(c.ip) + '</div></span>' +
        (me ? '<span class="note">' + e(t('ad_you')) + '</span>' :
          '<button class="pill nav" data-x="kick" data-v="' + e(c.ip) + '">' + e(t('ad_kick')) + '</button>' +
          '<button class="pill nav" data-x="ban" data-v="' + e(c.ip) + '">' + e(t('ad_ban')) + '</button>') + '</div>';
    });
    var blocked = st.known.filter(function (d) { return !d.allowed; });
    if (blocked.length) {
      h += '<div class="note" style="padding-top:10px">' + e(t('ad_blocked')) + '</div>';
      blocked.forEach(function (d) {
        h += '<div class="row"><span class="grow">' + e(d.name) + '<div class="sub">' + e(d.ip) + ' • ' + e(d.until ? t('ad_banned_until', when(d.until)) : t('ad_blocked_tag')) + '</div></span>' +
          '<button class="pill nav" data-x="unb" data-v="' + e(d.ip) + '">' + e(t('ad_unblock')) + '</button></div>';
      });
    }
    h += '</div>';
    // Paylaşımlar
    if (st.shares.length) {
      h += '<div class="card2"><h2>' + e(t('ad_shares')) + '</h2>';
      st.shares.forEach(function (x) {
        h += '<div class="row"><span class="grow">' + e(x.name) + '<div class="sub">' + e(when(x.expires)) + '</div></span>' +
          '<button class="pill nav" data-x="shc" data-v="' + e(x.token) + '">' + e(t('ad_close')) + '</button></div>';
      });
      h += '</div>';
    }
    view.innerHTML = h + '</div>';
    if (dirPath !== null) dirs(dirPath);
    var lang = document.getElementById('admLang');
    if (lang) lang.addEventListener('change', function () { act({ op: 'set', key: 'subLang', value: lang.value }); });
  }

  function dirs(p) {
    dirPath = p;
    var box = document.getElementById('admDirs');
    if (!box) { draw(); return; }
    call('/admin/dirs?p=' + encodeURIComponent(p)).then(function (j) {
      if (!document.getElementById('admDirs')) return;
      var h = '<div class="row"><span class="grow"><b>' + e(j.path || '/') + '</b></span>' +
        (j.path ? '<button class="pill acc nav" data-x="addf" data-v="' + e(j.path) + '">' + e(t('ad_add_here')) + '</button>' : '') +
        '<button class="pill nav" data-x="pickx">' + e(t('cancel')) + '</button></div>';
      if (j.path) h += '<div class="row"><a class="nav" href="javascript:void 0" data-x="cd" data-v="' + e(j.parent || '') + '">↩ ..</a></div>';
      (j.items || []).forEach(function (d) {
        h += '<div class="row"><a class="nav grow" href="javascript:void 0" data-x="cd" data-v="' + e(d.p) + '">' + U.ICO.folder + ' ' + e(d.n) + '</a></div>';
      });
      document.getElementById('admDirs').innerHTML = h;
    });
  }

  function onClick(ev) {
    if (location.hash.indexOf('#/admin') !== 0) return;
    var b = ev.target.closest('[data-x],[data-sw],[data-hide]');
    if (!b || !view.contains(b)) return;
    ev.preventDefault();
    if (b.hasAttribute('data-sw')) { var k = b.getAttribute('data-sw'); act({ op: 'set', key: k, value: !st.switches[k] }); return; }
    if (b.hasAttribute('data-hide')) { act({ op: 'hide', bucket: b.getAttribute('data-hide'), on: !b.classList.contains('on') }); return; }
    var x = b.getAttribute('data-x'), v = b.getAttribute('data-v');
    if (x === 'out') call('/admin/logout', {}).then(function () { login(''); });
    else if (x === 'name') act({ op: 'set', key: 'name', value: document.getElementById('admName').value });
    else if (x === 'rmf') act({ op: 'folderRemove', uri: v });
    else if (x === 'pick') { dirPath = ''; draw(); }
    else if (x === 'pickx') { dirPath = null; draw(); }
    else if (x === 'cd') dirs(v);
    else if (x === 'addf') { dirPath = null; act({ op: 'folderAdd', path: v }); }
    else if (x === 'kick') act({ op: 'kick', ip: v });
    else if (x === 'ban') act({ op: 'ban', ip: v, min: 60 });
    else if (x === 'unb') act({ op: 'unblock', ip: v });
    else if (x === 'shc') act({ op: 'shareClose', token: v });
    else if (x === 'ring') act({ op: 'ring' }, false).then(function () { setTimeout(load, 31000); });
    else if (x === 'ringStop') act({ op: 'ringStop' }, false);
  }

  window.AkisAdmin = {
    render: function (v, utils) {
      U = utils; view = v; dirPath = null;
      if (!view._admBound) { view.addEventListener('click', onClick); view._admBound = true; }
      return load();
    }
  };
})();
