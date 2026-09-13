"use strict";

let currentTheme = localStorage.getItem("raven-theme") || "dark";

function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    currentTheme = theme;
    localStorage.setItem("raven-theme", theme);
    let icon = theme === "dark" ? "fa-moon" : "fa-sun";
    let el = document.getElementById("sidebar-theme-icon");
    if (el) el.className = "fas " + icon;
}

function toggleTheme() {
    applyTheme(currentTheme === "dark" ? "light" : "dark");
}

applyTheme(currentTheme);

document.addEventListener("DOMContentLoaded", function () {
    let btn = document.getElementById("sidebar-theme-btn");
    if (btn) btn.addEventListener("click", toggleTheme);
});
