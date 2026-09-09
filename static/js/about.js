(() => {
  const aboutBtn = document.getElementById("about-btn");
  const aboutModal = document.getElementById("about-modal");
  const aboutCloseBtn = document.getElementById("about-close-btn");
  const aboutVersion = document.getElementById("about-version");
  if (!aboutBtn || !aboutModal) return;

  let versionLoaded = false;

  async function openAboutModal() {
    aboutModal.classList.remove("hidden");
    if (versionLoaded) return;
    try {
      const res = await fetch("/api/version");
      const data = await res.json();
      aboutVersion.textContent = `Version ${data.version}`;
      versionLoaded = true;
    } catch (e) {
      aboutVersion.textContent = "";
    }
  }

  function closeAboutModal() {
    aboutModal.classList.add("hidden");
  }

  aboutBtn.addEventListener("click", openAboutModal);
  aboutCloseBtn.addEventListener("click", closeAboutModal);
  aboutModal.addEventListener("click", (e) => {
    if (e.target === aboutModal) closeAboutModal();
  });
})();
