"use strict";

initBar();

(async () => {
  try {
    const res = await api("/reservations");
    if (!res.ok) return;
    const data = await res.json();
    const tbody = $("rows");
    tbody.replaceChildren();
    for (const r of data.reservations) {
      const tr = document.createElement("tr");
      for (const text of [r.name || r.user, r.date, r.room]) {
        const td = document.createElement("td");
        td.textContent = text;
        tr.appendChild(td);
      }
      tbody.appendChild(tr);
    }
    $("empty").hidden = data.reservations.length > 0;
  } catch (e) { /* redirection déjà faite si session expirée */ }
})();