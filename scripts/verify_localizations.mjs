import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { catalog, web } from './build_web_localizations.mjs';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const locales = ['en', 'es', 'fr', 'de', 'pt-BR', 'ja', 'zh-CN'];
const folders = { en:'values', es:'values-es', fr:'values-fr', de:'values-de', 'pt-BR':'values-pt-rBR', ja:'values-ja', 'zh-CN':'values-zh-rCN' };
const placeholder = value => [...value.matchAll(/%(\d+)\$([sd])/g)].map(match => `${match[1]}:${match[2]}`).sort().join(',');
const resourceEntries = xml => [...xml.matchAll(/<(string|plurals)\s+[^>]*name="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/g)]
  .filter(match => !/translatable="false"/.test(match[0]))
  .map(match => [match[2], match[1], match[3]]);

const baseXml = fs.readFileSync(path.join(project, 'app/src/main/res/values/strings.xml'), 'utf8');
const base = new Map(resourceEntries(baseXml).map(([name, type, body]) => [name, { type, placeholders: placeholder(body) }]));
if (base.size !== 162) throw new Error(`Expected 162 localized Android resources, found ${base.size}`);

for (const locale of locales.slice(1)) {
  const file = path.join(project, `app/src/main/res/${folders[locale]}/strings.xml`);
  const entries = new Map(resourceEntries(fs.readFileSync(file, 'utf8')).map(([name, type, body]) => [name, { type, placeholders: placeholder(body) }]));
  const missing = [...base.keys()].filter(key => !entries.has(key));
  const extra = [...entries.keys()].filter(key => !base.has(key));
  const mismatched = [...base.keys()].filter(key => entries.has(key) && (base.get(key).type !== entries.get(key).type || base.get(key).placeholders !== entries.get(key).placeholders));
  if (missing.length || extra.length || mismatched.length) throw new Error(`${locale}: missing=${missing} extra=${extra} placeholder/type mismatch=${mismatched}`);
}

const sharedKeys = Object.keys(catalog.en).sort().join('|');
const webKeys = Object.keys(web.en).sort().join('|');
for (const locale of locales) {
  if (Object.keys(catalog[locale]).sort().join('|') !== sharedKeys) throw new Error(`${locale}: web shared catalog keys differ`);
  if (Object.keys(web[locale]).sort().join('|') !== webKeys) throw new Error(`${locale}: web-only catalog keys differ`);
  for (const [key, source] of Object.entries(web.en)) {
    if (placeholder(source) !== placeholder(web[locale][key])) throw new Error(`${locale}: web placeholder mismatch for ${key}`);
  }
}

const html = fs.readFileSync(path.join(project, 'html-preview/index.html'), 'utf8');
if (!html.includes('<script src="l10n.generated.js"></script>')) throw new Error('HTML localization runtime is not loaded');
if ((html.match(/<option value=/g) || []).length !== 7) throw new Error('HTML language selector must expose seven locales');
const generated = fs.readFileSync(path.join(project, 'html-preview/l10n.generated.js'), 'utf8');
if (!generated.includes("supported=['en','es','fr','de','pt-BR','ja','zh-CN']")) throw new Error('Generated web locale set is incomplete');

const kotlinFiles = [
  'app/src/main/java/com/worksbien/borescopedirect/MainActivity.kt',
  'app/src/main/java/com/worksbien/borescopedirect/ui/BorescopeApp.kt',
  'app/src/main/java/com/worksbien/borescopedirect/camera/UvcCameraController.kt',
  'app/src/main/java/com/worksbien/borescopedirect/billing/BillingManager.kt',
];
for (const relative of kotlinFiles) {
  const source = fs.readFileSync(path.join(project, relative), 'utf8');
  const forbidden = [/Text\("[A-Za-z]/, /contentDescription\s*=\s*"[A-Za-z]/, /message\s*=\s*"[A-Za-z]/, /technicalDetail\s*=\s*"[A-Za-z]/, /Toast\.makeText\([^\n]*"[A-Za-z]/];
  if (forbidden.some(pattern => pattern.test(source))) throw new Error(`${relative}: hard-coded visible English remains`);
}

console.log(`Localization verified: ${base.size} Android resources + ${Object.keys(web.en).length} web-only strings × ${locales.length} locales`);
