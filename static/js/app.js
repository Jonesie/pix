(() => {
  const gallery = document.getElementById("gallery");
  const loading = document.getElementById("loading");
  const empty = document.getElementById("empty");
  const searchInput = document.getElementById("search");
  const tagsRow = document.getElementById("tags-row");
  const lightbox = document.getElementById("lightbox");
  const lightboxInner = document.getElementById("lightbox-inner");
  const closeLightbox = document.getElementById("close-lightbox");
  const prevLightboxBtn = document.getElementById("prev-lightbox");
  const nextLightboxBtn = document.getElementById("next-lightbox");
  const logoutBtn = document.getElementById("logout-btn");
  const editModal = document.getElementById("edit-modal");
  const editForm = document.getElementById("edit-form");
  const editCloseBtn = document.getElementById("edit-close-btn");
  const editSaveBtn = document.getElementById("edit-save-btn");
  const editError = document.getElementById("edit-error");
  const editCaption = document.getElementById("edit-caption");
  const editDescription = document.getElementById("edit-description");
  const editTags = document.getElementById("edit-tags");
  const editCreatedDate = document.getElementById("edit-created_date");
  const editSequence = document.getElementById("edit-sequence");

  let offset = 0;
  let hasMore = true;
  let loadingNow = false;
  let query = "";
  let activeTag = "";
  let searchDebounce = null;
  let editingId = null;

  function escapeHtml(s) {
    return (s || "").replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    })[c]);
  }

  /**
   * Minimal markdown -> HTML renderer (same block/inline logic used by the
   * lorna reader): headings, horizontal rules, blockquotes, standalone
   * ![alt](url) images, - bullet lists, and paragraphs. Inline: **bold**,
   * *italic*, [text](url) links. HTML-escapes everything first.
   */
  function mdToHtml(md) {
    if (!md) return "";
    const text = md.replace(/<!--[\s\S]*?-->/g, "").trim();
    const blocks = text.split(/\n{2,}/).map((b) => b.trim()).filter(Boolean);
    const out = [];

    for (const block of blocks) {
      if (block.startsWith("### ")) { out.push(`<h3>${inlineMd(block.slice(4).trim())}</h3>`); continue; }
      if (block.startsWith("## ")) { out.push(`<h2>${inlineMd(block.slice(3).trim())}</h2>`); continue; }
      if (block.startsWith("# ")) { out.push(`<h1>${inlineMd(block.slice(2).trim())}</h1>`); continue; }

      if (/^(-{3,}|\*{3,}|_{3,})$/.test(block)) { out.push("<hr>"); continue; }

      const imgMatch = block.match(/^!\[([^\]]*)\]\(([^)]+)\)$/);
      if (imgMatch) {
        const [, alt, src] = imgMatch;
        out.push(
          `<figure><img src="${escapeHtml(src)}" alt="${escapeHtml(alt)}" loading="lazy" />` +
          (alt ? `<figcaption>${escapeHtml(alt)}</figcaption>` : "") +
          `</figure>`
        );
        continue;
      }

      const lines = block.split("\n");
      if (lines.every((l) => l.startsWith("> "))) {
        const inner = lines.map((l) => l.slice(2).trim()).join(" ");
        out.push(`<blockquote><p>${inlineMd(inner)}</p></blockquote>`);
        continue;
      }

      if (lines.every((l) => /^[-*]\s+/.test(l))) {
        const items = lines.map((l) => `<li>${inlineMd(l.replace(/^[-*]\s+/, ""))}</li>`).join("");
        out.push(`<ul>${items}</ul>`);
        continue;
      }

      out.push(`<p>${inlineMd(lines.map((l) => l.trim()).join(" "))}</p>`);
    }

    return out.join("\n");
  }

  function inlineMd(s) {
    let html = escapeHtml(s);
    html = html.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    html = html.replace(/\*([^*]+)\*/g, "<em>$1</em>");
    html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_m, label, url) => (
      `<a href="${url}" target="_blank" rel="noopener">${label}</a>`
    ));
    return html;
  }

  async function loadTags() {
    const res = await fetch("/api/tags");
    const data = await res.json();
    tagsRow.innerHTML = "";
    const allChip = makeChip("All", "");
    allChip.classList.add("active");
    tagsRow.appendChild(allChip);
    for (const t of data.items) {
      tagsRow.appendChild(makeChip(`${t.name} (${t.count})`, t.name));
    }
  }

  function makeChip(label, tag) {
    const el = document.createElement("button");
    el.className = "tag-chip";
    el.textContent = label;
    el.dataset.tag = tag;
    el.addEventListener("click", () => {
      activeTag = tag;
      [...tagsRow.children].forEach((c) => c.classList.remove("active"));
      el.classList.add("active");
      resetAndLoad();
    });
    return el;
  }

  function cardHtml(img) {
    const cap = escapeHtml(img.caption || "");
    return `<div class="card" data-id="${img.id}">
      <img src="/images/thumb/${img.filename_thumb}" loading="lazy" alt="${cap}" />
      ${cap ? `<div class="caption">${cap}</div>` : ""}
      <div class="card-admin">
        <button class="card-admin-btn edit-card-btn" type="button">Edit</button>
        <button class="card-admin-btn delete-card-btn" type="button">Delete</button>
      </div>
    </div>`;
  }

  async function loadMore() {
    if (loadingNow || !hasMore) return;
    loadingNow = true;
    loading.style.display = "block";
    const params = new URLSearchParams({ offset: String(offset), limit: "30" });
    if (query) params.set("q", query);
    if (activeTag) params.set("tag", activeTag);
    const res = await fetch(`/api/images?${params}`);
    const data = await res.json();
    for (const img of data.items) {
      gallery.insertAdjacentHTML("beforeend", cardHtml(img));
    }
    offset = data.next_offset;
    hasMore = data.has_more;
    loadingNow = false;
    loading.style.display = "none";
    empty.style.display = offset === 0 && !data.items.length ? "block" : "none";
  }

  function resetAndLoad() {
    gallery.innerHTML = "";
    offset = 0;
    hasMore = true;
    loadMore();
  }

  gallery.addEventListener("click", async (e) => {
    const editBtn = e.target.closest(".edit-card-btn");
    if (editBtn) {
      const card = editBtn.closest(".card");
      openEditModal(card.dataset.id);
      return;
    }

    const deleteBtn = e.target.closest(".delete-card-btn");
    if (deleteBtn) {
      const card = deleteBtn.closest(".card");
      if (!confirm("Delete this image permanently?")) return;
      const res = await fetch(`/api/admin/images/${card.dataset.id}`, { method: "DELETE" });
      if (res.ok) card.remove();
      return;
    }

    const card = e.target.closest(".card");
    if (!card) return;
    openLightbox(card.dataset.id);
  });

  async function openLightbox(id) {
    const res = await fetch(`/api/images/${id}`);
    if (!res.ok) return;
    const img = await res.json();

    const hasContent = !!(img.content_md && img.content_md.trim());
    lightboxInner.innerHTML = `
      <img id="lightbox-img" src="/images/full/${img.filename_full}" alt="${escapeHtml(img.caption)}" />
      <div class="lightbox-meta">
        <h2>${escapeHtml(img.caption) || "Untitled"}</h2>
        ${img.description ? `<p>${escapeHtml(img.description)}</p>` : ""}
        ${hasContent ? `<div class="lightbox-content">${mdToHtml(img.content_md)}</div>` : ""}
      </div>`;

    // Alternate which side the image sits on, by page parity, so
    // consecutive pages don't all read image-left/text-right.
    const isEvenPage = typeof img.sequence === "number" && img.sequence % 2 === 0;
    lightboxInner.classList.toggle("reverse", isEvenPage);

    prevLightboxBtn.classList.toggle("hidden", !img.prev_id);
    prevLightboxBtn.dataset.targetId = img.prev_id || "";
    nextLightboxBtn.classList.toggle("hidden", !img.next_id);
    nextLightboxBtn.dataset.targetId = img.next_id || "";

    lightbox.classList.remove("hidden");
  }

  function closeLightboxBox() {
    lightbox.classList.add("hidden");
  }

  closeLightbox.addEventListener("click", closeLightboxBox);
  lightbox.addEventListener("click", (e) => {
    if (e.target === lightbox) closeLightboxBox();
  });
  prevLightboxBtn.addEventListener("click", () => {
    if (prevLightboxBtn.dataset.targetId) openLightbox(prevLightboxBtn.dataset.targetId);
  });
  nextLightboxBtn.addEventListener("click", () => {
    if (nextLightboxBtn.dataset.targetId) openLightbox(nextLightboxBtn.dataset.targetId);
  });
  document.addEventListener("keydown", (e) => {
    if (lightbox.classList.contains("hidden")) return;
    if (e.key === "Escape") closeLightboxBox();
    else if (e.key === "ArrowLeft" && prevLightboxBtn.dataset.targetId) openLightbox(prevLightboxBtn.dataset.targetId);
    else if (e.key === "ArrowRight" && nextLightboxBtn.dataset.targetId) openLightbox(nextLightboxBtn.dataset.targetId);
  });

  searchInput.addEventListener("input", () => {
    clearTimeout(searchDebounce);
    searchDebounce = setTimeout(() => {
      query = searchInput.value.trim();
      resetAndLoad();
    }, 300);
  });

  window.addEventListener("scroll", () => {
    if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 600) {
      loadMore();
    }
  });

  async function checkSession() {
    const res = await fetch("/api/admin/session");
    const data = await res.json();
    document.body.classList.toggle("is-admin", data.authenticated);
    logoutBtn.style.display = data.authenticated ? "inline-block" : "none";
  }

  logoutBtn.addEventListener("click", async () => {
    await fetch("/api/admin/logout", { method: "POST" });
    document.body.classList.remove("is-admin");
    logoutBtn.style.display = "none";
  });

  async function openEditModal(id) {
    const res = await fetch(`/api/images/${id}`);
    if (!res.ok) return;
    const img = await res.json();
    editingId = img.id;
    editError.textContent = "";
    editCaption.value = img.caption || "";
    editDescription.value = img.description || "";
    editTags.value = (img.tags || []).join(", ");
    editCreatedDate.value = img.created_date || "";
    editSequence.value = img.sequence ?? "";
    editModal.classList.remove("hidden");
  }

  function closeEditModal() {
    editModal.classList.add("hidden");
    editingId = null;
  }

  editCloseBtn.addEventListener("click", closeEditModal);
  editModal.addEventListener("click", (e) => {
    if (e.target === editModal) closeEditModal();
  });

  editForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!editingId) return;
    editError.textContent = "";
    editSaveBtn.disabled = true;
    try {
      const formData = new FormData(editForm);
      const res = await fetch(`/api/admin/images/${editingId}`, { method: "PUT", body: formData });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        editError.textContent = err.detail || "Save failed.";
        return;
      }
      const updated = await res.json();
      const card = gallery.querySelector(`.card[data-id="${updated.id}"]`);
      if (card) {
        const capEl = card.querySelector(".caption");
        const cap = escapeHtml(updated.caption || "");
        if (capEl) capEl.textContent = updated.caption || "";
        else if (cap) card.querySelector("img").insertAdjacentHTML("afterend", `<div class="caption">${cap}</div>`);
      }
      closeEditModal();
    } finally {
      editSaveBtn.disabled = false;
    }
  });

  loadTags();
  loadMore();
  checkSession();
})();
