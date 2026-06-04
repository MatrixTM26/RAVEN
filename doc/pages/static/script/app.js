const navbar = document.getElementById("navbar");
const burgerBtn = document.getElementById("burgerBtn");
const burgerIcon = document.getElementById("burgerIcon");
const mobileMenu = document.getElementById("mobileMenu");
const scrollTopBtn = document.getElementById("scrollTop");
const navLinks = document.querySelectorAll(".nav-link");
const mobileNavLinks = document.querySelectorAll(".mobile-nav-link");
const cmdTabs = document.querySelectorAll(".cmd-tab");
const cmdPanels = document.querySelectorAll(".cmd-panel");
const copyBtns = document.querySelectorAll(".copy-btn");

let menuOpen = false;

function toggleMenu() {
  menuOpen = !menuOpen;
  mobileMenu.classList.toggle("open", menuOpen);
  burgerIcon.className = menuOpen ? "fas fa-xmark" : "fas fa-bars";
  document.body.style.overflow = menuOpen ? "hidden" : "";
}

function closeMenu() {
  if (!menuOpen) return;
  menuOpen = false;
  mobileMenu.classList.remove("open");
  burgerIcon.className = "fas fa-bars";
  document.body.style.overflow = "";
}

burgerBtn.addEventListener("click", toggleMenu);

mobileNavLinks.forEach(function(link) {
  link.addEventListener("click", closeMenu);
});

document.addEventListener("click", function(e) {
  if (!mobileMenu.contains(e.target) && !burgerBtn.contains(e.target)) {
    closeMenu();
  }
});

window.addEventListener("scroll", function() {
  const scrolled = window.scrollY > 40;
  navbar.classList.toggle("scrolled", scrolled);
  scrollTopBtn.classList.toggle("visible", window.scrollY > 300);
  updateActiveSection();
});

scrollTopBtn.addEventListener("click", function() {
  window.scrollTo({ top: 0, behavior: "smooth" });
});

function updateActiveSection() {
  const sections = ["home", "overview", "features", "installation", "commands", "architecture", "config"];
  let currentSection = "home";

  sections.forEach(function(id) {
    const el = document.getElementById(id);
    if (!el) return;
    const rect = el.getBoundingClientRect();
    if (rect.top <= 100) {
      currentSection = id;
    }
  });

  navLinks.forEach(function(link) {
    const section = link.getAttribute("data-section");
    link.classList.toggle("active", section === currentSection);
  });
}

function activateTab(targetTab) {
  cmdTabs.forEach(function(tab) {
    tab.classList.toggle("active", tab.getAttribute("data-tab") === targetTab);
  });
  cmdPanels.forEach(function(panel) {
    const panelId = "tab-" + targetTab;
    panel.classList.toggle("active", panel.id === panelId);
  });
}

cmdTabs.forEach(function(tab) {
  tab.addEventListener("click", function() {
    activateTab(tab.getAttribute("data-tab"));
  });
});

function copyToClipboard(text, btn) {
  navigator.clipboard.writeText(text).then(function() {
    btn.classList.add("copied");
    btn.innerHTML = '<i class="fas fa-check"></i>';
    setTimeout(function() {
      btn.classList.remove("copied");
      btn.innerHTML = '<i class="fas fa-copy"></i>';
    }, 1800);
  }).catch(function() {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand("copy");
    document.body.removeChild(textarea);
    btn.classList.add("copied");
    btn.innerHTML = '<i class="fas fa-check"></i>';
    setTimeout(function() {
      btn.classList.remove("copied");
      btn.innerHTML = '<i class="fas fa-copy"></i>';
    }, 1800);
  });
}

copyBtns.forEach(function(btn) {
  btn.addEventListener("click", function() {
    const text = btn.getAttribute("data-copy");
    if (text) copyToClipboard(text, btn);
  });
});

function initIntersectionObserver() {
  const cards = document.querySelectorAll(".feature-card, .info-card, .install-step, .arch-item");
  const observer = new IntersectionObserver(
    function(entries) {
      entries.forEach(function(entry) {
        if (entry.isIntersecting) {
          entry.target.style.opacity = "1";
          entry.target.style.transform = "translateY(0)";
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.08, rootMargin: "0px 0px -40px 0px" }
  );

  cards.forEach(function(card, index) {
    card.style.opacity = "0";
    card.style.transform = "translateY(20px)";
    card.style.transition = "opacity 0.45s ease " + (index % 4) * 0.07 + "s, transform 0.45s ease " + (index % 4) * 0.07 + "s";
    observer.observe(card);
  });
}

function initSmoothLinks() {
  document.querySelectorAll('a[href^="#"]').forEach(function(link) {
    link.addEventListener("click", function(e) {
      const targetId = link.getAttribute("href").slice(1);
      const target = document.getElementById(targetId);
      if (target) {
        e.preventDefault();
        const offset = target.getBoundingClientRect().top + window.scrollY - 80;
        window.scrollTo({ top: offset, behavior: "smooth" });
        closeMenu();
      }
    });
  });
}

document.addEventListener("DOMContentLoaded", function() {
  initIntersectionObserver();
  initSmoothLinks();
  updateActiveSection();
  activateTab("general");
});
