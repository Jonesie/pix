(() => {
  const gallery = document.getElementById("gallery");
  const loading = document.getElementById("loading");
  const empty = document.getElementById("empty");
  const searchInput = document.getElementById("search");
  const tagsRow = document.getElementById("tags-row");
  const logoutBtn = document.getElementById("logout-btn");
  const adminBtn = document.getElementById("admin-btn");
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

  function formatCardDate(iso) {
    if (!iso) return "";
    const d = new Date(iso + "T00:00:00");
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString("en-NZ", { day: "numeric", month: "short", year: "numeric" });
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
    const desc = escapeHtml(img.description || "");
    const date = escapeHtml(formatCardDate(img.created_date));
    return `<div class="card" data-id="${img.id}">
      <img src="/images/thumb/${img.filename_thumb}" loading="lazy" alt="${cap}" />
      ${cap ? `<div class="caption">${cap}</div>` : ""}
      ${desc ? `<div class="description">${desc}</div>` : ""}
      ${date ? `<div class="date">${date}</div>` : ""}
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
    window.location.href = `/image?id=${encodeURIComponent(card.dataset.id)}`;
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
    adminBtn.style.display = data.authenticated ? "inline-block" : "none";
    logoutBtn.style.display = data.authenticated ? "inline-block" : "none";
  }

  logoutBtn.addEventListener("click", async () => {
    await fetch("/api/admin/logout", { method: "POST" });
    document.body.classList.remove("is-admin");
    adminBtn.style.display = "none";
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
