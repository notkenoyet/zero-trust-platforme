"use strict";

const $ = (id) => document.getElementById(id);

// Le cookie de session est envoyé automatiquement par le navigateur.
// Si la session a expiré (401), on retourne à la page de connexion.
async function api(path, options = {}) {
  const res = await fetch(path, Object.assign({}, options, {
    headers: Object.assign({ "Content-Type": "application/json" }, options.headers || {}),
    credentials: "same-origin",
  }));
  if (res.status === 401) {
    location.replace("/app/login");
    throw new Error("session");
  }
  return res;
}

async function logout() {
  try { await fetch("/logout", { method: "POST" }); } catch (e) { /* ignore */ }
  location.replace("/app/login");
}

async function initBar() {
  $("logout").addEventListener("click", logout);
  try {
    const res = await api("/me");
    const data = await res.json();
    $("whoami").textContent = "Connecté : " + data.username;
    const name = $("name");
    if (name && !name.value) name.value = data.username;
  } catch (e) { /* redirection déjà faite */ }
}