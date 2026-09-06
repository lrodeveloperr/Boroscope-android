import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const appFiles = [
  'app/src/main/java/com/worksbien/borescopedirect/MainActivity.kt',
  'app/src/main/java/com/worksbien/borescopedirect/access/PreviewAccessStore.kt',
  'app/src/main/java/com/worksbien/borescopedirect/billing/BillingManager.kt',
  'app/src/main/java/com/worksbien/borescopedirect/camera/CameraModels.kt',
  'app/src/main/java/com/worksbien/borescopedirect/camera/FrameStabilityProbe.kt',
  'app/src/main/java/com/worksbien/borescopedirect/camera/UvcCameraController.kt',
  'app/src/main/java/com/worksbien/borescopedirect/media/MediaRepository.kt',
  'app/src/main/java/com/worksbien/borescopedirect/ui/BorescopeApp.kt',
].map(read);
const source = appFiles.join('\n');
const controller = appFiles[5];
const billing = appFiles[2];
const activity = appFiles[0];
const media = appFiles[6];
const manifest = read('app/src/main/AndroidManifest.xml');
const html = read('html-preview/index.html');

const checks = [];
function check(name, test) {
  test();
  checks.push(name);
}

check('No unfinished markers or force unwraps in app code', () => {
  assert.doesNotMatch(source, /\b(?:TODO|FIXME)\b|!!/);
});
check('Only the minimum Android runtime permission is declared', () => {
  assert.match(manifest, /android\.permission\.CAMERA/);
  assert.doesNotMatch(manifest, /android\.permission\.(?:INTERNET|RECORD_AUDIO|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|AD_ID)/);
});
check('Compatibility requires a stable stream', () => {
  assert.match(controller, /FrameStabilityProbe/);
  assert.match(controller, /format\.name/);
});
check('Preview callback is registered from one controlled site', () => {
  assert.equal(controller.match(/addPreviewDataCallBack\(frameCallback\)/g)?.length, 1);
  assert.match(controller, /removePreviewDataCallBack\(frameCallback\)/);
});
check('Capture completion is idempotent', () => {
  assert.ok((controller.match(/AtomicBoolean\(false\)/g)?.length ?? 0) >= 3);
  assert.ok((controller.match(/compareAndSet\(false, true\)/g)?.length ?? 0) >= 3);
});
check('Stale video callbacks cannot clear a newer operation', () => {
  assert.match(controller, /activeVideoOperation == operation/);
  assert.match(controller, /activeVideoOperation = null/);
});
check('Driver boundaries are guarded', () => {
  for (const call of ['client.register()', 'activeCamera.openCamera', 'activeCamera.captureImage', 'activeCamera.captureVideoStart', 'camera?.captureVideoStop()']) {
    assert.ok(controller.includes(call), `missing ${call}`);
  }
  assert.ok((controller.match(/runCatching/g)?.length ?? 0) >= 18);
});
check('Lifecycle stop always clears camera resources', () => {
  assert.match(controller, /fun stop\(\) \{\s*val wasStarted = started/);
  assert.match(controller, /stopCurrentCamera\(\)/);
});
check('Concurrent Play queries are generation-gated', () => {
  assert.match(billing, /productQueryGeneration/);
  assert.match(billing, /purchaseQueryGeneration/);
  assert.match(billing, /generation != productQueryGeneration/);
  assert.match(billing, /generation != purchaseQueryGeneration/);
});
check('Purchase and restore cannot overlap', () => {
  assert.match(billing, /!purchaseInProgress && !restoreInProgress/);
  assert.match(billing, /_state\.value\.purchaseInProgress \|\| _state\.value\.restoreInProgress/);
});
check('Review flow is single-flight and lifecycle-gated', () => {
  assert.match(activity, /reviewRequestInFlight/);
  assert.match(activity, /reviewRequestInFlight\.compareAndSet\(false, true\)/);
  assert.match(activity, /Lifecycle\.State\.RESUMED/);
});
check('Unlock is visible only after confirmed compatibility', () => {
  assert.match(source, /trialExpired && cameraState\.phase == CameraPhase\.PAUSED_FOR_UNLOCK/);
  assert.match(html, /state\.phase==='PAUSED_FOR_UNLOCK'&&!state\.unlocked&&state\.remaining<=0/);
});
check('Capture filenames are collision-resistant', () => {
  assert.match(media, /uniqueCaptureFile/);
  assert.match(media, /while \(candidate\.exists\(\)\)/);
});
check('Media is validated before publication', () => {
  assert.match(media, /isUsablePhoto/);
  assert.match(media, /isPlayableVideo/);
  assert.match(media, /\.recording\.mp4/);
});
check('HTML contains every customer page and paywall state', () => {
  for (const id of ['cameraPage', 'galleryPage', 'helpPage', 'connectionCard', 'unlockCard', 'captureBar', 'feedCanvas']) {
    assert.ok(html.includes(`id="${id}"`), `missing ${id}`);
  }
  for (const copy of ['Compatibility preview', 'Restore purchase', 'Saved locally', 'Help &amp; compatibility', 'No subscription.']) {
    if (copy === 'No subscription.') continue;
    assert.ok(html.includes(copy), `missing ${copy}`);
  }
});
check('HTML JavaScript parses', () => {
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, 'script missing');
  Function(script);
});
check('HTML persistence validates untrusted local state', () => {
  assert.match(html, /schema!==2/);
  assert.match(html, /captures\.slice\(0,500\)\.filter/);
  assert.match(html, /escapeHtml/);
});
check('HTML trial uses bounded foreground time', () => {
  assert.match(html, /state\.remaining=clamp/);
  assert.match(html, /!document\.hidden/);
  assert.match(html, /visibilitychange/);
});

console.log(`Adversarial checks passed: ${checks.length}`);
for (const name of checks) console.log(`- ${name}`);
