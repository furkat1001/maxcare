// Проверка MaxCare снаружи: Cloudflare Worker, запускается расписанием (Cron Trigger) раз в 5 минут.
// Сервер не может сообщить, что пропал совсем, — это делает эта проверка. Остальное — deploy/health-check.sh.
// Пишет в Telegram только при смене состояния; пока сбой не устранён — напоминание раз в 6 часов.
// Настройка — docs/deploy-vps.md, раздел 6.
//
// Настройки Worker:
//   STATE               — привязка к пространству KV: состояние между запусками
//   TELEGRAM_BOT_TOKEN  — секрет
//   TELEGRAM_CHAT_ID    — секрет
//   CHECK_URL           — необязательно: другой адрес, чтобы проверить само оповещение

const DEFAULT_URL = "https://api.maxcare.dexx.uz/health";
// Тревога после двух неудачных запусков подряд, то есть примерно через 10 минут:
// короткий перезапуск сервера не будит ночью. Пауз внутри запуска нет — их заменяет расписание.
const FAILED_RUNS_TO_ALERT = 2;
const REMIND_AFTER_MS = 6 * 60 * 60 * 1000;
const STATE_KEY = "uptime";

export default {
  async scheduled(controller, env) {
    for (const name of ["STATE", "TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID"]) {
      // Ошибка видна в журнале Worker: без настроек проверка не должна молча «работать».
      if (!env[name]) throw new Error(`не задана настройка ${name}`);
    }
    await check(env, controller.scheduledTime);
  },
};

async function check(env, now) {
  const saved = await env.STATE.get(STATE_KEY, "json");
  const state = saved ?? { failures: 0, alerted: false, notifiedAt: 0 };

  if (await isUp(env.CHECK_URL || DEFAULT_URL)) {
    // Если Telegram не принял сообщение, состояние не сбрасывается: следующий запуск повторит его.
    if (state.alerted && !(await notify(env, "сервер снова доступен снаружи"))) return;
    if (state.failures > 0 || state.alerted) await env.STATE.delete(STATE_KEY);
    return;
  }

  state.failures += 1;
  const due = state.alerted
    ? now - state.notifiedAt >= REMIND_AFTER_MS
    : state.failures >= FAILED_RUNS_TO_ALERT;
  if (due) {
    const text = state.alerted
      ? "сервер всё ещё не отвечает снаружи (Cloudflare)"
      : "сервер не отвечает снаружи (проверка из-за границы, Cloudflare). " +
        "Если приложения в Узбекистане работают, возможен сбой международного канала.";
    if (await notify(env, text)) {
      state.alerted = true;
      state.notifiedAt = now;
    }
  }
  await env.STATE.put(STATE_KEY, JSON.stringify(state));
}

// Две попытки подряд: вторая спасает от одного оборванного соединения.
async function isUp(url) {
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(20_000) });
      if (response.ok) return true;
      console.log(`попытка ${attempt}: код ${response.status}`);
    } catch (error) {
      console.log(`попытка ${attempt}: ${error.name}`);
    }
  }
  return false;
}

// В журнал не пишется ни адрес с токеном, ни текст ответа Telegram — только код.
async function notify(env, text) {
  try {
    const response = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ chat_id: env.TELEGRAM_CHAT_ID, text: `MaxCare: ${text}` }),
      signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) console.log(`Telegram не принял сообщение: код ${response.status}`);
    return response.ok;
  } catch (error) {
    console.log(`Telegram недоступен: ${error.name}`);
    return false;
  }
}
