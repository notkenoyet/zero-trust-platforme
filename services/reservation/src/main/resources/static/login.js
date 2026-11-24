"use strict";

const $ = (id) => document.getElementById(id);

if (new URLSearchParams(location.search).has("registered")) {
  $("info").textContent = "Compte créé. Connectez-vous.";
}

$("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("error").textContent = "";
  try {
    const res = await fetch("/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: $("username").value, password: $("password").value }),
    });
    if (res.ok) {
      location.replace("/app/reservation");
      return;
    }
    $("error").textContent = res.status === 401
      ? "Identifiant ou mot de passe incorrect."
      : "Service indisponible, réessayez.";
  } catch (err) {
    $("error").textContent = "Erreur réseau.";
  }
});