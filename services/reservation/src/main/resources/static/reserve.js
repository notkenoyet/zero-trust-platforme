"use strict";

initBar();
$("date").min = new Date().toISOString().slice(0, 10);

function showConfirmation(data) {
  const r = data.reservation, n = data.notification;
  const ok = n.status === "sent";
  $("confirm-title").textContent = data.message;
  $("confirm-message").textContent = n.message;
  $("c-id").textContent = r.id;
  $("c-name").textContent = r.name;
  $("c-room").textContent = r.room;
  $("c-date").textContent = r.date;
  $("c-notif").textContent = ok ? "Envoyée le " + new Date(n.sent_at).toLocaleString("fr-FR") : "Non envoyée";
  $("confirmation").className = "confirmation " + (ok ? "ok" : "warn");
  $("confirmation").hidden = false;
}

$("reserve-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("error").textContent = "";
  $("confirmation").hidden = true;
  try {
    const res = await api("/reservations", {
      method: "POST",
      body: JSON.stringify({ name: $("name").value, room: $("room").value, date: $("date").value }),
    });
    if (!res.ok) {
      $("error").textContent = res.status === 422 ? "Vérifiez les champs saisis." : "La réservation a échoué.";
      return;
    }
    showConfirmation(await res.json());
  } catch (err) {
    if (err.message !== "session") $("error").textContent = "Erreur réseau.";
  }
});