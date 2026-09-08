(() => {
  const loginPanel = document.getElementById("login-panel");
  const adminArea = document.getElementById("admin-area");
  const passwordInput = document.getElementById("password");
  const loginError = document.getElementById("login-error");
  const logoutBtn = document.getElementById("logout-btn");
  const loginForm = document.getElementById("login-form");
  const uploadForm = document.getElementById("upload-form");
  const uploadBtn = document.getElementById("upload-btn");
  const uploadError = document.getElementById("upload-error");
  const uploadSuccess = document.getElementById("upload-success");

  function showLoggedIn() {
    loginPanel.style.display = "none";
    adminArea.style.display = "block";
    logoutBtn.style.display = "inline-block";
  }

  function showLoggedOut() {
    loginPanel.style.display = "block";
    adminArea.style.display = "none";
    logoutBtn.style.display = "none";
  }

  async function checkSession() {
    const res = await fetch("/api/admin/session");
    const data = await res.json();
    if (data.authenticated) showLoggedIn();
    else showLoggedOut();
  }

  loginForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    loginError.textContent = "";
    const body = new URLSearchParams({ password: passwordInput.value });
    const res = await fetch("/api/admin/login", { method: "POST", body });
    if (res.ok) {
      passwordInput.value = "";
      showLoggedIn();
    } else {
      loginError.textContent = "Wrong password.";
    }
  });

  logoutBtn.addEventListener("click", async () => {
    await fetch("/api/admin/logout", { method: "POST" });
    showLoggedOut();
  });

  uploadForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    uploadError.textContent = "";
    uploadSuccess.textContent = "";
    uploadBtn.disabled = true;
    try {
      const formData = new FormData(uploadForm);
      const res = await fetch("/api/admin/images", { method: "POST", body: formData });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        uploadError.textContent = err.detail || "Upload failed.";
        return;
      }
      uploadForm.reset();
      uploadSuccess.textContent = "Uploaded.";
    } finally {
      uploadBtn.disabled = false;
    }
  });

  checkSession();
})();
