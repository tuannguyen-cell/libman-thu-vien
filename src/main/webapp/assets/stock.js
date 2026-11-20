import {
  $,
  e,
  api,
  state,
  heading,
  button,
  icon,
  table,
  field,
  form,
  detail,
  select,
  tag,
  money,
  day,
  iso,
  modal,
  close,
  notify,
  valid,
} from "./ui.js";
import { refresh, editCatalog } from "./app.js";
const ctx = {
  mode: "list",
  supplierQuery: "",
  doc: null,
  invoice: 0,
  loanMode: "list",
  loanId: 0,
  member: "",
  due: "",
};
const draftKey = () =>
  "libman-draft-" + state.auth.user.id + "-" + state.auth.csrf;
export function loadDraft() {
  try {
    state.draft = JSON.parse(sessionStorage.getItem(draftKey()) || "null");
    if (state.draft) ctx.mode = "draft";
  } catch {
    state.draft = null;
  }
}
function persist() {
  if (state.draft)
    sessionStorage.setItem(draftKey(), JSON.stringify(state.draft));
}
export function discardDraft() {
  if (state.auth.user) sessionStorage.removeItem(draftKey());
  state.draft = null;
  ctx.doc = null;
  ctx.mode = "list";
}
export const hasDraft = () => Boolean(state.draft);
const activeDocs = async () => (await api("documents")).filter(valid);
const back = (label, action) => button(label, action, "", "secondary");
function lineTable(lines, priced, act) {
  return table(
    [
      "Mã",
      "Tên tài liệu",
      "Số lượng",
      ...(priced ? ["Đơn giá", "Thành tiền"] : []),
      "",
    ],
    lines.map((l) => [
      e(l.code),
      e(l.title),
      e(l.quantity),
      ...(priced
        ? [money(l.importPrice), money(l.quantity * l.importPrice)]
        : []),
      button("Bỏ", act, l.id, "textbtn"),
    ]),
    "Chưa có tài liệu trên phiếu.",
  );
}
function invoice(h) {
  return (
    heading("Hóa đơn nhập", back("Danh sách phiếu", "imports-list")) +
    `<section class="panel"><h2 class="print-title">LibMan · Hóa đơn nhập tài liệu</h2><div class="blockhead"><h2>${e(h.importCode)}</h2>${tag(h.paymentStatus)}</div>${detail(
      [
        ["Nhà cung cấp", h.supplierCode + " · " + h.supplierName],
        ["Nhân viên lập", h.librarianName],
        ["Ngày nhập", day(h.importDate)],
        [
          "Thời điểm thanh toán",
          h.paidAt
            ? day(h.paidAt) + " " + String(h.paidAt).slice(11, 19)
            : "Chưa thanh toán",
        ],
      ],
    )}${table(
      ["Mã", "Tài liệu", "Số lượng", "Đơn giá", "Thành tiền"],
      h.lines.map((l) => [
        e(l.documentCode),
        e(l.title),
        e(l.quantity),
        money(l.importPrice),
        money(l.amount),
      ]),
    )}<p class="amount spaced">Tổng thanh toán <b>${money(h.totalAmount)}</b></p><div class="actions spaced noprint">${button("In hóa đơn", "print", "", "secondary", "print")}${h.paymentStatus === "UNPAID" ? button("Xác nhận đã thanh toán", "pay", h.id, "primary") : back("Kết thúc", "imports-list")}</div></section>`
  );
}
async function imports() {
  if (ctx.mode === "invoice")
    return invoice(await api("invoice?id=" + ctx.invoice));
  if (ctx.mode === "supplier") {
    const ss = (
      await api("suppliers?q=" + encodeURIComponent(ctx.supplierQuery))
    ).filter(valid);
    return (
      heading("Chọn nhà cung cấp", back("Danh sách phiếu", "imports-list")) +
      `<div class="panel"><div class="blockhead"><form class="searchbar" data-form="supplier-search">${icon("search")}<input name="q" aria-label="Tên nhà cung cấp" value="${e(ctx.supplierQuery)}" placeholder="Tên nhà cung cấp"><button class="secondary">Tìm</button></form>${button("Thêm nhà cung cấp", "new-supplier", "", "secondary", "plus")}</div>${table(
        ["Mã", "Nhà cung cấp", "Địa chỉ", ""],
        ss.map((s) => [
          e(s.supplierCode),
          e(s.supplierName),
          e(s.address),
          button("Chọn", "choose-supplier", s.id),
        ]),
        "Không tìm thấy nhà cung cấp phù hợp.",
      )}</div>`
    );
  }
  if (ctx.mode === "draft" && state.draft) {
    const d = state.draft;
    return (
      heading("Lập đơn nhập", back("Danh sách phiếu", "imports-list")) +
      `<section class="panel"><div class="blockhead"><h2>${e(d.supplier.supplierName)}</h2><span class="tag warn">Bản nháp</span></div><p class="meta">${e(d.supplier.supplierCode)}</p><div class="spaced"><form data-form="find-code" class="searchbar">${icon("search")}<input aria-label="Mã tài liệu" name="code" required placeholder="Quét hoặc nhập đúng mã tài liệu" value="${e(ctx.code || "")}"><button class="secondary">Tìm mã</button></form></div>${ctx.missing ? `<p class="spaced">Chưa có tài liệu với mã ${e(ctx.code)}. ${button("Thêm tài liệu mới", "new-document")}</p>` : ""}${ctx.doc ? `<div class="panel"><h3>${e(ctx.doc.title)}</h3><p class="meta">${e(ctx.doc.documentCode)}</p>${form("add-line", `<div class="cols spaced">${field("quantity", "Số lượng", "1", "number", true, 'min="1" step="1"')}${field("importPrice", "Đơn giá (đồng)", "", "number", true, 'min="0.01" step="0.01"')}</div>`, "Thêm vào đơn")}</div>` : ""}</section><section class="panel"><h2>Chi tiết đơn nhập</h2>${lineTable(d.lines, true, "remove-line")}<p class="amount">Tổng tiền <b>${money(d.lines.reduce((s, l) => s + l.quantity * l.importPrice, 0))}</b></p>${form("commit-import", "", "Hoàn tất đơn nhập")}<div class="spaced">${button("Hủy bản nháp", "discard", "", "textbtn")}</div></section>`
    );
  }
  const hs = await api("imports");
  return (
    heading(
      "Nhập tài liệu",
      button(
        state.draft ? "Tiếp tục bản nháp" : "Lập đơn nhập",
        "start-import",
        "",
        "primary",
        "plus",
      ),
    ) +
    `<section class="panel"><div class="blockhead"><h2>Phiếu nhập kho</h2><span class="meta">${hs.length} phiếu</span></div>${table(
      ["Mã phiếu", "Nhà cung cấp", "Ngày nhập", "Tổng tiền", "Thanh toán", ""],
      hs.map((h) => [
        e(h.importCode),
        e(h.supplierName),
        day(h.importDate),
        money(h.totalAmount),
        tag(h.paymentStatus),
        button("Mở hóa đơn", "invoice", h.id, "textbtn"),
      ]),
      "Chưa có phiếu nhập. Chọn “Lập đơn nhập” để bắt đầu.",
    )}</section>`
  );
}
function mergeLine(lines, id, qty, doc, price = null) {
  if (!Number.isSafeInteger(qty) || qty <= 0)
    throw Error("Số lượng phải là số nguyên dương.");
  const old = lines.find((x) => x.id === id);
  if (old) {
    if (price !== null && old.importPrice !== price)
      throw Error("Mã này đã có đơn giá khác. Bỏ dòng cũ trước khi đổi giá.");
    if (old.quantity + qty > 2147483647) throw Error("Số lượng quá lớn.");
    old.quantity += qty;
  } else
    lines.push({
      id,
      quantity: qty,
      title: doc.title,
      code: doc.documentCode,
      ...(price === null ? {} : { importPrice: price }),
    });
}
async function loans() {
  if (ctx.loanMode === "return") {
    const ls = await api("loan?id=" + ctx.loanId);
    return (
      heading("Nhận trả tài liệu", back("Danh sách phiếu", "loans-list")) +
      `<section class="panel"><h2>${e(ctx.loanCode)}</h2>${form(
        "receive",
        table(
          ["Tài liệu", "Đã mượn", "Còn mượn", "Số trả lần này"],
          ls.map((l) => [
            e(l.documentCode) + " · " + e(l.title),
            e(l.quantity),
            e(l.remaining),
            `<input class="plain-input" style="max-width:95px" name="line-${l.id}" aria-label="Số trả ${e(l.title)}" type="number" value="0" min="0" max="${l.remaining}" step="1">`,
          ]),
        ),
        "Xác nhận trả",
      )}</section>`
    );
  }
  if (ctx.loanMode === "new") {
    const [ms, ds] = await Promise.all([api("members"), activeDocs()]);
    const eligible = ms.filter(
      (m) =>
        valid(m) &&
        m.cardStatus === "ACTIVE" &&
        String(m.cardExpiryDate) >= iso(),
    );
    return (
      heading("Lập phiếu mượn", back("Danh sách phiếu", "loans-list")) +
      `<section class="panel"><h2>Tài liệu cần mượn</h2>${form("borrow-line", `<div class="cols spaced">${select("id", "Chọn tài liệu", [["", "Chọn mã tài liệu"], ...ds.filter((d) => d.availableQuantity > 0).map((d) => [d.id, d.documentCode + " · " + d.title + " (" + d.availableQuantity + ")"])])}${field("quantity", "Số lượng", "1", "number", true, 'min="1" step="1"')}</div>`, "Thêm vào phiếu")}${lineTable(state.loan, false, "remove-borrow")}</section><section class="panel"><h2>Thông tin bạn đọc</h2>${eligible.length ? "" : '<p class="muted spaced">Chưa có bạn đọc có thẻ hoạt động và còn hạn. Cấp thẻ trong mục Bạn đọc trước khi cho mượn.</p>'}${form("commit-borrow", `<div class="cols spaced">${select("memberId", "Thẻ bạn đọc", [["", "Chọn bạn đọc"], ...eligible.map((m) => [m.id, m.memberCode + " · " + m.fullName])], ctx.member)}${field("dueDate", "Hạn trả", ctx.due, "date", true, 'min="' + iso() + '"')}</div>`, "Xác nhận cho mượn")}</section>`
    );
  }
  const hs = await api("loans");
  return (
    heading(
      "Mượn & trả",
      button("Lập phiếu mượn", "new-loan", "", "primary", "plus"),
    ) +
    `<section class="panel"><h2>Phiếu mượn tại quầy</h2>${table(
      ["Mã phiếu", "Bạn đọc", "Ngày mượn", "Hạn trả", "Còn mượn", ""],
      hs.map((h) => [
        e(h.orderCode),
        e(h.fullName),
        day(h.borrowDate),
        day(h.dueDate),
        e(h.remaining),
        button(
          Number(h.remaining) ? "Nhận trả" : "Chi tiết",
          "return",
          h.id,
          "textbtn",
        ),
      ]),
      "Chưa có phiếu mượn.",
    )}</section>`
  );
}
async function reports() {
  const s = state.report,
    types = [
      ["documents", "Tài liệu theo số lần mượn"],
      ["members", "Bạn đọc theo số lần mượn"],
      ["suppliers", "Nhà cung cấp theo số lượng nhập"],
    ];
  const rs = await api("stats?" + new URLSearchParams(s));
  const unit =
    s.type === "documents"
      ? "Lượt mượn"
      : s.type === "members"
        ? "Phiếu mượn"
        : "Số bản nhập";
  return (
    heading("Thống kê thư viện") +
    `<section class="panel">${form("report", select("type", "Loại thống kê", types, s.type) + `<div class="cols spaced">${field("from", "Từ ngày", s.from, "date")}${field("to", "Đến ngày", s.to, "date")}</div>`, "Xem thống kê")}</section><section class="panel"><div class="blockhead"><h2>${e(types.find((t) => t[0] === s.type)[1])}</h2><span class="meta">${day(s.from)} – ${day(s.to)}</span></div>${table(
      ["Mã", "Tên", unit, ...(s.type === "documents" ? [""] : [])],
      rs.map((r) => [
        e(r.code),
        e(r.label),
        `<b>${e(r.valueCount)}</b><span class="bar" style="--size:${Math.round((100 * r.valueCount) / rs[0].valueCount)}%"></span>`,
        ...(s.type === "documents"
          ? [button("Chi tiết", "report-detail", r.id, "textbtn")]
          : []),
      ]),
      "Chưa có phát sinh trong khoảng ngày đã chọn.",
    )}</section>`
  );
}
export function stockPage(k) {
  return k === "imports" ? imports() : k === "loans" ? loans() : reports();
}
export async function stockAction(act, id) {
  switch (act) {
    case "start-import":
      ctx.mode = state.draft ? "draft" : "supplier";
      break;
    case "imports-list":
      ctx.mode = "list";
      break;
    case "new-supplier":
      editCatalog("suppliers", {}, async (v) => {
        ctx.supplierQuery = v.supplierName;
        await refresh();
      });
      return;
    case "choose-supplier": {
      const supplier = (await api("suppliers")).find(
        (s) => s.id == id && valid(s),
      );
      if (!supplier) throw Error("Nhà cung cấp đã thay đổi.");
      state.draft = { supplier, token: crypto.randomUUID(), lines: [] };
      ctx.mode = "draft";
      ctx.doc = null;
      ctx.missing = false;
      ctx.code = "";
      persist();
      break;
    }
    case "new-document":
      editCatalog("catalog", { documentCode: ctx.code }, async (v) => {
        ctx.doc = (await activeDocs()).find(
          (d) => d.documentCode === v.documentCode,
        );
        ctx.code = v.documentCode;
        ctx.missing = false;
        await refresh();
      });
      return;
    case "remove-line":
      state.draft.lines = state.draft.lines.filter((l) => l.id != id);
      persist();
      break;
    case "discard":
      if (!confirm("Hủy đơn nhập đang lập?")) return;
      discardDraft();
      break;
    case "invoice":
      ctx.invoice = id;
      ctx.mode = "invoice";
      break;
    case "print":
      window.print();
      return;
    case "pay":
      if (!confirm("Bạn đã thanh toán thực tế cho nhà cung cấp?")) return;
      await api("payment", { id });
      notify("Đã ghi nhận thanh toán.");
      break;
    case "new-loan":
      ctx.loanMode = "new";
      state.loan = [];
      ctx.member = "";
      ctx.due = "";
      break;
    case "loans-list":
      ctx.loanMode = "list";
      break;
    case "remove-borrow":
      state.loan = state.loan.filter((l) => l.id != id);
      break;
    case "return": {
      const h = (await api("loans")).find((l) => l.id == id);
      ctx.loanId = id;
      ctx.loanCode = h.orderCode;
      if (!Number(h.remaining)) {
        const ls = await api("loan?id=" + id);
        modal(
          h.orderCode,
          table(
            ["Tài liệu", "Đã mượn", "Còn mượn"],
            ls.map((l) => [e(l.title), e(l.quantity), e(l.remaining)]),
          ),
        );
        return;
      }
      ctx.loanMode = "return";
      break;
    }
    case "report-detail": {
      const ls = await api(
        "stats-detail?" +
          new URLSearchParams({
            id,
            from: state.report.from,
            to: state.report.to,
          }),
      );
      modal(
        "Các dòng mượn trong kỳ",
        table(
          ["Mã phiếu", "Ngày mượn", "Bạn đọc", "Số bản"],
          ls.map((l) => [
            e(l.orderCode),
            day(l.borrowDate),
            e(l.fullName),
            e(l.quantity),
          ]),
        ),
      );
      return;
    }
    default:
      return;
  }
  await refresh();
}
export async function stockForm(name, v, f) {
  switch (name) {
    case "supplier-search":
      ctx.supplierQuery = v.q.trim();
      break;
    case "find-code": {
      ctx.code = v.code.trim();
      const ds = await api("documents?all=1");
      const match = ds.find(
        (d) => d.documentCode.toLowerCase() === ctx.code.toLowerCase(),
      );
      ctx.doc = match && valid(match) ? match : null;
      ctx.missing = !match;
      if (match && !ctx.doc) notify("Mã tài liệu đã ngừng sử dụng.");
      break;
    }
    case "add-line": {
      const price = Number(v.importPrice);
      if (!(price > 0) || !/^\d+(\.\d{1,2})?$/.test(v.importPrice))
        throw Error("Nhập đơn giá dương, tối đa hai chữ số thập phân.");
      mergeLine(
        state.draft.lines,
        ctx.doc.id,
        Number(v.quantity),
        ctx.doc,
        price,
      );
      ctx.doc = null;
      ctx.code = "";
      persist();
      break;
    }
    case "commit-import": {
      const d = state.draft;
      if (!d.lines.length)
        throw Error("Vui lòng thêm ít nhất một dòng tài liệu.");
      const { id } = await api("import", {
        supplierId: d.supplier.id,
        token: d.token,
        lines: d.lines,
      });
      discardDraft();
      ctx.mode = "invoice";
      ctx.invoice = id;
      notify("Đã lưu phiếu và cập nhật kho.");
      break;
    }
    case "borrow-line": {
      const d = (await activeDocs()).find((o) => o.id == v.id);
      if (!d) throw Error("Tài liệu không còn hoạt động.");
      const current = state.loan.find((l) => l.id === d.id);
      if ((current?.quantity || 0) + Number(v.quantity) > d.availableQuantity)
        throw Error("Không đủ số bản có sẵn.");
      mergeLine(state.loan, d.id, Number(v.quantity), d);
      break;
    }
    case "commit-borrow":
      if (!state.loan.length) throw Error("Thêm ít nhất một tài liệu.");
      await api("borrow", { ...v, lines: state.loan });
      ctx.loanMode = "list";
      state.loan = [];
      notify("Đã ghi nhận phiếu mượn.");
      break;
    case "receive": {
      const lines = Object.entries(v)
        .filter(([k, q]) => k.startsWith("line-") && Number(q) > 0)
        .map(([k, q]) => ({ id: Number(k.slice(5)), quantity: Number(q) }));
      if (!lines.length)
        throw Error("Nhập số lượng trả cho ít nhất một tài liệu.");
      await api("return", { loanId: ctx.loanId, lines });
      ctx.loanMode = "list";
      notify("Đã lưu phiếu trả.");
      break;
    }
    case "report":
      if (v.from > v.to) throw Error("Khoảng ngày không hợp lệ.");
      state.report = v;
      break;
    default:
      return;
  }
  await refresh();
}
document.addEventListener("change", (ev) => {
  if (ev.target.closest('[data-form="commit-borrow"]')) {
    if (ev.target.name === "dueDate") ctx.due = ev.target.value;
    if (ev.target.name === "memberId") ctx.member = ev.target.value;
  }
});
