"use client";

import { useCallback, useEffect, useState } from "react";

type Role = "customer" | "operator" | "checker";
type Auth = { username: string; password: string; role: Role };
type ApiResult<T> = { code: number; msg: string | null; data: T };
type Venue = { id: number; name: string; address: string; capacity: number };
type Event = { id: number; venueId: number; title: string; description: string | null };
type Session = {
  id: number; eventId: number; startsAt: string; endsAt: string; price: number;
  totalCapacity: number; available: number; locked: number; hot: boolean; status: string;
};
type Reservation = {
  id: number; sessionId: number; participantName: string; status: string;
  amount: number; code: string; expiresAt: string; createdAt: string;
};
type Report = { sessionId: number; reservations: number; paid: number; checkedIn: number; revenue: number };

function basic(auth: Auth) {
  const bytes = new TextEncoder().encode(`${auth.username}:${auth.password}`);
  return `Basic ${btoa(String.fromCharCode(...bytes))}`;
}

async function api<T>(path: string, auth?: Auth, body?: object): Promise<T> {
  const response = await fetch(`/api/backend${path}`, {
    method: body === undefined ? "GET" : "POST",
    headers: {
      ...(auth ? { Authorization: basic(auth) } : {}),
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  if (response.status === 401) throw new Error("账号或密码不正确");
  if (response.status === 403) throw new Error("当前账号没有此操作权限");
  if (!(response.headers.get("content-type") ?? "").includes("json")) {
    throw new Error(`后端返回 HTTP ${response.status}，请检查服务状态`);
  }
  const result = (await response.json()) as ApiResult<T>;
  if (!response.ok || result.code !== 1) throw new Error(result.msg || `请求失败（HTTP ${response.status}）`);
  return result.data;
}

function shortDate(value: string) {
  return value ? value.replace("T", " ").slice(0, 16) : "—";
}

function utcInputValue(minutesFromNow: number) {
  return new Date(Date.now() + minutesFromNow * 60_000).toISOString().slice(0, 16);
}

export default function Home() {
  const [auth, setAuth] = useState<Auth | null>(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [registering, setRegistering] = useState(false);
  const [tab, setTab] = useState<"discover" | "bookings" | "operations" | "checkin">("discover");
  const [venues, setVenues] = useState<Venue[]>([]);
  const [events, setEvents] = useState<Event[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [bookings, setBookings] = useState<Reservation[]>([]);
  const [participantName, setParticipantName] = useState("");
  const [queueResults, setQueueResults] = useState<Record<number, string>>( {} );
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [venueForm, setVenueForm] = useState({ name: "", address: "", capacity: 100 });
  const [eventForm, setEventForm] = useState({ venueId: "", title: "", description: "" });
  const [sessionForm, setSessionForm] = useState({ eventId: "", startsAt: utcInputValue(30), endsAt: utcInputValue(90), price: 0, capacity: 30, hot: false });
  const [paymentForm, setPaymentForm] = useState({ id: "", reference: "" });
  const [refundForm, setRefundForm] = useState({ id: "", reference: "" });
  const [checkInForm, setCheckInForm] = useState({ code: "", device: "front-gate" });
  const [reportId, setReportId] = useState("");
  const [report, setReport] = useState<Report | null>(null);

  const refreshCatalog = useCallback(async (current: Auth) => {
    const [nextVenues, nextEvents] = await Promise.all([
      api<Venue[]>("/api/venues", current),
      api<Event[]>("/api/events", current),
    ]);
    const nextSessions = (await Promise.all(nextEvents.map((event) =>
      api<Session[]>(`/api/events/${event.id}/sessions`, current)
    ))).flat();
    setVenues(nextVenues);
    setEvents(nextEvents);
    setSessions(nextSessions);
  }, []);

  const refreshBookings = useCallback(async (current: Auth) => {
    setBookings(await api<Reservation[]>("/api/reservations", current));
  }, []);

  useEffect(() => {
    if (!auth) return;
    refreshCatalog(auth).catch((error: Error) => setNotice(error.message));
    if (auth.role === "customer") refreshBookings(auth).catch((error: Error) => setNotice(error.message));
  }, [auth, refreshCatalog, refreshBookings]);

  async function action(work: () => Promise<void>, success: string) {
    setBusy(true);
    setNotice("");
    try { await work(); setNotice(success); }
    catch (error) { setNotice(error instanceof Error ? error.message : "操作失败"); }
    finally { setBusy(false); }
  }

  function login() {
    const candidate: Auth = {
      username: username.trim(), password,
      role: username.trim() === "operator" ? "operator" : username.trim() === "checker" ? "checker" : "customer",
    };
    action(async () => {
      if (!candidate.username || !candidate.password) throw new Error("请输入用户名和密码");
      await api<Venue[]>("/api/venues", candidate);
      setAuth(candidate);
      setTab(candidate.role === "operator" ? "operations" : candidate.role === "checker" ? "checkin" : "discover");
      setPassword("");
    }, `已进入 ${candidate.role === "operator" ? "运营" : candidate.role === "checker" ? "核销" : "用户"}工作台`);
  }

  function register() {
    action(async () => {
      await api<string>("/api/accounts", undefined, { username: username.trim(), password });
      setRegistering(false);
    }, "注册成功，请使用新账号登录");
  }

  async function reserve(session: Session) {
    if (!auth) return;
    await action(async () => {
      const result = await api<Reservation | string>("/api/reservations", auth, {
        sessionId: session.id, participantName: participantName.trim(),
      });
      if (typeof result === "string") setQueueResults((current) => ({ ...current, [session.id]: result }));
      else await refreshBookings(auth);
      await refreshCatalog(auth);
    }, session.hot ? "已进入队列，请查询排队结果" : "预约成功，已生成待支付订单");
  }

  async function checkQueue(sessionId: number) {
    if (!auth) return;
    await action(async () => {
      const result = await api<string | null>(`/api/reservations/queue/${sessionId}`, auth);
      setQueueResults((current) => ({ ...current, [sessionId]: result ?? "暂无结果" }));
      if (result?.startsWith("CONFIRMED:")) await refreshBookings(auth);
    }, "排队状态已更新");
  }

  const eventName = (id: number) => events.find((event) => event.id === id)?.title ?? `活动 #${id}`;
  const venueName = (id: number) => venues.find((venue) => venue.id === id)?.name ?? `场馆 #${id}`;

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand"><span className="brand-mark">V</span><span>VenuePass</span><span className="brand-sub">预约与履约演示</span></div>
        <div className="top-actions">
          {auth ? <><span className="identity"><span className="identity-dot" />{auth.username} · {auth.role === "operator" ? "运营" : auth.role === "checker" ? "核销" : "用户"}</span><button className="text-button" onClick={() => { setAuth(null); setBookings([]); setNotice(""); }}>退出</button></> : <span className="identity">本地演示平台</span>}
        </div>
      </header>

      {!auth ? (
        <main className="welcome">
          <div className="welcome-copy">
            <div className="eyebrow"><span className="eyebrow-line" /> VENUE OPERATIONS, SIMPLIFIED</div>
            <h1>从预约到入场，<br /><em>一站完成。</em></h1>
            <p>一个连接活动浏览、限量预约、支付登记与现场核销的轻量演示工作台。直接使用现有 VenuePass 后端数据。</p>
            <div className="flow"><span>01 创建场次</span><b>→</b><span>02 提交预约</span><b>→</b><span>03 入场核销</span></div>
          </div>
          <div className="login-card">
            <div className="card-kicker">WELCOME TO VENUEPASS</div>
            <h2>{registering ? "创建用户账号" : "进入演示平台"}</h2>
            <p>{registering ? "注册后可浏览和预约活动。" : "运营、核销及普通用户共用此入口。"}</p>
            <form onSubmit={(event) => { event.preventDefault(); registering ? register() : login(); }}>
              <label>用户名<input value={username} onChange={(event) => setUsername(event.target.value)} placeholder={registering ? "3–40 位字母、数字或下划线" : "例如 operator / checker / alice"} autoComplete="username" /></label>
              <label>密码<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder={registering ? "至少 8 位" : "输入账号密码"} autoComplete={registering ? "new-password" : "current-password"} /></label>
              <button className="primary wide" disabled={busy}>{busy ? "处理中…" : registering ? "注册账号" : "登录工作台"}<span>↗</span></button>
            </form>
            <button className="switch-link" onClick={() => { setRegistering(!registering); setNotice(""); }}>{registering ? "已有账号？返回登录" : "没有账号？注册普通用户"}</button>
            <div className="login-foot">运营及核销账号由后端环境变量配置。</div>
          </div>
        </main>
      ) : (
        <main className="workspace">
          <div className="workspace-heading"><div><div className="eyebrow"><span className="eyebrow-line" /> LIVE WORKSPACE</div><h1>{auth.role === "operator" ? "运营工作台" : auth.role === "checker" ? "入场核销" : "发现活动"}</h1><p>连接 VenuePass 后端 · 实时读取场馆、场次与预约状态</p></div><div className="heading-badge">DEMO / LOCAL</div></div>
          <nav className="tabs" aria-label="工作台导航">
            {auth.role !== "checker" && <button className={tab === "discover" ? "active" : ""} onClick={() => setTab("discover")}>活动与场次</button>}
            {auth.role === "customer" && <button className={tab === "bookings" ? "active" : ""} onClick={() => { setTab("bookings"); refreshBookings(auth).catch((error: Error) => setNotice(error.message)); }}>我的预约</button>}
            {auth.role === "operator" && <button className={tab === "operations" ? "active" : ""} onClick={() => setTab("operations")}>运营管理</button>}
            {(auth.role === "checker" || auth.role === "operator") && <button className={tab === "checkin" ? "active" : ""} onClick={() => setTab("checkin")}>入场核销</button>}
          </nav>

          {tab === "discover" && auth.role !== "checker" && <section className="panel-section">
            <div className="section-heading"><div><span className="section-number">01 / EXPLORE</span><h2>活动与场次</h2></div><div className="section-tools"><span className="muted">场次时间按 UTC 显示</span><button className="ghost" onClick={() => action(() => refreshCatalog(auth), "活动信息已刷新")} disabled={busy}>刷新列表 ↻</button></div></div>
            {events.length === 0 ? <div className="empty">暂无活动。运营人员可先创建场馆、活动和场次。</div> : events.map((event) => <div className="event-block" key={event.id}>
              <div className="event-heading"><div><span className="eyebrow small">EVENT #{event.id}</span><h3>{event.title}</h3><p>{event.description || "精彩活动，敬请参与"}</p></div><span className="venue-label">⌖ {venueName(event.venueId)}</span></div>
              <div className="session-grid">{sessions.filter((session) => session.eventId === event.id).length === 0 ? <p className="muted">暂无可展示场次</p> : sessions.filter((session) => session.eventId === event.id).map((session) => <div className="session-card" key={session.id}>
                <div className="session-top"><span className="pill">{session.hot ? "热门 · 排队预约" : "普通预约"}</span><span className={session.status === "OPEN" ? "status-open" : "status-closed"}>{session.status}</span></div>
                <div className="session-date">{shortDate(session.startsAt)}</div><div className="session-meta">至 {shortDate(session.endsAt)} · 场次 #{session.id}</div>
                <div className="session-bottom"><div><strong>¥{Number(session.price).toFixed(2)}</strong><small>剩余 {session.available} / {session.totalCapacity}</small></div>{auth.role === "customer" && <button className="primary compact" disabled={busy || session.status !== "OPEN" || session.available < 1} onClick={() => reserve(session)}>预约 →</button>}</div>
                {auth.role === "customer" && session.hot && queueResults[session.id] && <div className="queue-line"><span>{queueResults[session.id]}</span><button onClick={() => checkQueue(session.id)} disabled={busy}>查询结果</button></div>}
              </div>)}</div>
            </div>)}
            {auth.role === "customer" && <div className="participant-bar"><label>预约参与人姓名<input value={participantName} onChange={(event) => setParticipantName(event.target.value)} placeholder="预约前请填写姓名" /></label><span>同一用户对同一场次只能预约一次。</span></div>}
          </section>}

          {tab === "bookings" && auth.role === "customer" && <section className="panel-section"><div className="section-heading"><div><span className="section-number">02 / RESERVATIONS</span><h2>我的预约</h2></div><button className="ghost" onClick={() => action(() => refreshBookings(auth), "预约列表已刷新")} disabled={busy}>刷新列表 ↻</button></div>
            {bookings.length === 0 ? <div className="empty">暂无预约。前往活动与场次选择感兴趣的活动。</div> : <div className="booking-list">{bookings.map((booking) => <article className="booking-card" key={booking.id}><div className="booking-main"><span className="eyebrow small">RESERVATION #{booking.id}</span><h3>{eventName(sessions.find((session) => session.id === booking.sessionId)?.eventId ?? 0)}</h3><p>{booking.participantName} · 场次 #{booking.sessionId} · ¥{Number(booking.amount).toFixed(2)}</p><p>支付截止 {shortDate(booking.expiresAt)}</p></div><div className="booking-side"><span className="pill dark">{booking.status}</span><code>{booking.code}</code>{["PENDING_PAYMENT", "PAID"].includes(booking.status) && <button className="danger-link" disabled={busy} onClick={() => action(async () => { await api<string>(`/api/reservations/${booking.id}/cancel`, auth, {}); await refreshBookings(auth); await refreshCatalog(auth); }, "预约已取消；已支付预约需由运营人员处理退款")}>取消预约</button>}</div></article>)}</div>}
          </section>}

          {tab === "operations" && auth.role === "operator" && <section className="panel-section"><div className="section-heading"><div><span className="section-number">03 / OPERATIONS</span><h2>运营管理</h2></div><span className="muted">按场馆 → 活动 → 场次的顺序创建</span></div>
            <div className="form-grid">
              <form className="form-card" onSubmit={(event) => { event.preventDefault(); action(async () => { await api<Venue>("/api/admin/venues", auth, venueForm); await refreshCatalog(auth); setVenueForm({ name: "", address: "", capacity: 100 }); }, "场馆已创建"); }}><div className="card-kicker">STEP 01</div><h3>创建场馆</h3><label>场馆名称<input value={venueForm.name} onChange={(event) => setVenueForm({ ...venueForm, name: event.target.value })} required /></label><label>地址<input value={venueForm.address} onChange={(event) => setVenueForm({ ...venueForm, address: event.target.value })} required /></label><label>最大容量<input type="number" min="1" value={venueForm.capacity} onChange={(event) => setVenueForm({ ...venueForm, capacity: Number(event.target.value) })} required /></label><button className="primary wide" disabled={busy}>创建场馆 <span>→</span></button></form>
              <form className="form-card" onSubmit={(event) => { event.preventDefault(); action(async () => { await api<Event>("/api/admin/events", auth, { ...eventForm, venueId: Number(eventForm.venueId) }); await refreshCatalog(auth); setEventForm({ venueId: "", title: "", description: "" }); }, "活动已创建"); }}><div className="card-kicker">STEP 02</div><h3>创建活动</h3><label>所属场馆<select value={eventForm.venueId} onChange={(event) => setEventForm({ ...eventForm, venueId: event.target.value })} required><option value="">选择场馆</option>{venues.map((venue) => <option value={venue.id} key={venue.id}>{venue.name}</option>)}</select></label><label>活动名称<input value={eventForm.title} onChange={(event) => setEventForm({ ...eventForm, title: event.target.value })} required /></label><label>活动描述<input value={eventForm.description} onChange={(event) => setEventForm({ ...eventForm, description: event.target.value })} /></label><button className="primary wide" disabled={busy}>创建活动 <span>→</span></button></form>
              <form className="form-card" onSubmit={(event) => { event.preventDefault(); action(async () => { await api<Session>("/api/admin/sessions", auth, { ...sessionForm, eventId: Number(sessionForm.eventId), startsAt: `${sessionForm.startsAt}:00`, endsAt: `${sessionForm.endsAt}:00` }); await refreshCatalog(auth); }, "场次已创建"); }}><div className="card-kicker">STEP 03</div><h3>创建场次</h3><label>所属活动<select value={sessionForm.eventId} onChange={(event) => setSessionForm({ ...sessionForm, eventId: event.target.value })} required><option value="">选择活动</option>{events.map((event) => <option value={event.id} key={event.id}>{event.title}</option>)}</select></label><div className="two-col"><label>开始时间（UTC）<input type="datetime-local" value={sessionForm.startsAt} onChange={(event) => setSessionForm({ ...sessionForm, startsAt: event.target.value })} required /></label><label>结束时间（UTC）<input type="datetime-local" value={sessionForm.endsAt} onChange={(event) => setSessionForm({ ...sessionForm, endsAt: event.target.value })} required /></label></div><div className="two-col"><label>票价 ¥<input type="number" min="0" step="0.01" value={sessionForm.price} onChange={(event) => setSessionForm({ ...sessionForm, price: Number(event.target.value) })} required /></label><label>库存<input type="number" min="1" value={sessionForm.capacity} onChange={(event) => setSessionForm({ ...sessionForm, capacity: Number(event.target.value) })} required /></label></div><label className="check-label"><input type="checkbox" checked={sessionForm.hot} onChange={(event) => setSessionForm({ ...sessionForm, hot: event.target.checked })} /> 热门场次（创建后需预热库存）</label><button className="primary wide" disabled={busy}>创建场次 <span>→</span></button></form>
            </div>
            <div className="ops-divider" />
            <div className="ops-grid"><div className="form-card"><div className="card-kicker">SESSIONS</div><h3>场次操作</h3>{sessions.length === 0 ? <p className="muted">暂无场次</p> : sessions.map((session) => <div className="ops-row" key={session.id}><div><strong>#{session.id} · {eventName(session.eventId)}</strong><small>{shortDate(session.startsAt)} · {session.status} · 剩余 {session.available}</small></div><div>{session.hot && session.status === "OPEN" && <button className="mini-button" disabled={busy} onClick={() => action(() => api<string>(`/api/admin/sessions/${session.id}/preheat`, auth, {}).then(() => {}), "Redis 库存已预热")}>预热</button>}{session.status === "OPEN" && <button className="mini-button danger" disabled={busy} onClick={() => action(async () => { await api<string>(`/api/admin/sessions/${session.id}/cancel`, auth, {}); await refreshCatalog(auth); }, "场次已取消，已支付预约需处理退款")}>取消场次</button>}</div></div>)}</div>
              <div className="form-card"><div className="card-kicker">PAYMENT & REFUND</div><h3>支付与退款登记</h3><p className="muted">仅记录外部已确认的交易，不会实际扣款或退款。</p><form onSubmit={(event) => { event.preventDefault(); action(async () => { await api<string>(`/api/admin/reservations/${paymentForm.id}/payment`, auth, { reference: paymentForm.reference }); setPaymentForm({ id: "", reference: "" }); }, "支付已登记"); }}><div className="two-col"><label>预约 ID<input type="number" min="1" value={paymentForm.id} onChange={(event) => setPaymentForm({ ...paymentForm, id: event.target.value })} required /></label><label>支付交易号<input value={paymentForm.reference} onChange={(event) => setPaymentForm({ ...paymentForm, reference: event.target.value })} required /></label></div><button className="secondary" disabled={busy}>登记支付</button></form><form className="subform" onSubmit={(event) => { event.preventDefault(); action(async () => { await api<string>(`/api/admin/reservations/${refundForm.id}/refund`, auth, { reference: refundForm.reference }); setRefundForm({ id: "", reference: "" }); }, "退款已登记"); }}><div className="two-col"><label>预约 ID<input type="number" min="1" value={refundForm.id} onChange={(event) => setRefundForm({ ...refundForm, id: event.target.value })} required /></label><label>退款交易号<input value={refundForm.reference} onChange={(event) => setRefundForm({ ...refundForm, reference: event.target.value })} required /></label></div><button className="secondary" disabled={busy}>登记退款</button></form></div>
              <div className="form-card"><div className="card-kicker">REPORTING</div><h3>场次报表</h3><form onSubmit={(event) => { event.preventDefault(); action(async () => setReport(await api<Report | null>(`/api/admin/sessions/${reportId}/report`, auth)), "报表已更新"); }}><label>场次 ID<input type="number" min="1" value={reportId} onChange={(event) => setReportId(event.target.value)} required /></label><button className="secondary" disabled={busy}>查询报表</button></form>{report && <div className="report-grid"><div><strong>{report.reservations}</strong><small>预约总数</small></div><div><strong>{report.paid}</strong><small>已支付</small></div><div><strong>{report.checkedIn}</strong><small>已核销</small></div><div><strong>¥{Number(report.revenue).toFixed(2)}</strong><small>当前收入</small></div></div>}</div>
            </div>
          </section>}

          {tab === "checkin" && (auth.role === "checker" || auth.role === "operator") && <section className="panel-section"><div className="section-heading"><div><span className="section-number">04 / ADMISSION</span><h2>入场核销</h2></div></div><div className="checkin-layout"><form className="checkin-card" onSubmit={(event) => { event.preventDefault(); action(async () => { await api<string>("/api/check-in", auth, checkInForm); setCheckInForm({ ...checkInForm, code: "" }); }, "核销成功，已记录入场"); }}><div className="scan-icon">⌗</div><h3>验证预约码</h3><p>仅限已支付预约，且须在场次开放时间内核销。每个码仅可使用一次。</p><label>预约核销码<input value={checkInForm.code} onChange={(event) => setCheckInForm({ ...checkInForm, code: event.target.value })} placeholder="粘贴预约详情中的 code" required /></label><label>核销设备<input value={checkInForm.device} onChange={(event) => setCheckInForm({ ...checkInForm, device: event.target.value })} required /></label><button className="primary wide" disabled={busy}>确认入场 <span>→</span></button></form><div className="checkin-note"><span className="eyebrow small">HOW IT WORKS</span><h3>让每一次入场<br />都有据可查。</h3><p>核销成功后，系统将记录操作人员、设备与核销时间，并将预约状态更新为 CHECKED_IN。</p><div className="note-line">✓ 已支付状态校验</div><div className="note-line">✓ 场次时间窗口校验</div><div className="note-line">✓ 一次性核销码</div></div></div></section>}
        </main>
      )}

      {notice && <div role="status" className="toast"><span>{notice}</span><button aria-label="关闭提示" onClick={() => setNotice("")}>×</button></div>}
      <footer className="footer"><span>© VenuePass Demo</span><span>Next.js 前端 · Spring Boot 后端</span></footer>
    </div>
  );
}
