# Soukromá synchronizace na Cloudflare

Cloudflare Worker používá stejné rozhraní jako Python server. Kalendář ukládá do trvalé databáze SQLite na Cloudflare. Nasazení je určené pro jednu soukromou sadu dat sdílenou mezi vlastními zařízeními. Všechna zařízení používají stejný tajný klíč. Každý, kdo ho má, může celou sadu dat číst i měnit.

## Nasazení

V účtu Cloudflare používejte tarif **Workers Free**. Limity se vztahují na celý účet. Při vyčerpání bezplatného limitu může být synchronizace dočasně nedostupná. Změny aplikace v takovém případě uloží do zařízení a zkusí je poslat později. [Ceník a limity Cloudflare](https://developers.cloudflare.com/durable-objects/platform/pricing/)

V terminálu s aktuální LTS verzí Node.js přejděte do adresáře `sync/cloudflare`. Přihlaste se ke Cloudflare a potvrďte nasazení v požadovaném účtu.

```sh
npx --yes wrangler@4.168.0 login --scopes account:read user:read workers_scripts:write
npx wrangler deploy
```

Vygenerujte náhodný tajný klíč a uložte si ho do správce hesel.

```sh
python3 -c 'import secrets; print(secrets.token_urlsafe(32))'
```

Nastavte klíč jako tajnou proměnnou Workeru. Wrangler si hodnotu vyžádá interaktivně.

```sh
npx wrangler secret put CALENDAR_SYNC_TOKEN
```

Na obou zařízeních otevřete **Nastavení → Synchronizace**. Zadejte stejnou HTTPS adresu končící na `workers.dev`, kterou vypsal příkaz `deploy`, a stejný klíč. Stačí samotná adresa bez cesty `/v1/sync`. Poté uložte nastavení a zkuste přidat událost na jednom zařízení.

Při aktualizaci Workeru zachovejte jeho název a vazbu na třídu `CalendarStore`, aby zůstala připojená stávající databáze. Podrobnosti k obnově jsou v [dokumentaci úložiště Cloudflare](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/).
