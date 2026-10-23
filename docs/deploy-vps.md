# Сервер MaxCare на VPS — настройка

Решение от 2026-10-23: **Hostinger VPS KVM 2** (2 vCPU, 8 ГБ RAM, 100 ГБ NVMe), Ubuntu 24.04 LTS
без панели управления, дата-центр в **Германии или Франции**.

Почему так:
- нужен VPS, а не веб-хостинг: сервер — Kotlin/Ktor (JVM) + PostgreSQL + Redis в Docker;
- Hetzner Cloud после повышения цен 15.06.2026 дороже, а дешёвые линейки (CX, CAX) «нет в наличии»;
- страна: с 26.03.2026 закон Узбекистана о персональных данных требует хранить в стране только
  биометрию, генетические и абонентские данные; Германия и Франция — в списке 49 стран для
  упрощённой передачи (август 2026). Литвы в опубликованном списке нет. **Подтвердить у юриста**
  вместе с регистрацией базы ПД (roadmap, «Неделя 0»); до ответа не оплачивать надолго вперёд.

Секреты (пароль root, пароль `deploy`, закрытый SSH-ключ, `MAXCARE_SECRET_KEY`, пароль базы)
в репозиторий и в чат не попадают.

## 0. SSH-ключ на компьютере (Windows, PowerShell)

```
ssh-keygen -t ed25519 -C "maxcare-server"
```

- путь — Enter (по умолчанию `C:\Users\<имя>\.ssh\id_ed25519`);
- passphrase — задать (защита ключа, если файл утечёт).

Открытый ключ для Hostinger: `Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub` — строка
`ssh-ed25519 … maxcare-server` целиком; имя ключа в Hostinger — по компьютеру (`probook-laptop`).
Файл без `.pub` — закрытый ключ, никуда не отправлять.

При покупке: «What to install» → Operating system → **Ubuntu 24.04 LTS** (или шаблон «Docker»
на Ubuntu 24.04). Панели (cPanel, CyberPanel, CloudPanel…) и приложения — не нужны. Docker
Manager можно включить для просмотра, но контейнеры MaxCare управляются только через
`docker compose` из репозитория.

## 1. Первый вход и пользователь `deploy`

IP-адрес — в hPanel. На компьютере:

```
ssh root@IP
```

На сервере, по одной команде:

```
apt update && apt -y full-upgrade
adduser deploy
usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh && cp /root/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh && chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
```

**Не закрывая окно root**, во втором окне проверить:

```
ssh deploy@IP
sudo whoami      # → root
```

Только после успешной проверки — шаг 2: иначе можно потерять доступ к серверу.

## 2. Защита SSH, файрвол, автообновления

Под `deploy`:

```
printf 'PermitRootLogin no\nPasswordAuthentication no\nKbdInteractiveAuthentication no\n' | sudo tee /etc/ssh/sshd_config.d/00-hardening.conf
sudo sshd -t && sudo systemctl restart ssh
```

Файл с префиксом `00-` читается первым и перекрывает `50-cloud-init.conf`, который на Ubuntu
может разрешать вход по паролю. Проверить в **новом** окне, что `ssh deploy@IP` по-прежнему
пускает, а `ssh root@IP` — нет.

```
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo apt -y install unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
```

Docker публикует порты в обход `ufw`, поэтому в `docker compose` наружу открываются только 80 и
443 (Caddy); PostgreSQL и Redis — только во внутренней сети Docker.

Docker (если не ставился шаблоном или Docker Manager):

```
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker deploy    # перезайти по SSH
```

## 3. Домен и TLS

- DNS-запись `A`: `api.maxcare.dexx.uz` → IP сервера (у регистратора / DNS-провайдера `dexx.uz`).
- Сертификат — **только Let's Encrypt**: приложение ребёнка закрепляет корни Let's Encrypt
  (`android/app-kids/src/main/res/xml/network_security_config.xml`, срок привязки до 2027-10-01).
  В Caddy указать ACME-издателя Let's Encrypt явно, без запасного ZeroSSL.

## 4. Что ещё сделать (в репозитории, не начато)

- `Dockerfile` сервера и `docker-compose` для продакшена: сервер, PostgreSQL, Redis, Caddy (TLS).
- Шаблон переменных окружения (`MAXCARE_SECRET_KEY`, `MAXCARE_DB_*`, `MAXCARE_REDIS_URL`,
  `MAXCARE_GOOGLE_CLIENT_IDS`); сами значения — только в файле на сервере.
- Ежедневные шифрованные копии базы **в другое место**, проверка восстановления.
- Мониторинг доступности и места на диске.
