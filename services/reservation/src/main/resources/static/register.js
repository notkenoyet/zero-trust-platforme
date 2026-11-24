"use strict";

const $ = (id) => document.getElementById(id);

$("register-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("error").textContent = "";
  try {
    const res = await fetch("/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: $("username").value,
        password: $("password").value,
        email: $("email").value,
        first_name: $("first").value,
        last_name: $("last").value,
      }),
    });
    if (res.ok) {
      location.replace("/app/login?registered=1");
      return;
    }
    $("error").textContent =
      res.status === 409 ? "Identifiant ou e-mail déjà utilisé."
      : res.status === 400 || res.status === 422 ? "Vérifiez les champs (mot de passe : 10 caractères minimum)."
      : "Service indisponible, réessayez.";
  } catch (err) {
    $("error").textContent = "Erreur réseau.";
  }
});