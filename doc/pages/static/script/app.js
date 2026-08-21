(function () {
  var burgerBtn = document.getElementById('burgerBtn');
  var burgerIcon = document.getElementById('burgerIcon');
  var mobileDrawer = document.getElementById('mobileDrawer');
  var drawerOverlay = document.getElementById('drawerOverlay');
  var drawerCollapse = document.getElementById('drawerCollapse');
  var isOpen = false;

  function openDrawer() {
    isOpen = true;
    mobileDrawer.classList.add('open');
    drawerOverlay.classList.add('open');
    if (burgerIcon) { burgerIcon.classList.remove('fa-bars'); burgerIcon.classList.add('fa-xmark'); }
    document.body.style.overflow = 'hidden';
  }

  function closeDrawer() {
    isOpen = false;
    mobileDrawer.classList.remove('open');
    drawerOverlay.classList.remove('open');
    if (burgerIcon) { burgerIcon.classList.remove('fa-xmark'); burgerIcon.classList.add('fa-bars'); }
    document.body.style.overflow = '';
  }

  function toggleDrawer() {
    if (isOpen) closeDrawer();
    else openDrawer();
  }

  if (burgerBtn) burgerBtn.addEventListener('click', toggleDrawer);
  if (drawerCollapse) drawerCollapse.addEventListener('click', closeDrawer);
  if (drawerOverlay) drawerOverlay.addEventListener('click', closeDrawer);

  document.querySelectorAll('.drawer-item, .drawer-gh').forEach(function (item) {
    item.addEventListener('click', closeDrawer);
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && isOpen) closeDrawer();
  });

  var track = document.getElementById('screenshotTrack');
  var dots = document.querySelectorAll('.screenshot-dot');
  var titleEl = document.getElementById('screenshotTitle');
  var titles = [
    'RAVEN-C2 — web panel',
    'RAVEN-C2 — sessions',
    'RAVEN-C2 — terminal',
    'RAVEN-C2 — cli mode',
    'RAVEN-C2 — javafx gui',
    'RAVEN-C2 — dashboard'
  ];
  var currentSlide = 0;
  var totalSlides = 6;
  var autoTimer;

  function goToSlide(n) {
    currentSlide = (n + totalSlides) % totalSlides;
    if (track) track.style.transform = 'translateX(-' + (currentSlide * 100) + '%)';
    dots.forEach(function (d, i) {
      d.classList.toggle('active', i === currentSlide);
    });
    if (titleEl) titleEl.textContent = titles[currentSlide];
  }

  function startAuto() {
    autoTimer = setInterval(function () { goToSlide(currentSlide + 1); }, 4000);
  }

  function resetAuto() {
    clearInterval(autoTimer);
    startAuto();
  }

  var ssPrev = document.getElementById('ssPrev');
  var ssNext = document.getElementById('ssNext');

  if (ssPrev) ssPrev.addEventListener('click', function () { goToSlide(currentSlide - 1); resetAuto(); });
  if (ssNext) ssNext.addEventListener('click', function () { goToSlide(currentSlide + 1); resetAuto(); });

  dots.forEach(function (d) {
    d.addEventListener('click', function () {
      goToSlide(parseInt(d.dataset.idx));
      resetAuto();
    });
  });

  if (track) startAuto();

  document.querySelectorAll('.tab').forEach(function (tab) {
    tab.addEventListener('click', function () {
      document.querySelectorAll('.tab').forEach(function (t) { t.classList.remove('active'); });
      document.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
      tab.classList.add('active');
      var panel = document.getElementById('tab-' + tab.dataset.tab);
      if (panel) panel.classList.add('active');
    });
  });

  document.querySelectorAll('.cb-copy').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var text = btn.dataset.copy.replace(/&#10;/g, '\n');
      navigator.clipboard.writeText(text).then(function () {
        btn.innerHTML = '<i class="fas fa-check"></i>';
        setTimeout(function () { btn.innerHTML = '<i class="fas fa-copy"></i>'; }, 1500);
      });
    });
  });

  var toTop = document.getElementById('toTop');

  window.addEventListener('scroll', function () {
    if (toTop) toTop.classList.toggle('visible', window.scrollY > 400);
  });

  if (toTop) toTop.addEventListener('click', function () {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });

  var navItems = document.querySelectorAll('.nav-item[data-section]');
  var drawerItems = document.querySelectorAll('.drawer-item[data-section]');
  var sections = document.querySelectorAll('section[id]');

  window.addEventListener('scroll', function () {
    var current = '';
    sections.forEach(function (sec) {
      if (window.scrollY >= sec.offsetTop - 80) current = sec.id;
    });
    navItems.forEach(function (item) {
      item.classList.toggle('active', item.dataset.section === current);
    });
    drawerItems.forEach(function (item) {
      item.classList.toggle('active', item.dataset.section === current);
    });
  });

  var revealEls = document.querySelectorAll('.reveal');
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add('in');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.08 });

  revealEls.forEach(function (el) { observer.observe(el); });
})();

;(function () {
  var dsItems = document.querySelectorAll('.dsb-item[data-section]');
  var sections = document.querySelectorAll('section[id]');

  window.addEventListener('scroll', function () {
    var current = '';
    sections.forEach(function (sec) {
      if (window.scrollY >= sec.offsetTop - 80) current = sec.id;
    });
    dsItems.forEach(function (item) {
      item.classList.toggle('active', item.dataset.section === current);
    });
  });
})();
