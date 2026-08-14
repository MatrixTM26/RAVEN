"use strict";

let sidebarOpen = false;
let sidebarCollapsed =
    localStorage.getItem("raven-sidebar-collapsed") === "true";

let tooltipEl = null;
let tooltipTimer = null;

function isMobile() {
    return window.innerWidth <= 768;
}

function openSidebar() {
    sidebarOpen = true;
    let sb = document.getElementById("sidebar");
    let ov = document.getElementById("mobile-overlay");
    if (sb) {
        sb.classList.remove("collapsed");
        sb.classList.add("open");
    }
    if (ov) ov.classList.add("active");
}

function closeSidebar() {
    sidebarOpen = false;
    let sb = document.getElementById("sidebar");
    let ov = document.getElementById("mobile-overlay");
    if (sb) sb.classList.remove("open");
    if (ov) ov.classList.remove("active");
}

function toggleSidebar() {
    sidebarOpen ? closeSidebar() : openSidebar();
}

function collapseDesktopSidebar() {
    if (isMobile()) return;
    sidebarCollapsed = !sidebarCollapsed;
    document
        .getElementById("sidebar")
        .classList.toggle("collapsed", sidebarCollapsed);
    localStorage.setItem("raven-sidebar-collapsed", sidebarCollapsed);
    hideTooltip();
}

function showTooltip(label, targetEl) {
    if (!sidebarCollapsed || isMobile()) return;
    if (!tooltipEl) {
        tooltipEl = document.createElement("div");
        tooltipEl.className = "sidebar-tooltip";
        document.body.appendChild(tooltipEl);
    }
    tooltipEl.textContent = label;
    let rect = targetEl.getBoundingClientRect();
    let sbW =
        parseInt(
            getComputedStyle(document.documentElement).getPropertyValue(
                "--sidebar-w-collapsed"
            )
        ) || 52;
    tooltipEl.style.top = rect.top + rect.height / 2 - 13 + "px";
    tooltipEl.style.left = sbW + 10 + "px";
    tooltipEl.classList.add("visible");
}

function hideTooltip() {
    if (tooltipEl) tooltipEl.classList.remove("visible");
}

function applyCollapsedState() {
    if (isMobile()) return;
    if (sidebarCollapsed) {
        document.getElementById("sidebar").classList.add("collapsed");
    }
}

document.addEventListener("DOMContentLoaded", function () {
    applyCollapsedState();

    let collapseBtn = document.getElementById("sidebar-collapse-btn");
    if (collapseBtn)
        collapseBtn.addEventListener("click", collapseDesktopSidebar);

    let logoSvg = document.querySelector(".logo-svg");
    if (logoSvg) {
        logoSvg.addEventListener("click", function () {
            if (sidebarCollapsed && !isMobile()) collapseDesktopSidebar();
        });
    }

    document.querySelectorAll("[data-nav]").forEach(function (item) {
        item.addEventListener("mouseenter", function () {
            let label = item.dataset.label;
            if (label) showTooltip(label, item);
        });
        item.addEventListener("mouseleave", hideTooltip);
        item.addEventListener("click", hideTooltip);
    });

    let hamburger = document.getElementById("hamburger");
    if (hamburger) hamburger.addEventListener("click", toggleSidebar);

    let overlay = document.getElementById("mobile-overlay");
    if (overlay) overlay.addEventListener("click", closeSidebar);

    window.addEventListener("resize", function () {
        if (!isMobile()) {
            closeSidebar();
            if (sidebarCollapsed) {
                document.getElementById("sidebar").classList.add("collapsed");
            } else {
                document
                    .getElementById("sidebar")
                    .classList.remove("collapsed");
            }
        } else {
            document.getElementById("sidebar").classList.remove("collapsed");
        }
    });
});
