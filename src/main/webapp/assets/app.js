import {
  $,
  e,
  icon,
  brand,
  button,
  field,
  form,
  heading,
  table,
  detail,
  tag,
  day,
  modal,
  close,
  notify,
  api,
  values,
  state,
  valid,
  staff,
} from "./ui.js";
import {
  stockPage,
  stockAction,
  stockForm,
  discardDraft,
  loadDraft,
  hasDraft,
} from "./stock.js";
const pages = {
  library: ["Tra cứu tài liệu", "book"],
  catalog: ["Danh mục tài liệu", "list"],
  members: ["Bạn đọc", "users"],
  suppliers: ["Nhà cung cấp", "supplier"],
  imports: ["Nhập tài liệu", "box"],
  loans: ["Mượn & trả", "loan"],
  reports: ["Thống kê", "stats"],
  card: ["Thẻ bạn đọc", "card"],
};
const menus = {
  Member: ["library", "card"],
  Librarian: ["library", "catalog", "members", "suppliers", "imports", "loans"],
  Manager: ["reports"],
};
const roles = {
  Member: "Bạn đọc",
  Librarian: "Nhân viên thư viện",
  Manager: "Nhân viên quản lý",
};
let generation = 0;
function login() {
  document.title = "Đăng nhập · LibMan";
  $("#app").innerHTML =
    `<div class="login"><section class="photo">${brand()}<div class="photo-copy"><div class="eyebrow">Cổng thư viện đại học</div><h1>Không gian<br><em>tri thức.</em></h1><p>Tra cứu tài liệu và sử dụng các dịch vụ thư viện trong cùng một không gian.</p></div></section><section class="login-side"><div class="eyebrow">Tài khoản thư viện</div><h2>Đăng nhập</h2><p class="muted">Sử dụng tài khoản được thư viện cấp để tiếp tục.</p><form data-form="login">${field("username", "Tên đăng nhập", "", "text", true, 'autocomplete="username" placeholder="Nhập tên đăng nhập"')}<label class="field">Mật khẩu<span class="password"><input name="password" type="password" autocomplete="current-password" placeholder="Nhập mật khẩu" required><button type="button" data-act="eye" aria-label="Hiện mật khẩu">${icon("eye")}</button></span></label><p class="error" role="alert"></p><button class="primary wide">Đăng nhập</button></form>${
      state.auth.demo
        ? `<div class="demo"><p>Điền tài khoản dùng thử</p><div class="chiprow">${[
            ["bandoc", "Bạn đọc"],
            ["thuthu", "Thủ thư"],
            ["quanly", "Quản lý"],
          ]
            .map(([id, l]) => button(l, "demo", id, "chip"))
            .join("")}</div></div>`
        : ""
    }</section></div>`;
}
function shell() {
  const u = state.auth.user;
  $("#app").innerHTML =
    `<div class="shell"><aside class="sidebar">${brand()}<div><div class="eyebrow">Nghiệp vụ</div><nav class="nav" aria-label="Nghiệp vụ">${menus[u.role].map((k) => button(pages[k][0], "nav", k, "", pages[k][1])).join("")}</nav></div><div class="sidebar-foot">${button("Đăng xuất", "logout", "", "signout", "logout")}</div></aside><div class="workspace"><header class="topbar"><span class="crumb" id="crumb"></span><span class="mobile-brand"><b>LibMan</b></span><div class="userchip"><span class="avatar">${e(u.fullName.split(" ").slice(-1)[0][0])}</span><span>${e(u.fullName)}<br><span class="meta">${roles[u.role]}</span></span>${button("", "logout", "", "iconbtn mobile-brand", "logout")}</div></header><div class="content" id="content"></div></div></div>`;
}
function route(k) {
  if (!menus[state.auth.user.role].includes(k))
    k = menus[state.auth.user.role][0];
  if (location.hash.slice(1) !== k) location.hash = k;
  else render(k);
}
async function render(k) {
  if (!menus[state.auth.user.role].includes(k)) {
    route(menus[state.auth.user.role][0]);
    return;
  }
  const token = ++generation;
  state.page = k;
  document.title = pages[k][0] + " · LibMan";
  $("#crumb").textContent = pages[k][0];
  document
    .querySelectorAll(".nav button")
    .forEach((b) => b.classList.toggle("active", b.dataset.id === k));
  $("#content").innerHTML = '<p class="muted">Đang tải…</p>';
  try {
    let html;
    if (k === "library" || k === "catalog") {
      const list = await api(
        "documents" +
          (k === "catalog"
            ? "?all=1"
            : state.search
              ? "?q=" + encodeURIComponent(state.search)
              : ""),
      );
      if (token !== generation) return;
      state.items = list;
      html = k === "library" ? library() : directory(k);
    } else if (k === "members" || k === "suppliers") {
      const list = await api(k);
      if (token !== generation) return;
      state.items = list;
      html = directory(k);
    } else if (k === "card") {
      const m = await api("me");
      html =
        heading("Thẻ bạn đọc") +
        `<section class="panel"><div class="blockhead"><h2>${e(m.fullName)}</h2>${tag(m.cardStatus)}</div>${detail(
          [
            ["Mã bạn đọc", m.memberCode],
            ["Ngày đăng ký", day(m.registrationDate)],
            ["Ngày cấp", day(m.cardIssueDate)],
            ["Ngày hết hạn", day(m.cardExpiryDate)],
          ],
        )}${m.cardStatus === "NONE" ? button("Đăng ký thẻ trực tuyến", "register", null, "primary") : ""}</section>`;
    } else html = await stockPage(k);
    if (token === generation) $("#content").innerHTML = html;
  } catch (err) {
    if (token === generation)
      $("#content").innerHTML =
        `<p class="error">${e(err.message)}</p>${button("Thử lại", "refresh")}`;
  }
}
const tints = [
  ["#e7e1c9", "#4e6242"],
  ["#ede0d1", "#976447"],
  ["#dde6df", "#456c60"],
  ["#e1e2d8", "#536075"],
  ["#e8dfcf", "#846a41"],
  ["#e8e4d6", "#66596c"],
];
function library() {
  const groups = ["Tất cả", ...new Set(state.items.map((d) => d.category))],
    items = state.items.filter(
      (d) => state.group === "Tất cả" || d.category === state.group,
    );
  return `<section class="hero"><div class="eyebrow">Thư viện đại học</div><h1>Tra cứu <em>tài liệu</em></h1><form data-form="search" class="searchbar">${icon("search")}<input aria-label="Tên tài liệu" name="q" value="${e(state.search)}" required placeholder="Nhập tên tài liệu cần tìm…"><button class="primary">Tìm kiếm</button></form></section><div class="blockhead"><h2>${state.search ? "Kết quả tìm kiếm" : "Danh mục thư viện"}</h2><span class="meta">${items.length} đầu tài liệu</span></div><div class="chiprow">${groups.map((g) => button(g, "group", g, "chip " + (g === state.group ? "on" : ""))).join("")}${state.search ? button("Xóa từ khóa", "reset", "", "textbtn") : ""}</div><div class="grid spaced">${items
    .map((d) => {
      const [t, c] = tints[(d.id - 1) % tints.length];
      return `<button class="book" data-act="detail" data-id="${d.id}"><span class="book-art" style="--tint:${t};--ink:${c}"><span class="cover"><small>LIBMAN</small><span>${e(d.title)}</span><small>${e(d.documentCode)}</small></span></span><span class="book-info"><span class="meta">${e(d.category)} · ${e(d.documentCode)}</span><strong>${e(d.title)}</strong><span class="meta">${e(d.author || "Chưa có tác giả")}</span></span></button>`;
    })
    .join(
      "",
    )}</div>${items.length ? "" : '<p class="empty">Không tìm thấy tài liệu phù hợp.</p>'}`;
}
const info = {
  catalog: {
    key: "document",
    title: "tài liệu",
    fields: [
      ["documentCode", "Mã tài liệu"],
      ["title", "Tên tài liệu"],
      ["category", "Loại tài liệu"],
      ["author", "Tác giả"],
      ["publisher", "Nhà xuất bản"],
      ["publishYear", "Năm xuất bản"],
      ["shelfLocation", "Vị trí kệ"],
    ],
  },
  suppliers: {
    key: "supplier",
    title: "nhà cung cấp",
    fields: [
      ["supplierCode", "Mã nhà cung cấp"],
      ["supplierName", "Tên nhà cung cấp"],
      ["address", "Địa chỉ"],
      ["phone", "Điện thoại"],
      ["email", "Email"],
    ],
  },
  members: {
    key: "member",
    title: "bạn đọc",
    fields: [
      ["fullName", "Họ tên"],
      ["dateOfBirth", "Ngày sinh"],
      ["email", "Email"],
      ["phone", "Điện thoại"],
      ["address", "Địa chỉ"],
    ],
  },
};
function directory(k) {
  const spec = info[k],
    filter = state.filter.toLocaleLowerCase("vi");
  let all = state.items.filter(
    (o) =>
      spec.fields.some(([f]) =>
        String(o[f] ?? "")
          .toLocaleLowerCase("vi")
          .includes(filter),
      ) ||
      String(o.memberCode || "")
        .toLowerCase()
        .includes(filter),
  );
  let heads =
    k === "catalog"
      ? ["Mã", "Tên tài liệu", "Loại", "Tổng / có sẵn"]
      : k === "members"
        ? ["Mã thẻ", "Họ tên", "Tài khoản", "Thẻ"]
        : ["Mã", "Nhà cung cấp", "Điện thoại", "Email"];
  const rows = all.map((o) => {
    let r =
      k === "catalog"
        ? [
            e(o.documentCode),
            e(o.title),
            e(o.category),
            o.totalQuantity + " / " + o.availableQuantity,
          ]
        : k === "members"
          ? [
              e(o.memberCode || "—"),
              e(o.fullName),
              e(o.username),
              tag(o.cardStatus),
            ]
          : [e(o.supplierCode), e(o.supplierName), e(o.phone), e(o.email)];
    r.push(
      valid(o) ? "Đang dùng" : "Ngừng dùng",
      `<div class="actions">${button("Sửa", "edit", o.id, "textbtn")}${k === "members" && valid(o) && o.cardStatus !== "NONE" ? button("Thẻ", "manage-card", o.id, "textbtn") : ""}${valid(o) ? button("Xóa", "remove", o.id, "textbtn") : ""}</div>`,
    );
    return r;
  });
  return (
    heading(
      pages[k][0],
      button("Thêm " + spec.title, "new", "", "primary", "plus"),
    ) +
    `<div class="panel"><form class="searchbar" data-form="filter">${icon("search")}<input name="q" value="${e(state.filter)}" placeholder="Tìm theo mã hoặc thông tin" aria-label="Lọc danh mục"><button class="secondary">Lọc</button></form><div class="spaced">${table([...heads, "Trạng thái", "Thao tác"], rows)}</div></div>`
  );
}
export function editCatalog(k, o = {}, callback = null) {
  state.editor = { k, id: o.id, callback };
  const spec = info[k],
    fields = spec.fields
      .map(([n, l], i) =>
        field(
          n,
          l,
          o[n] || "",
          n === "dateOfBirth"
            ? "date"
            : n === "email"
              ? "email"
              : n === "publishYear"
                ? "number"
                : "text",
          k === "catalog" ? i < 3 : k === "suppliers" ? i < 2 : i === 0,
        ),
      )
      .join("");
  modal(
    (o.id ? "Sửa " : "Thêm ") + spec.title,
    form(
      "save-item",
      `<div class="cols">${k === "members" && !o.id ? field("username", "Tên đăng nhập") + field("password", "Mật khẩu ban đầu", "", "password", true, 'minlength="8"') : ""}${fields}</div>`,
    ),
  );
}
function cardForm(o) {
  modal(
    "Quản lý thẻ bạn đọc",
    form(
      "card-action",
      `<input type="hidden" name="id" value="${o.id}"><p>${e(o.fullName)} · ${tag(o.cardStatus)}</p>${field("expiry", "Hạn sử dụng", o.cardExpiryDate || "", "date")}`,
      "Cấp / gia hạn thẻ",
    ) +
      (o.cardStatus === "ACTIVE"
        ? button("Khóa thẻ", "lock", o.id, "danger spaced")
        : ""),
  );
}
export const refresh = () => render(state.page);
document.addEventListener("click", async (ev) => {
  const b = ev.target.closest("[data-act]");
  if (!b) return;
  const { act, id } = b.dataset;
  try {
    switch (act) {
      case "demo": {
        const f = $("form[data-form=login]");
        f.username.value = id;
        f.password.value = "ThuVien@2026";
        f.querySelector("button.primary").focus();
        break;
      }
      case "eye": {
        const p = $("input[name=password]");
        p.type = p.type === "password" ? "text" : "password";
        b.setAttribute(
          "aria-label",
          p.type === "password" ? "Hiện mật khẩu" : "Ẩn mật khẩu",
        );
        break;
      }
      case "nav":
        state.filter = "";
        route(id);
        break;
      case "close":
        close();
        break;
      case "logout":
        if (
          confirm(
            hasDraft()
              ? "Đăng xuất và hủy bản nháp đang lập?"
              : "Đăng xuất khỏi LibMan?",
          )
        ) {
          await api("logout", {});
          discardDraft();
          state.loan = [];
          state.search = "";
          location.replace(location.pathname);
        }
        break;
      case "group":
        state.group = id;
        $("#content").innerHTML = library();
        break;
      case "reset":
        state.group = "Tất cả";
        state.search = "";
        await refresh();
        break;
      case "detail": {
        const d = await api("document?id=" + id);
        modal(
          d.title,
          detail([
            ["Mã tài liệu", d.documentCode],
            ["Tác giả", d.author],
            ["Nhà xuất bản", d.publisher],
            ["Năm xuất bản", d.publishYear],
            ["Loại", d.category],
            ["Vị trí kệ", d.shelfLocation],
            ["Số bản có sẵn", d.availableQuantity],
            [
              "Trạng thái",
              d.availableQuantity > 0 ? "Có thể mượn" : "Đã cho mượn hết",
            ],
          ]),
        );
        break;
      }
      case "register": {
        const m = await api("me");
        modal(
          "Đăng ký thẻ bạn đọc",
          form(
            "register",
            `<div class="cols">${field("memberCode", "Mã bạn đọc", m.memberCode)}${info.members.fields.map(([n, l]) => field(n, l, m[n], n === "dateOfBirth" ? "date" : n === "email" ? "email" : "text")).join("")}</div>`,
            "Gửi đăng ký",
          ),
        );
        break;
      }
      case "new":
        editCatalog(state.page);
        break;
      case "edit":
        editCatalog(
          state.page,
          state.items.find((o) => o.id == id),
        );
        break;
      case "manage-card":
        cardForm(state.items.find((o) => o.id == id));
        break;
      case "lock":
        if (confirm("Khóa thẻ của bạn đọc này?")) {
          await api("card", { id, status: "LOCKED" });
          close();
          await refresh();
        }
        break;
      case "remove":
        if (
          confirm(
            "Xóa bản ghi này? Bản ghi có lịch sử giao dịch sẽ được ngừng sử dụng.",
          )
        ) {
          await api("remove-" + info[state.page].key, { id });
          await refresh();
        }
        break;
      case "refresh":
        await refresh();
        break;
      default:
        await stockAction(act, id);
    }
  } catch (err) {
    notify(err.message);
  }
});
document.addEventListener("submit", async (ev) => {
  const f = ev.target;
  if (!f.matches("form[data-form]")) return;
  ev.preventDefault();
  const name = f.dataset.form,
    v = values(f),
    btn = ev.submitter;
  if (btn) btn.disabled = true;
  const err = $(".error", f);
  if (err) err.textContent = "";
  try {
    switch (name) {
      case "login":
        state.auth = await api("login", v);
        loadDraft();
        shell();
        route(menus[state.auth.user.role][0]);
        break;
      case "search":
        if (!v.q.trim()) throw Error("Vui lòng nhập tên tài liệu.");
        state.search = v.q.trim();
        state.group = "Tất cả";
        await refresh();
        break;
      case "filter":
        state.filter = v.q;
        $("#content").innerHTML = directory(state.page);
        break;
      case "save-item": {
        const { k, id, callback } = state.editor;
        await api("save-" + info[k].key, { ...v, id });
        close();
        notify("Đã lưu thông tin.");
        if (callback) await callback(v);
        else await refresh();
        break;
      }
      case "card-action":
        await api("card", { ...v, status: "ACTIVE" });
        close();
        await refresh();
        break;
      case "register":
        await api("register-card", v);
        close();
        await refresh();
        break;
      default:
        await stockForm(name, v, f);
    }
  } catch (ex) {
    if (err) err.textContent = ex.message;
    else notify(ex.message);
  } finally {
    if (btn) btn.disabled = false;
  }
});
window.addEventListener("hashchange", () => {
  if (state.auth.user) {
    close();
    route(location.hash.slice(1));
  }
});
async function init() {
  try {
    state.auth = await api("session");
    if (!state.auth.user) {
      login();
      return;
    }
    loadDraft();
    shell();
    route(location.hash.slice(1));
  } catch (err) {
    $("#app").innerHTML =
      `<div class="loading"><h2>Không thể mở thư viện</h2><p class="error">${e(err.message)}</p><button class="secondary" id="retry">Thử lại</button></div>`;
    $("#retry").onclick = init;
  }
}
init();
