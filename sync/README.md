# Synchronizace telefonu a tabletu

Server uchovává jednu soukromou sadu kalendářů, událostí a poznámek. Aplikace funguje i bez připojení a změny odešle po jeho obnovení. Obě zařízení musí mít přístup ke stejné adrese serveru a používat stejný tajný klíč.

Pro provoz na bezplatném tarifu Workers Free použijte [nasazení na Cloudflare](cloudflare/README.md). Následující postup je pro vlastní Python server.

## Spuštění

Vyžaduje Python 3.10 nebo novější a trvalé úložiště pro databázi SQLite. Vygenerujte klíč a bezpečně jej uložte pro další spuštění serveru a nastavení obou zařízení.

```sh
python3 -c 'import secrets; print(secrets.token_urlsafe(32))'
```

Nastavte proměnnou prostředí `CALENDAR_SYNC_TOKEN` na vygenerovaný klíč. Spusťte server z kořene projektu.

```sh
python3 sync/server.py --database /cesta/k/databazi/calendar.sqlite3
```

Výchozí adresa serveru je `127.0.0.1:8765`. Pro fyzická zařízení ji zpřístupněte přes HTTPS reverzní proxy s důvěryhodným certifikátem. Proxy musí předávat `POST /v1/sync` včetně hlavičky `Authorization`. Server umí obsloužit HTTPS i přímo pomocí přepínačů `--bind 0.0.0.0 --cert /cesta/fullchain.pem --key /cesta/privkey.pem`. Adresa a port musí být dosažitelné z obou zařízení.

V aplikaci na obou zařízeních otevřete **Nastavení → Synchronizace**, zadejte stejnou adresu HTTPS včetně případného portu a stejný klíč. Během vývoje lze v debug verzi použít z Android emulátoru `http://10.0.2.2:8765` pro přístup k serveru běžícímu na hostiteli.

Pokud je server pouze v místní síti, na Androidu 17 při nedostupném spojení použijte v nastavení synchronizace **Povolit přístup k serveru v místní síti**.

## Jak se změny projeví

- Aplikace synchronizuje při otevření, pravidelně během používání a na pozadí podle možností Androidu. Ruční tlačítko **Synchronizovat nyní** je v nastavení.
- Každé zařízení po synchronizaci naplánuje vlastní upozornění podle časového pásma události. Na každém zařízení povolte oznámení pro aplikaci. Pro co nejpřesnější čas lze v nastavení aplikace povolit také přesná upozornění. Android může zpozdit synchronizaci na pozadí.
- První připojení sloučí místní záznamy se záznamy na serveru. Pokud dvě zařízení změní stejnou poznámku, událost nebo kalendář, platí poslední změna přijatá serverem. Změny různých záznamů se zachovají.
- Každý, kdo zná klíč, může tuto sadu číst i upravovat. Server ukládá data do SQLite. Zálohujte databázi pomocí zálohovacího rozhraní SQLite a uchovávejte ji na soukromém úložišti.
