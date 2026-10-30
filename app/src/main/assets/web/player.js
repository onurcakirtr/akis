/*
 * Akış oynatıcısı.
 *  Motor: telefon dosyayı (MKV/MP4/WebM/TS) fMP4'e çevirip /x/<id> adresinden akıtır; burada MediaSource'a beslenir.
 *         Tamponlama, sarma, geri bellek temizliği ve ses parçası seçimi bizde. Tarayıcının oynatıcı arayüzü kullanılmaz.
 *  Arayüz: kendi kontrollerimiz, altyazı çizici, dokunmatik hareketler, klavye kısayolları.
 */
(function () {
  'use strict';

  var T = (window.AKIS && window.AKIS.t) || {};
  function t(k) {
    var s = T[k] || k;
    for (var i = 1; i < arguments.length; i++) s = s.split('{' + (i - 1) + '}').join(arguments[i]);
    return s;
  }
  function enc(s) { return encodeURIComponent(s); }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function fmt(sec) {
    if (!isFinite(sec) || sec < 0) sec = 0;
    sec = Math.floor(sec);
    var h = Math.floor(sec / 3600), m = Math.floor(sec / 60) % 60, s = sec % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (s < 10 ? '0' : '') + s;
  }
  var langNames = null;
  try { langNames = new Intl.DisplayNames([(window.AKIS && window.AKIS.lang) || 'en'], { type: 'language' }); } catch (e) { }
  function langName(code) {
    if (!code) return '';
    try { var n = langNames && langNames.of(code); if (n && n !== code) return n.charAt(0).toUpperCase() + n.slice(1); } catch (e) { }
    return code.toUpperCase();
  }
  function get(k, d) { try { var v = localStorage.getItem('akis.' + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } }
  function put(k, v) { try { localStorage.setItem('akis.' + k, JSON.stringify(v)); } catch (e) { } }

  var I = {
    play: '<svg viewBox="0 0 24 24"><path d="M8 5.5v13l10.5-6.5z"/></svg>',
    pause: '<svg viewBox="0 0 24 24"><path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"/></svg>',
    back: '<svg viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></svg>',
    rew: '<svg viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><text x="12" y="15.5" font-size="7" text-anchor="middle" fill="currentColor" stroke="none" font-family="sans-serif" font-weight="700">10</text></svg>',
    fwd: '<svg viewBox="0 0 24 24"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/><text x="12" y="15.5" font-size="7" text-anchor="middle" fill="currentColor" stroke="none" font-family="sans-serif" font-weight="700">10</text></svg>',
    vol: '<svg viewBox="0 0 24 24"><path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"/></svg>',
    mute: '<svg viewBox="0 0 24 24"><path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M17 9l5 6M22 9l-5 6"/></svg>',
    fs: '<svg viewBox="0 0 24 24"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg>',
    fit: '<svg viewBox="0 0 24 24"><rect x="3" y="6" width="18" height="12" rx="2"/><path d="M7 10v4M17 10v4M7 12h3M14 12h3"/></svg>',
    fsx: '<svg viewBox="0 0 24 24"><path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"/></svg>',
    pip: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><rect x="12" y="11" width="7" height="6" rx="1" fill="currentColor"/></svg>',
    cc: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M10.5 10.5a2 2 0 1 0 0 3M16.5 10.5a2 2 0 1 0 0 3"/></svg>',
    gear: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
    next: '<svg viewBox="0 0 24 24"><path d="M5 5.5v13L15 12zM17 5h2.5v14H17z" fill="currentColor" stroke="none"/></svg>'
  };

  var MS = window.ManagedMediaSource || window.MediaSource;

  // ======================= Motor: telefondaki fMP4 akışı → MediaSource =======================

  function Engine(video, id, dur, choice, onFatal) {
    this.video = video; this.id = id; this.dur = dur;
    this.vcodec = choice.vcodec; this.a = choice.a; this.tx = choice.tx; this.acodec = choice.acodec;
    this.onFatal = onFatal;
    this.gen = 0; this.queue = []; this.qbytes = 0; this.sb = null; this.done = false; this.ended = false;
  }
  Engine.mime = function (vcodec, acodec) {
    var c = []; if (vcodec) c.push(vcodec); if (acodec) c.push(acodec);
    return 'video/mp4; codecs="' + c.join(', ') + '"';
  };
  Engine.prototype.mime = function () { return Engine.mime(this.vcodec, this.a >= 0 ? this.acodec : null); };
  Engine.prototype.open = function (start) {
    var self = this;
    this.ms = new MS();
    if (window.ManagedMediaSource && MS === window.ManagedMediaSource) this.video.disableRemotePlayback = true;
    this.url = URL.createObjectURL(this.ms);
    this.video.src = this.url;
    this.ms.addEventListener('sourceopen', function () {
      try {
        if (self.dur > 0) self.ms.duration = self.dur;
        self.sb = self.ms.addSourceBuffer(self.mime());
        self.sb.mode = 'segments';
      } catch (e) { self.onFatal(e); return; }
      self.sb.addEventListener('updateend', function () { self.pump(); });
      self.sb.addEventListener('error', function () { self.onFatal(new Error('SourceBuffer')); });
      self.video.currentTime = start;
      self.load(start);
    }, { once: true });
  };
  Engine.prototype.ahead = function () {
    var b = this.video.buffered, ct = this.video.currentTime;
    for (var i = 0; i < b.length; i++) if (ct >= b.start(i) - 0.3 && ct <= b.end(i)) return b.end(i) - ct;
    return 0;
  };
  Engine.prototype.inBuffer = function (x) {
    var b = this.video.buffered;
    for (var i = 0; i < b.length; i++) if (x >= b.start(i) && x < b.end(i) - 0.5) return true;
    return false;
  };
  Engine.prototype.load = function (start) {
    var self = this, gen = ++this.gen;
    if (this.ctrl) this.ctrl.abort();
    this.ctrl = new AbortController();
    this.queue = []; this.qbytes = 0; this.done = false;
    var url = '/x/' + enc(this.id) + '?t=' + start.toFixed(2) + '&a=' + this.a + '&tx=' + (this.tx ? 1 : 0);
    fetch(url, { signal: this.ctrl.signal, cache: 'no-store' }).then(function (r) {
      if (!r.ok || !r.body) throw new Error('HTTP ' + r.status);
      var reader = r.body.getReader();
      function step() {
        if (gen !== self.gen) { reader.cancel(); return; }
        // Önde yeterince tampon varsa (≈90 sn) okumayı beklet
        if (self.ahead() > 90 || self.qbytes > 24e6) { setTimeout(step, 400); return; }
        return reader.read().then(function (x) {
          if (gen !== self.gen) { reader.cancel(); return; }
          if (x.done) { self.done = true; self.pump(); return; }
          self.queue.push(x.value); self.qbytes += x.value.length;
          self.pump();
          return step();
        });
      }
      return step();
    }).catch(function (e) {
      if (gen === self.gen && e.name !== 'AbortError') self.onFatal(e);
    });
  };
  Engine.prototype.pump = function () {
    var sb = this.sb;
    if (!sb || sb.updating || !this.ms || this.ms.readyState === 'closed') return;
    if (this.queue.length) {
      var parts = [], total = 0;
      while (this.queue.length && total < 2e6) { var c = this.queue.shift(); parts.push(c); total += c.length; }
      this.qbytes -= total;
      var buf = parts[0];
      if (parts.length > 1) {
        buf = new Uint8Array(total);
        for (var i = 0, o = 0; i < parts.length; i++) { buf.set(parts[i], o); o += parts[i].length; }
      }
      try {
        this.ended = false;
        sb.appendBuffer(buf);
      } catch (e) {
        if (e.name === 'QuotaExceededError') {
          this.queue.unshift(buf); this.qbytes += total;
          if (!this.evict(true)) setTimeout(this.pump.bind(this), 800);
        } else this.onFatal(e);
      }
      return;
    }
    if (this.done && !this.ended && this.ms.readyState === 'open') {
      try { this.ms.endOfStream(); this.ended = true; } catch (e) { }
      return;
    }
    this.evict(false);
  };
  /** Geride kalan tamponu siler. @return silme başladı mı */
  Engine.prototype.evict = function (force) {
    var sb = this.sb, b = sb.buffered, ct = this.video.currentTime;
    if (!b.length || sb.updating) return false;
    var keep = force ? 8 : 60;
    if (ct - b.start(0) > keep + (force ? 2 : 60)) {
      try { sb.remove(b.start(0), ct - keep); return true; } catch (e) { }
    }
    return false;
  };
  Engine.prototype.seek = function (x) {
    var self = this;
    if (this.inBuffer(x)) { this.video.currentTime = x; return; }
    this.gen++;
    if (this.ctrl) this.ctrl.abort();
    this.queue = []; this.qbytes = 0; this.done = false;
    function go() {
      if (!self.sb) return;
      if (self.sb.updating) { self.sb.addEventListener('updateend', go, { once: true }); return; }
      try { self.sb.abort(); } catch (e) { }
      var b = self.sb.buffered;
      var restart = function () { self.video.currentTime = x; self.load(x); };
      if (b.length) {
        try {
          self.sb.remove(0, b.end(b.length - 1) + 1);
          self.sb.addEventListener('updateend', restart, { once: true });
        } catch (e) { restart(); }
      } else restart();
    }
    go();
  };
  Engine.prototype.destroy = function () {
    this.gen++;
    if (this.ctrl) this.ctrl.abort();
    this.queue = [];
    try { if (this.ms && this.ms.readyState === 'open') this.ms.endOfStream(); } catch (e) { }
    this.video.removeAttribute('src');
    try { this.video.load(); } catch (e) { }
    if (this.url) URL.revokeObjectURL(this.url);
    this.sb = null;
  };

  /** Telefon dönüştüremezse ya da MediaSource yoksa: dosyayı doğrudan aynı yüzeye ver. */
  function Direct(video, url) { this.video = video; this.url = url; }
  Direct.prototype.open = function (start) {
    var v = this.video;
    v.src = this.url;
    if (start > 0) v.addEventListener('loadedmetadata', function () { v.currentTime = start; }, { once: true });
  };
  Direct.prototype.seek = function (x) { this.video.currentTime = x; };
  Direct.prototype.destroy = function () { this.video.removeAttribute('src'); try { this.video.load(); } catch (e) { } };

  // ======================= Altyazı =======================

  function parseTime(s) {
    var m = /(?:(\d+):)?(\d+):(\d+)[.,](\d+)/.exec(s);
    if (!m) return NaN;
    return (+(m[1] || 0)) * 3600 + (+m[2]) * 60 + (+m[3]) + (+(m[4] + '00').slice(0, 3)) / 1000;
  }
  function parseVtt(text) {
    var cues = [];
    var blocks = text.replace(/\r/g, '').split(/\n\s*\n/);
    for (var i = 0; i < blocks.length; i++) {
      var lines = blocks[i].split('\n');
      for (var j = 0; j < lines.length; j++) {
        if (lines[j].indexOf('-->') < 0) continue;
        var p = lines[j].split('-->');
        var a = parseTime(p[0]), b = parseTime(p[1]);
        var body = lines.slice(j + 1).join('\n').trim();
        if (isFinite(a) && isFinite(b) && body) {
          // Yalnızca <i>, <b>, <u> kalsın
          var html = esc(body).replace(/&lt;(\/?)([ibu])&gt;/g, '<$1$2>');
          cues.push({ a: a, b: b, h: html });
        }
        break;
      }
    }
    return cues;
  }

  // ======================= Arayüz =======================

  /**
   * @param host  oynatıcının yerleşeceği eleman
   * @param info  /api/item cevabı
   * @param opts  { onBack(), onNext(item), inFolder }
   */
  function mount(host, info, opts) {
    var item = info.item, id = item.id;
    var dur = (item.d || 0) / 1000;
    var root = document.createElement('div');
    root.className = 'pl ui';
    root.tabIndex = 0;
    root.innerHTML =
      '<video playsinline webkit-playsinline preload="auto"></video>' +
      '<div class="subs"></div><div class="tap"></div>' +
      '<div class="layer">' +
      '<div class="top"><button class="ib" data-a="back" aria-label="' + esc(t('back')) + '">' + I.back + '</button>' +
      '<div class="t">' + esc(item.t) + '</div>' +
      '<button class="ib" data-a="cc" aria-label="' + esc(t('subtitles')) + '">' + I.cc + '</button>' +
      '<button class="ib" data-a="gear" aria-label="' + esc(t('settings')) + '">' + I.gear + '</button></div>' +
      '<div class="mid"><button class="skip" data-a="rew" aria-label="' + esc(t('seek_back')) + '">' + I.rew + '</button>' +
      '<button class="big" data-a="toggle" aria-label="' + esc(t('play')) + '">' + I.play + '</button>' +
      '<button class="skip" data-a="fwd" aria-label="' + esc(t('seek_fwd')) + '">' + I.fwd + '</button></div>' +
      '<div class="bottom">' +
      '<div class="seek" role="slider" tabindex="0" aria-label="' + esc(t('position')) + '"><div class="tr"><div class="bf"></div><div class="pg"></div></div><div class="kn"></div><div class="tip">0:00</div></div>' +
      '<div class="row">' +
      '<button class="ib fill" data-a="toggle" aria-label="' + esc(t('play')) + '">' + I.play + '</button>' +
      '<button class="ib" data-a="rew" aria-label="' + esc(t('seek_back')) + '">' + I.rew + '</button>' +
      '<button class="ib" data-a="fwd" aria-label="' + esc(t('seek_fwd')) + '">' + I.fwd + '</button>' +
      '<span class="volw" style="display:flex;align-items:center"><button class="ib" data-a="mute" aria-label="' + esc(t('mute')) + '">' + I.vol + '</button>' +
      '<span class="vol"><input type="range" min="0" max="1" step="0.05" aria-label="' + esc(t('volume')) + '"></span></span>' +
      '<span class="time">0:00 / 0:00</span><span class="grow"></span>' +
      (info.next ? '<button class="ib" data-a="next" aria-label="' + esc(t('next')) + '">' + I.next + '</button>' : '') +
      ((document.pictureInPictureEnabled) ? '<button class="ib" data-a="pip" aria-label="' + esc(t('pip')) + '">' + I.pip + '</button>' : '') +
      '<button class="ib" data-a="fit" aria-label="' + esc(t('fit')) + '">' + I.fit + '</button>' +
      '<button class="ib" data-a="fs" aria-label="' + esc(t('fullscreen')) + '">' + I.fs + '</button>' +
      '</div></div></div>' +
      '<div class="spinner"></div><div class="flash"></div>';
    host.appendChild(root);

    var video = root.querySelector('video');
    var subsEl = root.querySelector('.subs');
    var tap = root.querySelector('.tap');
    var seekEl = root.querySelector('.seek'), pg = root.querySelector('.pg'), bf = root.querySelector('.bf'), kn = root.querySelector('.kn'), tip = root.querySelector('.tip');
    var timeEl = root.querySelector('.time'), spinner = root.querySelector('.spinner'), flashEl = root.querySelector('.flash');
    var volIn = root.querySelector('.vol input');
    var engine = null, probe = null, choice = null, alive = true, uiTimer = 0, menu = null, chip = null, nextCard = null;
    var cues = [], subIdx = -1, subs = info.subs || [], tried = {};
    var lastReport = 0, dragging = null;
    var subSize = get('subSize', 1), subBg = get('subBg', 'semi'), subPos = get('subPos', 'low'), delay = get('delay.' + id, 0);
    function applySubStyle() {
      subsEl.style.setProperty('--ss', subSize);
      subsEl.setAttribute('data-bg', subBg);
      subsEl.setAttribute('data-pos', subPos);
    }
    applySubStyle();
    function setDelay(d) {
      delay = Math.round(d * 10) / 10;
      put('delay.' + id, delay);
      flash(t('sync_val', (delay > 0 ? '+' : '') + delay.toFixed(1) + ' s'), 0);
      renderSubs();
    }

    video.volume = get('vol', 1);
    video.muted = get('muted', false);
    volIn.value = video.muted ? 0 : video.volume;
    video.playbackRate = get('rate', 1);
    var FITS = ['contain', 'cover', 'fill'];
    var fit = get('fit', 'contain'), bright = get('bright', 1);
    function applyLook() {
      video.style.objectFit = fit;
      video.style.filter = bright === 1 ? '' : 'brightness(' + bright + ')';
    }
    applyLook();
    function cycleFit() {
      fit = FITS[(FITS.indexOf(fit) + 1) % FITS.length];
      put('fit', fit); applyLook();
      flash(t('fit_' + fit), 0);
    }

    function dura() { return dur > 0 ? dur : (isFinite(video.duration) ? video.duration : 0); }

    // ---------- görünürlük ----------
    function showUI(keep) {
      root.classList.add('ui'); root.classList.remove('hide-cursor');
      clearTimeout(uiTimer);
      if (!keep && !video.paused && !menu) uiTimer = setTimeout(function () { root.classList.remove('ui'); root.classList.add('hide-cursor'); }, 2600);
    }
    function hideUI() { if (!video.paused && !menu) { root.classList.remove('ui'); root.classList.add('hide-cursor'); } }
    var flashTimer = 0;
    function flash(text, side) {
      flashEl.textContent = text;
      flashEl.style.left = side < 0 ? '22%' : side > 0 ? '78%' : '50%';
      flashEl.classList.add('on');
      clearTimeout(flashTimer);
      flashTimer = setTimeout(function () { flashEl.classList.remove('on'); }, 700);
    }

    // ---------- oynatma ----------
    function play() { var p = video.play(); if (p && p.catch) p.catch(function () { showUI(true); }); }
    function toggle() { if (video.paused) play(); else video.pause(); showUI(); }
    function seek(x) {
      x = clamp(x, 0, Math.max(0, dura() - 0.3));
      if (engine) engine.seek(x);
      updateTime(x);
    }
    function skip(d) { seek(video.currentTime + d); flash((d > 0 ? '+' : '−') + Math.abs(d) + ' s', d); showUI(); }
    function setVol(v) {
      v = clamp(v, 0, 1); video.volume = v; video.muted = v === 0; volIn.value = v;
      put('vol', v); put('muted', video.muted);
      flash(Math.round(v * 100) + '%', 0);
    }
    function toggleMute() { video.muted = !video.muted; put('muted', video.muted); volIn.value = video.muted ? 0 : video.volume; }
    function setRate(r) { video.playbackRate = r; put('rate', r); flash(r + '×', 0); }

    function isFs() { return document.fullscreenElement === root || document.webkitFullscreenElement === root; }
    function toggleFs() {
      if (isFs()) { (document.exitFullscreen || document.webkitExitFullscreen).call(document); return; }
      var req = root.requestFullscreen || root.webkitRequestFullscreen;
      if (req) {
        var p = req.call(root);
        if (p && p.then) p.then(function () { try { screen.orientation.lock('landscape').catch(function () { }); } catch (e) { } }).catch(function () { });
      } else if (video.webkitEnterFullscreen) video.webkitEnterFullscreen();
    }
    function onFsChange() {
      var b = root.querySelector('[data-a=fs]');
      if (b) b.innerHTML = isFs() ? I.fsx : I.fs;
    }
    document.addEventListener('fullscreenchange', onFsChange);
    document.addEventListener('webkitfullscreenchange', onFsChange);

    // ---------- zaman ve çubuk ----------
    function bufferedEnd(ct) {
      var b = video.buffered;
      for (var i = 0; i < b.length; i++) if (ct >= b.start(i) - 0.5 && ct <= b.end(i) + 0.5) return b.end(i);
      return ct;
    }
    function updateTime(ct) {
      var d = dura();
      if (ct === undefined) ct = video.currentTime;
      var f = d > 0 ? clamp(ct / d, 0, 1) : 0;
      if (!dragging) {
        pg.style.width = (f * 100) + '%';
        kn.style.left = (f * 100) + '%';
      }
      bf.style.width = (d > 0 ? clamp(bufferedEnd(ct) / d, 0, 1) * 100 : 0) + '%';
      timeEl.textContent = fmt(ct) + ' / ' + fmt(d);
    }
    function renderSubs() {
      if (subIdx < 0 || !cues.length) { if (subsEl.innerHTML) subsEl.innerHTML = ''; return; }
      var ct = video.currentTime - delay, html = '';
      for (var i = 0; i < cues.length; i++) {
        var c = cues[i];
        if (c.a > ct) break;
        if (ct < c.b) html += (html ? '<br>' : '') + '<span>' + c.h + '</span>';
      }
      if (subsEl.innerHTML !== html) subsEl.innerHTML = html;
    }
    var raf = 0;
    function loop() {
      if (!alive) return;
      updateTime(); renderSubs();
      raf = requestAnimationFrame(loop);
    }
    raf = requestAnimationFrame(loop);

    function report(force) {
      if (!window.AKIS.history || item.k !== 'v') return;
      var now = Date.now();
      if (!force && now - lastReport < 10000) return;
      lastReport = now;
      var d = dura();
      if (d <= 0) return;
      try { fetch('/h?id=' + enc(id) + '&t=' + video.currentTime.toFixed(1) + '&d=' + d.toFixed(1), { method: 'POST', keepalive: true }); } catch (e) { }
    }

    // ---------- sarma çubuğu (fare + dokunmatik) ----------
    function fracAt(e) { var r = seekEl.getBoundingClientRect(); return clamp((e.clientX - r.left) / r.width, 0, 1); }
    seekEl.addEventListener('pointermove', function (e) {
      var f = fracAt(e);
      tip.style.left = (f * 100) + '%';
      tip.textContent = fmt(f * dura());
      if (dragging) { dragging.f = f; pg.style.width = kn.style.left = (f * 100) + '%'; showUI(true); }
    });
    seekEl.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      seekEl.setPointerCapture(e.pointerId);
      seekEl.classList.add('drag');
      dragging = { f: fracAt(e) };
      pg.style.width = kn.style.left = (dragging.f * 100) + '%';
      tip.style.left = (dragging.f * 100) + '%'; tip.textContent = fmt(dragging.f * dura());
      showUI(true);
    });
    function endDrag() {
      if (!dragging) return;
      var f = dragging.f; dragging = null;
      seekEl.classList.remove('drag');
      seek(f * dura()); showUI();
    }
    seekEl.addEventListener('pointerup', endDrag);
    seekEl.addEventListener('pointercancel', endDrag);

    // ---------- düğmeler ----------
    root.addEventListener('click', function (e) {
      var b = e.target.closest('[data-a]');
      if (!b || !root.contains(b)) return;
      e.stopPropagation();
      var a = b.getAttribute('data-a');
      if (a === 'toggle') toggle();
      else if (a === 'rew') skip(-10);
      else if (a === 'fwd') skip(10);
      else if (a === 'mute') toggleMute();
      else if (a === 'fs') toggleFs();
      else if (a === 'fit') cycleFit();
      else if (a === 'pip') { if (document.pictureInPictureElement) document.exitPictureInPicture(); else video.requestPictureInPicture().catch(function () { }); }
      else if (a === 'next') goNext();
      else if (a === 'back') opts.onBack();
      else if (a === 'cc') openMenu('cc');
      else if (a === 'gear') openMenu('gear');
      showUI();
    });
    volIn.addEventListener('input', function () { setVol(+volIn.value); });

    // ---------- menüler ----------
    function closeMenu() { if (menu) { menu.remove(); menu = null; showUI(); } }
    function openMenu(kind) {
      if (menu) { var same = menu.getAttribute('data-k') === kind; closeMenu(); if (same) return; }
      menu = document.createElement('div');
      menu.className = 'menu'; menu.setAttribute('data-k', kind);
      var h = '';
      if (kind === 'cc') {
        var auds = audioTracks();
        if (auds.length > 1) {
          h += '<h4>' + esc(t('audio_track')) + '</h4>';
          auds.forEach(function (x, i) {
            h += '<button data-au="' + x.i + '" class="' + (choice && choice.a === x.i ? 'on' : '') + '">' + esc(audioLabel(x, i)) + '</button>';
          });
        }
        h += '<h4>' + esc(t('subtitles')) + '</h4><button data-s="-1" class="' + (subIdx < 0 ? 'on' : '') + '">' + esc(t('off')) + '</button>';
        subs.forEach(function (s, i) { h += '<button data-s="' + i + '" class="' + (subIdx === i ? 'on' : '') + '">' + esc(s.l) + '</button>'; });
        if (!subs.length) h += '<h4>' + esc(t('no_subs')) + '</h4>';
        if (subs.length) {
          var opt = function (key, val, label, cur) { return '<button data-ss="' + key + ':' + val + '" class="' + (cur === val ? 'on' : '') + '">' + esc(label) + '</button>'; };
          h += '<h4>' + esc(t('sub_style')) + '</h4>' +
            '<div class="opts"><span>' + esc(t('size')) + '</span>' + opt('size', 0.85, 'S', subSize) + opt('size', 1, 'M', subSize) + opt('size', 1.25, 'L', subSize) + opt('size', 1.6, 'XL', subSize) + '</div>' +
            '<div class="opts"><span>' + esc(t('background')) + '</span>' + opt('bg', 'none', t('bg_none'), subBg) + opt('bg', 'semi', t('bg_semi'), subBg) + opt('bg', 'solid', t('bg_solid'), subBg) + '</div>' +
            '<div class="opts"><span>' + esc(t('position')) + '</span>' + opt('pos', 'low', t('pos_low'), subPos) + opt('pos', 'high', t('pos_high'), subPos) + '</div>' +
            '<div class="opts"><span>' + esc(t('sync')) + '</span><button data-ss="delay:-0.1">−</button><b>' + (delay > 0 ? '+' : '') + delay.toFixed(1) + ' s</b>' +
            '<button data-ss="delay:0.1">+</button><button data-ss="delay:0">' + esc(t('reset')) + '</button></div>';
        }
      } else {
        h += '<h4>' + esc(t('speed')) + '</h4>';
        [0.5, 0.75, 1, 1.25, 1.5, 2].forEach(function (r) {
          h += '<button data-r="' + r + '" class="' + (video.playbackRate === r ? 'on' : '') + '">' + (r === 1 ? esc(t('normal')) : r + '×') + '</button>';
        });
        if (info.next) {
          h += '<h4>' + esc(t('autoplay')) + '</h4><button data-ap="1" class="' + (get('autoplay', true) ? 'on' : '') + '">' + esc(get('autoplay', true) ? t('on') : t('off')) + '</button>';
        }
      }
      menu.innerHTML = h;
      menu.addEventListener('click', function (e) {
        e.stopPropagation();
        var b = e.target.closest('button'); if (!b) return;
        if (b.hasAttribute('data-ss')) {
          // Görünüm ayarları: menü açık kalsın
          var kv = b.getAttribute('data-ss').split(':'), v = kv[1];
          if (kv[0] === 'size') { subSize = +v; put('subSize', subSize); }
          else if (kv[0] === 'bg') { subBg = v; put('subBg', subBg); }
          else if (kv[0] === 'pos') { subPos = v; put('subPos', subPos); }
          else if (kv[0] === 'delay') setDelay(+v === 0 ? 0 : delay + (+v));
          applySubStyle(); renderSubs();
          var k = menu.getAttribute('data-k'), sc = menu.scrollTop;
          closeMenu(); openMenu(k); if (menu) menu.scrollTop = sc;
          return;
        }
        if (b.hasAttribute('data-s')) selectSub(+b.getAttribute('data-s'), true);
        else if (b.hasAttribute('data-r')) setRate(+b.getAttribute('data-r'));
        else if (b.hasAttribute('data-au')) switchAudio(+b.getAttribute('data-au'));
        else if (b.hasAttribute('data-ap')) put('autoplay', !get('autoplay', true));
        closeMenu();
      });
      root.appendChild(menu);
      var first = menu.querySelector('button.on') || menu.querySelector('button');
      if (first) first.focus({ preventScroll: true });
      showUI(true);
    }

    function audioTracks() { return probe && probe.ok ? probe.tracks.filter(function (x) { return !x.v; }) : []; }
    function audioLabel(x, i) {
      var ch = x.ch ? (x.ch > 2 ? (x.ch - 1) + '.1' : x.ch === 2 ? t('stereo') : t('mono')) : '';
      var name = x.name || langName(x.lang) || t('track', i + 1);
      if (x.name && x.lang && x.name.toLowerCase().indexOf(langName(x.lang).toLowerCase()) < 0) name += ' (' + langName(x.lang) + ')';
      return name + (ch ? ' · ' + ch : '');
    }
    function curAudioLang() {
      if (!choice || choice.a < 0) return '';
      var a = audioTracks().filter(function (x) { return x.i === choice.a; })[0];
      return (a && a.lang) || '';
    }
    /** user=true: kullanıcı seçti; tercih o ses diline göre saklanır (ör. İngilizce ses → Türkçe altyazı, Türkçe dublaj → kapalı). */
    function selectSub(i, user) {
      subIdx = i; cues = [];
      if (user) {
        var pref = get('subFor', {});
        pref[curAudioLang() || '*'] = i < 0 ? 'off' : (subs[i].lang || subs[i].l);
        put('subFor', pref);
        put('subOn', i >= 0);
        if (i >= 0 && subs[i].lang) put('subLang', subs[i].lang);
      }
      renderSubs();
      if (i < 0) return;
      var waitT = setTimeout(function () { if (subIdx === i && !cues.length) flash(t('subs_loading'), 0); }, 700);
      fetch(subs[i].u).then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.text(); }).then(function (txt) {
        clearTimeout(waitT);
        if (subIdx === i) { cues = parseVtt(txt); renderSubs(); }
      }).catch(function () { clearTimeout(waitT); if (subIdx === i) flash(t('subs_failed'), 0); });
    }
    /** Açılışta altyazı: önce bu ses dili için saklanan tercih, yoksa genel tercih. */
    function initSubs() {
      if (!subs.length) return;
      var lang = curAudioLang();
      var pref = get('subFor', {});
      var want = pref[lang || '*'];
      if (want === undefined) {
        if (!get('subOn', true)) return;
        want = get('subLang', window.AKIS.subLang || '');
        // Dublaj tercih edilen altyazı diliyle aynıysa altyazıyı açma (yalnızca "zorunlu" varsa onu göster)
        if (lang && want && lang === want) {
          for (var f = 0; f < subs.length; f++) if (subs[f].forced && subs[f].lang === lang) { selectSub(f); return; }
          return;
        }
      }
      if (want === 'off') return;
      var i = -1;
      for (var k = 0; k < subs.length; k++) if ((subs[k].lang === want || subs[k].l === want) && !subs[k].forced) { i = k; break; }
      if (i < 0) for (var k2 = 0; k2 < subs.length; k2++) if (subs[k2].lang === want || subs[k2].l === want) { i = k2; break; }
      if (i < 0 && pref[lang || '*'] === undefined) i = 0;
      if (i >= 0) selectSub(i);
    }

    // ---------- dokunmatik ve fare hareketleri ----------
    var down = null, lastTap = { t: 0, side: 0, n: 0 }, tapTimer = 0, longTimer = 0, boosted = false, prevRate = 1;
    tap.addEventListener('pointerdown', function (e) {
      down = { x: e.clientX, y: e.clientY, t: Date.now(), type: e.pointerType, ct: video.currentTime, scrub: false, target: 0, vert: '', v0: video.volume, b0: bright };
      try { tap.setPointerCapture(e.pointerId); } catch (x) { }
      clearTimeout(longTimer);
      longTimer = setTimeout(function () {
        if (down && !down.scrub && !video.paused) { boosted = true; prevRate = video.playbackRate; video.playbackRate = 2; flash('2× ▶▶', 0); }
      }, 550);
    });
    tap.addEventListener('pointermove', function (e) {
      if (e.pointerType === 'mouse' && !down) { showUI(); return; }
      if (!down) return;
      var dx = e.clientX - down.x, dy = e.clientY - down.y;
      if (!down.scrub && !down.vert && down.type !== 'mouse' && Math.abs(dx) > 16 && Math.abs(dx) > Math.abs(dy) * 1.3) { down.scrub = true; clearTimeout(longTimer); }
      if (!down.scrub && !down.vert && down.type !== 'mouse' && Math.abs(dy) > 16 && Math.abs(dy) > Math.abs(dx) * 1.3) {
        var rr = tap.getBoundingClientRect();
        down.vert = (down.x - rr.left) < rr.width / 2 ? 'bright' : 'vol';
        clearTimeout(longTimer);
      }
      if (down.vert) {
        var hgt = tap.getBoundingClientRect().height;
        var delta = -dy / (hgt * 0.8);
        if (down.vert === 'bright') {
          bright = Math.round(clamp(down.b0 + delta * 1.3, 0.3, 1.6) * 100) / 100;
          applyLook(); put('bright', bright);
          flash(t('brightness', Math.round(bright * 100) + '%'), -1);
        } else {
          var nv = clamp(down.v0 + delta, 0, 1);
          video.volume = nv; video.muted = nv === 0; volIn.value = nv; put('vol', nv); put('muted', video.muted);
          flash(t('volume_val', Math.round(nv * 100) + '%'), 1);
        }
        return;
      }
      if (down.scrub) {
        var w = tap.getBoundingClientRect().width;
        var span = Math.min(Math.max(dura(), 60), 300);
        down.target = clamp(down.ct + dx / w * span, 0, dura());
        var diff = down.target - down.ct;
        flash(fmt(down.target) + '  (' + (diff >= 0 ? '+' : '−') + fmt(Math.abs(diff)) + ')', 0);
        showUI(true);
      }
    });
    function pointerEnd(e) {
      clearTimeout(longTimer);
      var d = down; down = null;
      if (!d) return;
      if (boosted) { boosted = false; video.playbackRate = prevRate; flash(prevRate + '×', 0); return; }
      if (d.scrub) { seek(d.target); showUI(); return; }
      if (d.vert) return;
      if (e.type === 'pointercancel' || Date.now() - d.t > 500) return;
      var r = tap.getBoundingClientRect();
      var rel = (d.x - r.left) / r.width;
      var side = rel < 0.33 ? -1 : rel > 0.67 ? 1 : 0;
      var now = Date.now();
      if (d.type === 'mouse') {
        // Tek tık: oynat/duraklat, çift tık: tam ekran
        if (now - lastTap.t < 280) { clearTimeout(tapTimer); lastTap.t = 0; toggleFs(); return; }
        lastTap.t = now;
        tapTimer = setTimeout(function () { toggle(); }, 280);
        return;
      }
      // Dokunmatik: tek dokunuş arayüzü gösterir/gizler, kenarlara çift dokunuş ±10 sn
      if (now - lastTap.t < 320 && side !== 0 && (lastTap.side === side || lastTap.n > 0)) {
        clearTimeout(tapTimer);
        lastTap.n = lastTap.side === side ? lastTap.n + 1 : 1;
        lastTap.t = now; lastTap.side = side;
        seek(video.currentTime + side * 10);
        flash((side > 0 ? '+' : '−') + (lastTap.n * 10) + ' s', side);
        return;
      }
      if (now - lastTap.t < 320 && side === 0) { clearTimeout(tapTimer); lastTap.t = 0; toggle(); return; }
      lastTap = { t: now, side: side, n: 0 };
      clearTimeout(tapTimer);
      tapTimer = setTimeout(function () {
        lastTap.n = 0;
        if (menu) { closeMenu(); return; }
        if (root.classList.contains('ui') && !video.paused) hideUI(); else showUI();
      }, 320);
    }
    tap.addEventListener('pointerup', pointerEnd);
    tap.addEventListener('pointercancel', pointerEnd);
    root.addEventListener('mousemove', function () { showUI(); });
    root.addEventListener('mouseleave', function () { hideUI(); });

    // ---------- klavye ----------
    function onKey(e) {
      if (!alive || e.ctrlKey || e.metaKey || e.altKey) return;
      var tag = (e.target && e.target.tagName) || '';
      if (tag === 'INPUT' && e.target.type !== 'range') return;
      if (tag === 'SELECT' || tag === 'TEXTAREA') return;
      var k = e.key;
      if (menu) {
        if (k === 'Escape' || k === 'Backspace') { closeMenu(); e.preventDefault(); return; }
        if (k === 'ArrowDown' || k === 'ArrowUp') {
          var bs = Array.prototype.slice.call(menu.querySelectorAll('button'));
          var i = bs.indexOf(document.activeElement);
          var n = bs[clamp(i + (k === 'ArrowDown' ? 1 : -1), 0, bs.length - 1)];
          if (n) n.focus();
          e.preventDefault();
        }
        return;
      }
      var handled = true;
      switch (k) {
        case ' ': case 'k': case 'K': case 'MediaPlayPause':
          if (tag === 'BUTTON' && k === ' ') { handled = false; break; }
          toggle(); break;
        case 'Enter':
          if (tag === 'BUTTON' || document.activeElement === seekEl) { handled = false; break; }
          toggle(); break;
        case 'ArrowLeft': case 'j': case 'J': skip(-10); break;
        case 'ArrowRight': case 'l': case 'L': skip(10); break;
        case 'ArrowUp': setVol(video.volume + 0.05); break;
        case 'ArrowDown': setVol(video.volume - 0.05); break;
        case 'm': case 'M': toggleMute(); flash(video.muted ? t('mute') : Math.round(video.volume * 100) + '%', 0); break;
        case 'f': case 'F': toggleFs(); break;
        case 'c': case 'C':
          if (subs.length) { var nx = subIdx + 1 >= subs.length ? -1 : subIdx + 1; selectSub(nx, true); flash(nx < 0 ? t('subs_off') : subs[nx].l, 0); }
          break;
        case 'n': case 'N': case 'MediaTrackNext': if (info.next) goNext(); break;
        case 'g': case 'G': if (subIdx >= 0) setDelay(delay - 0.1); break;
        case 'h': case 'H': if (subIdx >= 0) setDelay(delay + 0.1); break;
        case '>': setRate(Math.min(2, video.playbackRate + 0.25)); break;
        case '<': setRate(Math.max(0.25, video.playbackRate - 0.25)); break;
        case 'Escape': if (isFs()) handled = false; else opts.onBack(); break;
        case 'Backspace': case 'BrowserBack': opts.onBack(); break;
        default:
          if (k >= '0' && k <= '9') seek(dura() * (+k) / 10); else handled = false;
      }
      if (handled) { e.preventDefault(); showUI(); }
    }
    document.addEventListener('keydown', onKey);

    // ---------- olaylar ----------
    function setPlayIcons() {
      root.querySelectorAll('[data-a=toggle]').forEach(function (b) {
        b.innerHTML = video.paused ? I.play : I.pause;
        b.setAttribute('aria-label', video.paused ? t('play') : t('pause'));
      });
    }
    video.addEventListener('play', function () { setPlayIcons(); showUI(); });
    video.addEventListener('pause', function () { setPlayIcons(); showUI(true); report(true); });
    video.addEventListener('playing', function () { spinner.hidden = true; });
    video.addEventListener('waiting', function () { spinner.hidden = false; });
    video.addEventListener('canplay', function () { spinner.hidden = true; });
    video.addEventListener('seeking', function () { spinner.hidden = false; });
    video.addEventListener('seeked', function () { spinner.hidden = true; });
    video.addEventListener('timeupdate', function () { if (!video.paused) report(false); });
    video.addEventListener('volumechange', function () {
      var b = root.querySelector('[data-a=mute]'); if (b) b.innerHTML = video.muted || video.volume === 0 ? I.mute : I.vol;
    });
    video.addEventListener('ended', function () { report(true); showUI(true); if (info.next) showNext(); });
    video.addEventListener('error', function () { fail(video.error); });

    // ---------- sıradaki ----------
    function goNext() { if (info.next) opts.onNext(info.next); }
    function showNext() {
      if (nextCard) return;
      var auto = get('autoplay', true), left = 8;
      nextCard = document.createElement('div');
      nextCard.className = 'nextc';
      nextCard.innerHTML = '<div class="sub">' + esc(t('next_up')) + '</div><div class="title">' + esc(info.next.t) + '</div>' +
        '<div class="r"><button class="pill acc" data-n="go">' + esc(t('play_now')) + (auto ? ' · <span>' + left + '</span>' : '') + '</button>' +
        '<button class="pill" data-n="x">' + esc(t('cancel')) + '</button></div>';
      root.appendChild(nextCard);
      var timer = 0;
      if (auto) {
        timer = setInterval(function () {
          left--;
          var s = nextCard && nextCard.querySelector('span'); if (s) s.textContent = left;
          if (left <= 0) { clearInterval(timer); goNext(); }
        }, 1000);
      }
      nextCard.addEventListener('click', function (e) {
        e.stopPropagation();
        var b = e.target.closest('[data-n]'); if (!b) return;
        clearInterval(timer);
        if (b.getAttribute('data-n') === 'go') goNext(); else { nextCard.remove(); nextCard = null; }
      });
      var go = nextCard.querySelector('[data-n=go]'); if (go) go.focus({ preventScroll: true });
    }

    // ---------- kaldığın yer çipi ----------
    function showResumeChip(at) {
      chip = document.createElement('div');
      chip.className = 'chip2';
      chip.innerHTML = '<span>' + esc(t('resumed', fmt(at))) + '</span><button>' + esc(t('restart')) + '</button>';
      chip.querySelector('button').addEventListener('click', function (e) { e.stopPropagation(); seek(0); chip.remove(); chip = null; });
      root.appendChild(chip);
      setTimeout(function () { if (chip) { chip.remove(); chip = null; } }, 7000);
    }

    // ---------- hata ve yedek yollar ----------
    function message(title, text, withRetryDirect) {
      var m = document.createElement('div');
      m.className = 'msg';
      var file = '/m/' + enc(id) + '/file.' + (item.ext || 'bin');
      m.innerHTML = '<b style="font-size:17px">' + esc(title) + '</b><p>' + esc(text) + '</p><div class="actions">' +
        '<a class="pill acc" href="vlc://' + esc(location.origin + file) + '">' + esc(t('open_vlc')) + '</a>' +
        '<a class="pill" href="' + esc(file) + '" download>' + esc(t('download')) + '</a>' +
        (withRetryDirect ? '<button class="pill" data-m="direct">' + esc(t('try_anyway')) + '</button>' : '') +
        '<button class="pill" data-m="back">' + esc(t('back')) + '</button></div>';
      m.addEventListener('click', function (e) {
        e.stopPropagation();
        var b = e.target.closest('[data-m]'); if (!b) return;
        if (b.getAttribute('data-m') === 'back') opts.onBack();
        else { m.remove(); startDirect(video.currentTime || startAt); }
      });
      root.appendChild(m);
      spinner.hidden = true;
    }
    function fail(err) {
      if (!alive) return;
      var at = video.currentTime || startAt;
      // Telefona ulaşılamıyorsa biçim denemeleri boşuna: doğrudan bağlantı hatası göster
      if (err && /HTTP|fetch|NetworkError|network/i.test(String(err.message || err))) {
        message(t('error'), t('error_load'), false);
        return;
      }
      // 1) ses yüzünden olabilir: telefonda çevirmeyi dene, olmazsa sessiz dene
      if (engine instanceof Engine && choice && choice.a >= 0) {
        var au = probe.tracks.filter(function (x) { return x.i === choice.a; })[0];
        if (!choice.tx && au && au.tx && !tried.tx) { tried.tx = true; restart({ a: choice.a, tx: true, acodec: 'mp4a.40.2' }, at); toast(t('audio_converting')); return; }
        if (!tried.mute) { tried.mute = true; restart({ a: -1, tx: false, acodec: null }, at); toast(t('no_audio')); return; }
      }
      // 2) doğrudan dosyayı dene
      if (engine instanceof Engine && !tried.direct && video.canPlayType(item.mime || '')) { startDirect(at); return; }
      message(t('unsupported_title'), t('unsupported_text'), false);
      if (window.console) console.warn('Akış oynatıcı:', err);
    }
    function restart(change, at) {
      if (engine) engine.destroy();
      choice = { vcodec: choice.vcodec, a: change.a, tx: change.tx, acodec: change.acodec };
      engine = new Engine(video, id, dura(), choice, fail);
      engine.open(at);
      play();
    }
    function startDirect(at) {
      tried.direct = true;
      if (engine) engine.destroy();
      engine = new Direct(video, '/m/' + enc(id) + '/file.' + (item.ext || 'bin'));
      engine.open(at);
      play();
    }
    function switchAudio(trackIndex) {
      var au = probe.tracks.filter(function (x) { return x.i === trackIndex; })[0];
      if (!au) return;
      if (au.lang) put('audioLang', au.lang);
      setTimeout(function () { if (get('subFor', {})[au.lang || '*'] !== undefined) { selectSub(-1); initSubs(); } }, 0);
      var ok = au.codec && MS && MS.isTypeSupported(Engine.mime(choice.vcodec, au.codec));
      restart(ok ? { a: au.i, tx: false, acodec: au.codec } : au.tx ? { a: au.i, tx: true, acodec: 'mp4a.40.2' } : { a: -1, tx: false, acodec: null }, video.currentTime);
    }
    function toast(s) { if (window.AkisToast) window.AkisToast(s); }

    // ---------- başlat ----------
    var startAt = 0;
    if (info.resume && info.resume > 5000 && (!item.d || info.resume < item.d - 15000)) startAt = info.resume / 1000;

    fetch('/api/probe?id=' + enc(id)).then(function (r) { return r.json(); }).then(function (pr) {
      if (!alive) return;
      probe = pr;
      if (pr.d > 0) dur = pr.d / 1000;
      var vt = pr.ok ? pr.tracks.filter(function (x) { return x.v && x.codec; })[0] : null;
      var anyVideo = pr.ok && pr.tracks.some(function (x) { return x.v; });
      var canMse = !!MS && vt && MS.isTypeSupported(Engine.mime(vt.codec, null));
      if (!canMse) {
        // Kendi motorumuz bu görüntü biçimini tarayıcıya veremiyor
        if (video.canPlayType(item.mime || '') || !anyVideo) { startDirect(startAt); }
        else { message(t('unsupported_title'), t('unsupported_text'), true); return; }
      } else {
        var auds = pr.tracks.filter(function (x) { return !x.v; });
        var want = get('audioLang', '');
        var au = auds.filter(function (x) { return want && x.lang === want; })[0] ||
          auds.filter(function (x) { return x.def; })[0] || auds[0];
        var c = { vcodec: vt.codec, a: -1, tx: false, acodec: null };
        if (au) {
          if (au.codec && MS.isTypeSupported(Engine.mime(vt.codec, au.codec))) { c.a = au.i; c.acodec = au.codec; }
          else if (au.tx) { c.a = au.i; c.tx = true; c.acodec = 'mp4a.40.2'; toast(t('audio_converting')); }
          else toast(t('no_audio'));
        }
        choice = c;
        engine = new Engine(video, id, dur, choice, fail);
        engine.open(startAt);
      }
      initSubs();
      if (startAt > 0) showResumeChip(startAt);
      play();
      root.focus({ preventScroll: true });
    }).catch(function (e) { fail(e); });

    if ('mediaSession' in navigator) {
      try {
        navigator.mediaSession.metadata = new MediaMetadata({ title: item.t, artwork: [{ src: '/t/' + enc(id) + '.jpg' }] });
        navigator.mediaSession.setActionHandler('play', play);
        navigator.mediaSession.setActionHandler('pause', function () { video.pause(); });
        navigator.mediaSession.setActionHandler('seekbackward', function () { skip(-10); });
        navigator.mediaSession.setActionHandler('seekforward', function () { skip(10); });
        navigator.mediaSession.setActionHandler('seekto', function (d) { seek(d.seekTime); });
        navigator.mediaSession.setActionHandler('nexttrack', info.next ? goNext : null);
      } catch (e) { }
    }

    return {
      destroy: function () {
        if (!alive) return;
        report(true);
        alive = false;
        cancelAnimationFrame(raf);
        clearTimeout(uiTimer); clearTimeout(tapTimer); clearTimeout(longTimer);
        document.removeEventListener('keydown', onKey);
        document.removeEventListener('fullscreenchange', onFsChange);
        document.removeEventListener('webkitfullscreenchange', onFsChange);
        if (isFs()) { try { (document.exitFullscreen || document.webkitExitFullscreen).call(document); } catch (e) { } }
        if (engine) engine.destroy();
        root.remove();
      }
    };
  }

  window.AkisPlayer = { mount: mount, fmt: fmt, Engine: Engine, MS: MS };
})();
