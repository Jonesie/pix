(() => {
  const params = new URLSearchParams(window.location.search);
  const initialId = params.get("id");
  if (!initialId) {
    window.location.replace("/");
    return;
  }

  const imageEl = document.getElementById("image-full");
  const videoEl = document.getElementById("video-full");
  const captionEl = document.getElementById("image-caption");
  const descEl = document.getElementById("image-description");
  const dateEl = document.getElementById("image-date");
  const tagsEl = document.getElementById("image-tags");
  const prevBtn = document.getElementById("prev-btn");
  const nextBtn = document.getElementById("next-btn");

  function escapeHtml(s) {
    return (s || "").replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    })[c]);
  }

  function formatDate(iso) {
    if (!iso) return "";
    const d = new Date(iso + "T00:00:00");
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString("en-NZ", { day: "numeric", month: "short", year: "numeric" });
  }

  async function preloadImage(imageId) {
    try {
      const res = await fetch(`/api/images/${imageId}`);
      if (!res.ok) return;
      const img = await res.json();
      if (img.media_type === "video") return; // don't pre-fetch a whole video
      const im = new Image();
      im.src = `/images/full/${img.filename_full}`;
    } catch (_) {}
  }

  async function loadImage(imageId, pushHistory) {
    const res = await fetch(`/api/images/${imageId}`);
    if (!res.ok) {
      if (res.status === 404) {
        window.location.replace("/");
        return;
      }
      return;
    }
    const img = await res.json();

    // Stop any playback before switching media.
    videoEl.pause();
    videoEl.removeAttribute("src");
    videoEl.load();

    if (img.media_type === "video") {
      videoEl.src = `/images/full/${img.filename_full}`;
      videoEl.classList.remove("hidden");
      imageEl.classList.add("hidden");
    } else {
      imageEl.src = `/images/full/${img.filename_full}`;
      imageEl.alt = img.caption || "";
      imageEl.classList.remove("hidden");
      videoEl.classList.add("hidden");
    }

    captionEl.textContent = img.caption || "Untitled";
    captionEl.style.display = img.caption ? "block" : "none";

    descEl.textContent = img.description || "";
    descEl.style.display = img.description ? "block" : "none";

    const dateStr = formatDate(img.created_date);
    dateEl.textContent = dateStr;
    dateEl.style.display = dateStr ? "block" : "none";

    tagsEl.innerHTML = (img.tags || [])
      .map((t) => `<span class="tag-pill">${escapeHtml(t)}</span>`)
      .join("");
    tagsEl.style.display = (img.tags || []).length ? "flex" : "none";

    prevBtn.classList.toggle("hidden", !img.prev_id);
    prevBtn.dataset.targetId = img.prev_id || "";
    nextBtn.classList.toggle("hidden", !img.next_id);
    nextBtn.dataset.targetId = img.next_id || "";

    document.title = `${img.caption || "Image"} · _pix_`;

    const url = new URL(window.location);
    url.searchParams.set("id", imageId);
    if (pushHistory) {
      window.history.pushState({ id: imageId }, "", url);
    } else {
      window.history.replaceState({ id: imageId }, "", url);
    }

    if (nextBtn.dataset.targetId) preloadImage(nextBtn.dataset.targetId);
  }

  prevBtn.addEventListener("click", () => {
    if (prevBtn.dataset.targetId) loadImage(prevBtn.dataset.targetId, true);
  });
  nextBtn.addEventListener("click", () => {
    if (nextBtn.dataset.targetId) loadImage(nextBtn.dataset.targetId, true);
  });

  document.addEventListener("keydown", (e) => {
    if (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA") return;
    if (e.key === "Escape") window.location.href = "/";
    else if (e.key === "ArrowLeft" && prevBtn.dataset.targetId) loadImage(prevBtn.dataset.targetId, true);
    else if (e.key === "ArrowRight" && nextBtn.dataset.targetId) loadImage(nextBtn.dataset.targetId, true);
  });

  window.addEventListener("popstate", (e) => {
    const id = e.state && e.state.id;
    if (id) loadImage(id, false);
  });

  loadImage(initialId, false);
})();
