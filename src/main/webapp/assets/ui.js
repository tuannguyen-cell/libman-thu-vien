export const $ = (s, root = document) => root.querySelector(s);
export const e = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const paths = {
  book: "M3 5c3-1 6-1 9 2 3-3 6-3 9-2v14c-4-1-6 0-9 2-3-2-6-3-9-2z M12 7v14",
  search: "M16 16l5 5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  box: "M3 7l9-5 9 5-9 5z M3 7v11l9 5 9-5V7 M12 12v11",
  users:
    "M15 21v-2a6 6 0 0 0-12 0v2 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M17 4a4 4 0 0 1 0 7 M17 15c3 0 5 2 5 5",
  card: "M2 5h20v15H2z M2 9h20 M5 15h5",
  stats: "M3 3v18h18 M7 17v-4 M12 17V8 M17 17V5",
  loan: "M3 6h16l-3-3 M21 18H5l3 3 M19 6l-3 3 M5 18l3-3",
  logout: "M10 3H4v18h6 M9 12h12l-4-4 M21 12l-4 4",
  eye: "M2 12c5-8 15-8 20 0-5 8-15 8-20 0z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
  close: "M5 5l14 14 M19 5L5 19",
  plus: "M12 4v16 M4 12h16",
  list: "M3 5h18 M3 12h18 M3 19h18",
  print: "M6 9V3h12v6 M6 17H3V9h18v8h-3 M6 14h12v7H6z",
  supplier: "M3 20V7l9-4 9 4v13z M8 20v-6h8v6 M7 9h2 M15 9h2",
};
export const icon = (k) =>
  `<svg aria-hidden="true" viewBox="0 0 24 24" class="ico"><path d="${paths[k] || paths.book}"/></svg>`;
export const brand = () =>
  `<div class="brand"><span class="brandmark">${icon("book")}</span><span>LibMan<small>THƯ VIỆN ĐẠI HỌC</small></span></div>`;
export const money = (v) =>
  new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(
    Number(v) || 0,
  ) + " ₫";
export const day = (s) =>
  String(s || "")
    .slice(0, 10)
    .split("-")
    .reverse()
    .join("/") || "—";
export const iso = () =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    new Date(),
  );
export const field = (
  n,
  label,
  v = "",
  type = "text",
  required = true,
  extra = "",
) =>
  `<label class="field">${e(label)}<input name="${e(n)}" type="${type}" value="${e(v)}" ${required ? "required" : ""} ${extra}></label>`;
export const select = (n, label, opts, v) =>
  `<label class="field">${e(label)}<select name="${e(n)}" required>${opts.map(([id, name]) => `<option value="${e(id)}" ${String(id) === String(v) ? "selected" : ""}>${e(name)}</option>`).join("")}</select></label>`;
export const button = (label, act, id = "", cls = "secondary", ico = "") =>
  `<button type="button" class="${cls}" aria-label="${e(label || (act === "logout" ? "Đăng xuất" : act))}" data-act="${e(act)}" data-id="${e(id)}">${ico ? icon(ico) : ""}${e(label)}</button>`;
export const table = (heads, rows, empty = "Chưa có dữ liệu.") =>
  rows.length
    ? `<div class="tablewrap"><table><thead><tr>${heads.map((h) => `<th scope="col">${e(h)}</th>`).join("")}</tr></thead><tbody>${rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`
    : `<div class="empty">${e(empty)}</div>`;
export const detail = (rows) =>
  `<dl class="detail">${rows.map(([k, v]) => `<dt>${e(k)}</dt><dd>${e(v ?? "—")}</dd>`).join("")}</dl>`;
export const tag = (s) => {
  const labels = {
    ACTIVE: "Đang hoạt động",
    NONE: "Chưa đăng ký",
    PENDING: "Chờ duyệt",
    LOCKED: "Đã khóa",
    PAID: "Đã thanh toán",
    UNPAID: "Chưa thanh toán",
  };
  return `<span class="tag ${["PENDING", "UNPAID"].includes(s) ? "warn" : ["NONE", "LOCKED"].includes(s) ? "off" : ""}">${e(labels[s] || s)}</span>`;
};
export const heading = (title, action = "") =>
  `<div class="heading"><h1>${e(title)}</h1><div class="actions">${action}</div></div>`;
export const form = (id, html, submit = "Lưu") =>
  `<form data-form="${id}">${html}<p class="error" role="alert"></p><div class="actions spaced"><button class="primary" type="submit">${e(submit)}</button></div></form>`;
export function modal(title, html) {
  const d = $("#dialog");
  d.innerHTML = `<div class="blockhead"><h2 id="dialogTitle">${e(title)}</h2>${button("Đóng", "close", "", "secondary")}</div>${html}`;
  d.setAttribute("aria-labelledby", "dialogTitle");
  if (!d.open) d.showModal();
}
export const close = () => $("#dialog").close();
let timer;
export function notify(s) {
  const t = $("#toast");
  t.textContent = s;
  clearTimeout(timer);
  timer = setTimeout(() => (t.textContent = ""), 4000);
}
export const state = {
  auth: {},
  page: "",
  items: [],
  search: "",
  group: "Tất cả",
  draft: null,
  pick: null,
  loan: [],
  filter: "",
  report: { type: "documents", from: iso().slice(0, 7) + "-01", to: iso() },
};
export async function api(path, data) {
  const r = await fetch("api/" + path, {
    method: data ? "POST" : "GET",
    headers: data
      ? {
          "Content-Type": "application/json",
          "X-CSRF-Token": state.auth.csrf || "",
        }
      : {},
    body: data ? JSON.stringify(data) : undefined,
    credentials: "same-origin",
  });
  const value = await r.json();
  if (!r.ok) {
    if (r.status === 401 && path !== "login" && state.auth.user) {
      close();
      location.reload();
    }
    throw Error(value.error || "Không thể hoàn tất yêu cầu.");
  }
  return value;
}
export const values = (f) => Object.fromEntries(new FormData(f));
export const staff = () => state.auth.user?.role === "Librarian";
export const valid = (r) => Boolean(Number(r.active) || r.active === true);
export const onScreen = (s) =>
  document
    .querySelector("#content")
    ?.replaceChildren(document.createTextNode(s));
